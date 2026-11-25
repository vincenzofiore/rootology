package app.rootology.notifications;

import app.rootology.access.AccountFlagService;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admins are told of a flagged account (#165) through the same outbox as everything else: one row each
 * with no space, an email digest and a Telegram message that say the login and the counts, never a question.
 */
class AccountFlagNotificationsTest extends SettableClockContext {

    static final Instant START = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    AccountFlagService flags;

    @Autowired
    SpaceService spaces;

    @Autowired
    NotificationService notifications;

    @Autowired
    JdbcClient jdbc;

    @Value("${rootology.test.mailpit-api}")
    String mailpit;

    @BeforeEach
    void clean() {
        now = START;
    }

    private void admin(String login) {
        jdbc.sql("""
                        insert into app_role_grant (github_login, role, granted_by, granted_at)
                        values (:login, 'ADMIN', 'flag-setup', now())""")
                .param("login", login).update();
        jdbc.sql("""
                        insert into notification_preference (github_login, email) values (:login, :email)
                        on conflict (github_login) do update set email = excluded.email""")
                .param("login", login).param("email", login + "@example.org").update();
    }

    private void forgetAdmin(String login) {
        jdbc.sql("delete from app_role_grant where github_login = :login and granted_by = 'flag-setup'")
                .param("login", login).update();
    }

    /** A working chat, for the one test that reads what Telegram would say. */
    private void telegramLinked(String login) {
        jdbc.sql("insert into telegram_link (login, chat_id, linked_at) values (:login, :chat, now()) "
                        + "on conflict (login) do update set chat_id = excluded.chat_id, failed_at = null")
                .param("login", login).param("chat", 900_000_000L + Math.abs(login.hashCode())).update();
    }

    /**
     * An account that meets the signal: signed in long ago, no garden, and eight judged questions of which
     * seven were off-domain. The question is a washing machine, which must never leave the app.
     */
    private void suspiciousAccount(String login) {
        spaces.signedIn(login);
        jdbc.sql("update app_user set first_login_at = :at where login = :l")
                .param("at", Timestamp.from(now.minus(Duration.ofDays(12)))).param("l", login).update();
        for (int i = 0; i < 8; i++) {
            jdbc.sql("""
                    insert into pipeline_run (process_id, question_id, question, noise_dropout, noise_spurious,
                        noise_mislabel, noise_near_subject, noise_seed, decision_answer, decision_confidence,
                        decision_escalated, decision_reason, answer, answered_by_human, answer_text, finished_at,
                        started_by, off_domain)
                    values (gen_random_uuid()::text, 'free', 'quanto costa una lavatrice?', 0, 0, 0, false, 0,
                        '{}', 1, false, 'test', '{}', false, 'an answer', :at, :l, :off)""")
                    .param("at", Timestamp.from(now.minus(Duration.ofHours(2)).plusSeconds(i)))
                    .param("l", login).param("off", i < 7).update();
        }
    }

    @Test
    void aFlagCreatesOneRowPerAdminWithNoSpaceAndTheCounts() {
        admin("flag-admin-one");
        admin("flag-admin-two");
        suspiciousAccount("flag-asker");
        try {
            flags.evaluate();

            assertThat(notifications.of("flag-admin-one", 10)).anySatisfy(n -> {
                assertThat(n.accountFlag()).isTrue();
                assertThat(n.spaceId()).isZero();
                assertThat(n.unread()).isTrue();
                assertThat(n.body()).isEqualTo("flag-asker: 7 domande su 8 fuori tema, nessun orto da 12 giorni");
                assertThat(n.body()).doesNotContain("lavatrice");
            });
            assertThat(notifications.of("flag-admin-two", 10)).anySatisfy(n -> assertThat(n.accountFlag()).isTrue());
            assertThat(notifications.of("flag-asker", 10)).as("nothing is said to the person")
                    .noneMatch(n -> n.accountFlag());
        } finally {
            forgetAdmin("flag-admin-one");
            forgetAdmin("flag-admin-two");
        }
    }

    @Test
    void theEmailAndTheTelegramMessageOnlySayTheLoginTheCountsAndWhereToDecide() throws InterruptedException {
        admin("flag-admin-mail");
        telegramLinked("flag-admin-mail");
        suspiciousAccount("flag-mailer");
        try {
            flags.evaluate();
            doReturn(TelegramClient.Delivery.SENT).when(telegram).send(anyLong(), anyString());

            now = START.plus(Duration.ofMinutes(3));
            assertThat(notifications.digest()).isGreaterThanOrEqualTo(1);
            JsonNode mail = messageTo("flag-admin-mail@example.org");
            for (String part : new String[]{"Text", "HTML"}) {
                assertThat(mail.path(part).asString()).as(part + ": the login, the counts and the page")
                        .contains("/admin", "flag-mailer", "7 domande su 8 fuori tema")
                        .doesNotContain("lavatrice");
            }

            ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
            assertThat(notifications.telegramDigest()).isGreaterThanOrEqualTo(1);
            verify(telegram, atLeastOnce()).send(anyLong(), text.capture());
            assertThat(String.join("\n", text.getAllValues()))
                    .contains("account è stato segnalato", "flag-mailer", "/admin")
                    .doesNotContain("lavatrice");
        } finally {
            forgetAdmin("flag-admin-mail");
        }
    }

    /** The full message Mailpit received for this address, as NotificationServiceTest does. */
    private JsonNode messageTo(String address) throws InterruptedException {
        RestClient api = RestClient.create(mailpit);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = JsonMapper.builder().build().readTree(api.get()
                    .uri(u -> u.path("/api/v1/search").queryParam("query", "to:\"" + address + "\"").build())
                    .retrieve().body(String.class));
            if (found.path("messages_count").asInt() > 0) {
                String id = found.path("messages").get(0).path("ID").asString();
                return JsonMapper.builder().build().readTree(api.get()
                        .uri("/api/v1/message/" + id).retrieve().body(String.class));
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No email to " + address);
    }
}
