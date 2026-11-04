package io.github.vincenzofiore.rootology.notifications;

import io.github.vincenzofiore.rootology.mail.MailService;
import io.github.vincenzofiore.rootology.mail.OutgoingMail;
import io.github.vincenzofiore.rootology.pipeline.EscalationResolved;
import io.github.vincenzofiore.rootology.pipeline.EscalationWaiting;
import io.github.vincenzofiore.rootology.spaces.Member;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Notifications (#26). A waiting question notifies the space's owners and members, the asker included
 * (in a personal space nobody else can answer); guests are not notified, since they cannot answer. Emails
 * go out as a digest: notifications at least `group-window` old are grouped per person, outside their quiet
 * hours, and dropped once the question is answered.
 */
@Service
public class NotificationService {

    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcClient jdbc;
    private final SpaceService spaces;
    private final MailService mail;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration groupWindow;
    private final String baseUrl;

    NotificationService(JdbcClient jdbc, SpaceService spaces, MailService mail, ApplicationEventPublisher events,
                        Clock clock, @Value("${rootology.notifications.group-window}") Duration groupWindow,
                        @Value("${rootology.base-url}") String baseUrl) {
        this.jdbc = jdbc;
        this.spaces = spaces;
        this.mail = mail;
        this.events = events;
        this.clock = clock;
        this.groupWindow = groupWindow;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @EventListener
    @Transactional
    void on(EscalationWaiting waiting) {
        Set<String> recipients = spaces.membersOf(waiting.spaceId(), waiting.askedBy()).stream()
                .filter(m -> m.role().contributes()).map(Member::githubLogin).collect(Collectors.toSet());
        for (String recipient : recipients) {
            jdbc.sql("INSERT INTO notification (recipient, space_id, kind, process_id, title, body, created_at) "
                            + "VALUES (?, ?, 'ESCALATION_WAITING', ?, ?, ?, ?)")
                    .params(recipient, waiting.spaceId(), waiting.processId(), "A question waits for an answer",
                            truncate(waiting.askedBy() + " asked: " + waiting.question() + " (" + waiting.reason() + ")", 1000),
                            Timestamp.from(clock.instant()))
                    .update();
        }
        events.publishEvent(new NotificationsChanged(recipients));
    }

    @EventListener
    @Transactional
    void on(EscalationResolved resolved) {
        List<String> recipients = jdbc.sql("SELECT DISTINCT recipient FROM notification WHERE process_id = ?")
                .params(resolved.processId()).query(String.class).list();
        jdbc.sql("UPDATE notification SET resolved_at = ?, resolved_by = ? WHERE process_id = ? AND resolved_at IS NULL")
                .params(Timestamp.from(resolved.at()), resolved.reviewer(), resolved.processId()).update();
        events.publishEvent(new NotificationsChanged(Set.copyOf(recipients)));
    }

    /** The latest notifications of a person, newest first. */
    @Transactional(readOnly = true)
    public List<NotificationView> of(String login, int limit) {
        return jdbc.sql("SELECT id, space_id, process_id, title, body, created_at, read_at, resolved_at, resolved_by "
                        + "FROM notification WHERE recipient = ? ORDER BY created_at DESC, id DESC LIMIT ?")
                .params(normalize(login), limit)
                .query((rs, i) -> new NotificationView(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6).toInstant(), instant(rs.getTimestamp(7)),
                        instant(rs.getTimestamp(8)), rs.getString(9)))
                .list();
    }

    @Transactional(readOnly = true)
    public int unread(String login) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE recipient = ? AND read_at IS NULL")
                .params(normalize(login)).query(Integer.class).single();
    }

    /** Marks a person's own notifications as read: one, or all when id is null. */
    @Transactional
    public void markRead(String login, Long id) {
        jdbc.sql("UPDATE notification SET read_at = :at WHERE recipient = :who AND read_at IS NULL"
                        + (id == null ? "" : " AND id = :id"))
                .param("at", Timestamp.from(clock.instant())).param("who", normalize(login))
                .param("id", id).update();
        events.publishEvent(new NotificationsChanged(Set.of(normalize(login))));
    }

    @Transactional(readOnly = true)
    public NotificationPreferences preferences(String login) {
        return jdbc.sql("SELECT email, email_escalations, quiet_from, quiet_to FROM notification_preference "
                        + "WHERE github_login = ?").params(normalize(login))
                .query((rs, i) -> new NotificationPreferences(rs.getString(1), rs.getBoolean(2),
                        time(rs.getTime(3)), time(rs.getTime(4))))
                .optional().orElse(NotificationPreferences.DEFAULT);
    }

    @Transactional
    public void save(String login, NotificationPreferences preferences) {
        String email = preferences.email() == null || preferences.email().isBlank() ? null
                : preferences.email().trim().toLowerCase(Locale.ROOT);
        if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
            throw new IllegalArgumentException(preferences.email() + " is not an email address");
        }
        jdbc.sql("INSERT INTO notification_preference (github_login, email, email_escalations, quiet_from, quiet_to) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (github_login) DO UPDATE SET email = EXCLUDED.email, "
                        + "email_escalations = EXCLUDED.email_escalations, quiet_from = EXCLUDED.quiet_from, "
                        + "quiet_to = EXCLUDED.quiet_to")
                .params(normalize(login), email, preferences.emailEscalations(), sqlTime(preferences.quietFrom()),
                        sqlTime(preferences.quietTo()))
                .update();
    }

    /** At sign-in: the primary verified address becomes the notification address, unless one is already set. */
    @Transactional
    public void rememberEmail(String login, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        jdbc.sql("INSERT INTO notification_preference (github_login, email) VALUES (?, ?) "
                        + "ON CONFLICT (github_login) DO UPDATE SET email = coalesce(notification_preference.email, EXCLUDED.email)")
                .params(normalize(login), email.trim().toLowerCase(Locale.ROOT)).update();
    }

    @Scheduled(fixedDelayString = "${rootology.notifications.digest-every}", initialDelayString = "PT1M")
    void sendDigests() {
        digest();
    }

    /**
     * One email per person with every question still waiting that is at least group-window old and not yet
     * emailed; skipped during their quiet hours (it goes at the next run after them). Returns the emails sent.
     */
    @Transactional
    public int digest() {
        Instant now = clock.instant();
        Map<String, List<Pending>> byRecipient = new LinkedHashMap<>();
        jdbc.sql("SELECT id, recipient, body FROM notification WHERE kind = 'ESCALATION_WAITING' AND emailed_at IS NULL "
                        + "AND resolved_at IS NULL AND created_at <= ? ORDER BY created_at")
                .params(Timestamp.from(now.minus(groupWindow)))
                .query((rs, i) -> new Pending(rs.getLong(1), rs.getString(2), rs.getString(3))).list()
                .forEach(p -> byRecipient.computeIfAbsent(p.recipient(), k -> new java.util.ArrayList<>()).add(p));
        int sent = 0;
        for (Map.Entry<String, List<Pending>> e : byRecipient.entrySet()) {
            NotificationPreferences prefs = preferences(e.getKey());
            if (prefs.quietAt(LocalTime.now(clock))) {
                continue;
            }
            if (prefs.emailEscalations() && prefs.email() != null) {
                mail.sendLater(digestMail(prefs.email(), e.getValue()));
                sent++;
            }
            // Handled: sent, or not wanted, or no address. Never emailed twice.
            jdbc.sql("UPDATE notification SET emailed_at = :at WHERE id IN (:ids)")
                    .param("at", Timestamp.from(now)).param("ids", e.getValue().stream().map(Pending::id).toList())
                    .update();
        }
        if (sent > 0) {
            log.info("Sent {} notification emails", sent);
        }
        return sent;
    }

    private OutgoingMail digestMail(String to, List<Pending> waiting) {
        String link = baseUrl + "/escalations";
        String subject = waiting.size() == 1 ? "Rootology: una domanda aspetta una risposta"
                : "Rootology: " + waiting.size() + " domande aspettano una risposta";
        String list = waiting.stream().map(p -> "- " + p.body()).collect(Collectors.joining("\n"));
        String text = """
                Ciao,

                %s

                %s

                Per rispondere: %s
                Puoi cambiare queste email nelle preferenze di notifica di Rootology.
                """.formatted(waiting.size() == 1 ? "una domanda nel tuo spazio aspetta una risposta:"
                : "alcune domande nei tuoi spazi aspettano una risposta:", list, link);
        String html = "<p>Ciao,</p><p>" + (waiting.size() == 1 ? "una domanda nel tuo spazio aspetta una risposta:"
                : "alcune domande nei tuoi spazi aspettano una risposta:") + "</p><ul>"
                + waiting.stream().map(p -> "<li>" + HtmlUtils.htmlEscape(p.body()) + "</li>").collect(Collectors.joining())
                + "</ul><p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">Rispondi</a></p>"
                + "<p style=\"color:#666\">Puoi cambiare queste email nelle preferenze di notifica di Rootology.</p>";
        return new OutgoingMail(to, subject, text, html);
    }

    private record Pending(long id, String recipient, String body) {
    }

    private static String normalize(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static LocalTime time(Time t) {
        return t == null ? null : t.toLocalTime();
    }

    private static Time sqlTime(LocalTime t) {
        return t == null ? null : Time.valueOf(t);
    }
}
