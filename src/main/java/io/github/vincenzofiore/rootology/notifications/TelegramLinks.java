package io.github.vincenzofiore.rootology.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The link between a person and their Telegram chat (#38). The person asks for a code in the app, opens the
 * bot with it, and the chat is linked; only the code's SHA-256 is stored, like invitations (#23), and the code
 * lives ten minutes and works once. One person, one private chat; a new link replaces the old one.
 */
@Component
public class TelegramLinks {

    private static final Logger log = LoggerFactory.getLogger(TelegramLinks.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A code as the app shows it and the deep link carries: Telegram allows A-Z, a-z, 0-9, _ and - (#38). */
    static final String CODE = "[A-Za-z0-9_-]{20,64}";

    /** The code shown in the app, and the deep link that opens the bot with it (no url when the bot is unknown). */
    public record Start(String code, String url) {
    }

    /** A link, working or not: a failed one is kept, so the person can be told in the app. */
    public record Link(long chatId, Instant linkedAt, Instant failedAt) {

        public boolean working() {
            return failedAt == null;
        }
    }

    private final JdbcClient jdbc;
    private final TelegramClient telegram;
    private final Clock clock;
    private final Duration validity;

    TelegramLinks(JdbcClient jdbc, TelegramClient telegram, Clock clock,
                  @Value("${rootology.telegram.code-validity}") Duration validity) {
        this.jdbc = jdbc;
        this.telegram = telegram;
        this.clock = clock;
        this.validity = validity;
    }

    /** True when a bot token is set: without one the Notifications page shows no Telegram at all. */
    public boolean configured() {
        return telegram.configured();
    }

    /** A new one-time code for this person, with the deep link when the bot's username is known. */
    @Transactional
    public Start start(String login) {
        Instant now = clock.instant();
        jdbc.sql("UPDATE telegram_link_code SET used_at = :now WHERE login = :login AND used_at IS NULL")
                .param("now", java.sql.Timestamp.from(now)).param("login", login).update();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.sql("INSERT INTO telegram_link_code (code_hash, login, created_at, expires_at) VALUES (?, ?, ?, ?)")
                .params(hash(code), login, java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now.plus(validity)))
                .update();
        String url = telegram.configured()
                ? telegram.botUsername().map(username -> "https://t.me/" + username + "?start=" + code).orElse(null)
                : null;
        return new Start(code, url);
    }

    /** Consumes a code from a private chat: true when it was valid, unused and its chat is now linked. */
    @Transactional
    public boolean complete(String code, long chatId) {
        Optional<String> login = jdbc.sql("""
                        UPDATE telegram_link_code SET used_at = :now
                        WHERE code_hash = :hash AND used_at IS NULL AND expires_at > :now
                        RETURNING login""")
                .param("now", java.sql.Timestamp.from(clock.instant())).param("hash", hash(code))
                .query(String.class).optional();
        if (login.isEmpty()) {
            return false;
        }
        // One Telegram account belongs to one person: if it was linked to someone else, that link goes.
        jdbc.sql("DELETE FROM telegram_link WHERE chat_id = :chat").param("chat", chatId).update();
        jdbc.sql("""
                        INSERT INTO telegram_link (login, chat_id, linked_at) VALUES (:login, :chat, :now)
                        ON CONFLICT (login) DO UPDATE SET chat_id = EXCLUDED.chat_id,
                            linked_at = EXCLUDED.linked_at, failed_at = NULL""")
                .param("login", login.get()).param("chat", chatId)
                .param("now", java.sql.Timestamp.from(clock.instant())).update();
        log.info("A Telegram chat was linked to a person");
        return true;
    }

    @Transactional
    public void unlink(String login) {
        jdbc.sql("DELETE FROM telegram_link WHERE login = :login").param("login", login).update();
    }

    /** Telegram no longer delivers here (blocked bot, deleted chat): the link is kept, marked, and never used. */
    @Transactional
    public void fail(long chatId) {
        int failed = jdbc.sql("UPDATE telegram_link SET failed_at = :now WHERE chat_id = :chat AND failed_at IS NULL")
                .param("now", java.sql.Timestamp.from(clock.instant())).param("chat", chatId).update();
        if (failed > 0) {
            log.info("A Telegram chat no longer accepts messages: the link is marked as failed");
        }
    }

    @Transactional(readOnly = true)
    public Optional<Link> of(String login) {
        return jdbc.sql("SELECT chat_id, linked_at, failed_at FROM telegram_link WHERE login = :login")
                .param("login", login)
                .query((rs, i) -> new Link(rs.getLong(1), rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()))
                .optional();
    }

    /** The SHA-256 of a code, hex, as the database keeps it. */
    static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
