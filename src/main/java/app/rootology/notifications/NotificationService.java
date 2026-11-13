package app.rootology.notifications;

import app.rootology.access.AccessRequested;
import app.rootology.mail.MailService;
import app.rootology.mail.OutgoingMail;
import app.rootology.pipeline.EscalationResolved;
import app.rootology.pipeline.EscalationWaiting;
import app.rootology.roles.AppRole;
import app.rootology.roles.RoleService;
import app.rootology.spaces.Member;
import app.rootology.spaces.SpaceService;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Notifications (#26), and weather alerts (#37). A waiting question notifies the space's owners and members, the asker included
 * (in a personal space nobody else can answer); guests are not notified, since they cannot answer. Emails
 * go out as a digest: notifications at least `group-window` old are grouped per person, outside their quiet
 * hours, and dropped once the question is answered. The same rows are the outbox of Telegram (#38): prompt, one
 * message per person per tick, with its own delivery mark and its own preferences, and the shared quiet hours.
 */
@Service
public class NotificationService {

    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** The delivery mark of each channel, on the same rows: a notification can go out by both (#38). */
    private static final String EMAIL_MARK = "emailed_at";
    private static final String TELEGRAM_MARK = "telegramed_at";

    private final JdbcClient jdbc;
    private final SpaceService spaces;
    private final RoleService roles;
    private final MailService mail;
    private final TelegramClient telegram;
    private final TelegramLinks telegramLinks;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration groupWindow;
    private final String baseUrl;

    NotificationService(JdbcClient jdbc, SpaceService spaces, RoleService roles, MailService mail,
                        TelegramClient telegram, TelegramLinks telegramLinks, ApplicationEventPublisher events,
                        Clock clock, @Value("${rootology.notifications.group-window}") Duration groupWindow,
                        @Value("${rootology.base-url}") String baseUrl) {
        this.jdbc = jdbc;
        this.spaces = spaces;
        this.roles = roles;
        this.mail = mail;
        this.telegram = telegram;
        this.telegramLinks = telegramLinks;
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
            // The data only (#94): the page and the email word it in the reader's language, and the reason
            // of the decision stays on the Escalations card.
            jdbc.sql("INSERT INTO notification (recipient, space_id, garden_id, kind, process_id, title, body, "
                            + "asked_by, created_at) VALUES (?, ?, ?, 'ESCALATION_WAITING', ?, ?, ?, ?, ?)")
                    .params(recipient, waiting.spaceId(), garden(waiting.gardenId()), waiting.processId(),
                            "A question waits for an answer", truncate(waiting.question(), 1000), waiting.askedBy(),
                            Timestamp.from(clock.instant()))
                    .update();
        }
        events.publishEvent(new NotificationsChanged(recipients));
    }

    @EventListener
    @Transactional
    void on(EscalationResolved resolved) {
        Set<String> changed = new HashSet<>(jdbc.sql("SELECT DISTINCT recipient FROM notification WHERE process_id = ?")
                .params(resolved.processId()).query(String.class).list());
        // A waiting question that has its answer asks nothing of anyone any more: it is also read, so it leaves
        // the bell's count (its row has no Open button once resolved, so nothing else would clear it).
        jdbc.sql("UPDATE notification SET resolved_at = ?, resolved_by = ?, read_at = COALESCE(read_at, ?) "
                        + "WHERE process_id = ? AND resolved_at IS NULL")
                .params(Timestamp.from(resolved.at()), resolved.reviewer(), Timestamp.from(resolved.at()),
                        resolved.processId()).update();
        // #51: the asker gets a new, unread notification with the answer; the other recipients only see their
        // waiting notification resolved. The person who answered gets nothing new, unless someone else asked,
        // and someone who left the space while the question waited cannot open it: no notification.
        String asker = normalize(resolved.askedBy());
        if (!asker.isEmpty() && !asker.equals(normalize(resolved.reviewer()))
                && spaces.isMember(resolved.spaceId(), asker)) {
            jdbc.sql("INSERT INTO notification (recipient, space_id, garden_id, kind, process_id, title, "
                            + "resolved_by, created_at) VALUES (?, ?, ?, 'ESCALATION_ANSWERED', ?, ?, ?, ?)")
                    .params(asker, resolved.spaceId(), garden(resolved.gardenId()), resolved.processId(),
                            "Your question has an answer", resolved.reviewer(), Timestamp.from(resolved.at()))
                    .update();
            changed.add(asker);
        }
        events.publishEvent(new NotificationsChanged(Set.copyOf(changed)));
    }

    /**
     * A new invitation request (#65): one notification per admin, with no space, since it is about the app and
     * not about a garden. It says that something waits and links the Administration page; the motivation, the
     * login and the email stay behind the admin role.
     */
    @EventListener
    @Transactional
    void on(AccessRequested requested) {
        Set<String> admins = new HashSet<>(roles.holders(AppRole.ADMIN));
        for (String admin : admins) {
            jdbc.sql("INSERT INTO notification (recipient, space_id, garden_id, kind, title, body, created_at) "
                            + "VALUES (?, NULL, NULL, 'ACCESS_REQUEST', ?, ?, ?)")
                    .params(admin, "A new invitation request", "A new invitation request is waiting",
                            Timestamp.from(clock.instant()))
                    .update();
        }
        if (!admins.isEmpty()) {
            events.publishEvent(new NotificationsChanged(admins));
        }
    }

    /** The latest notifications of a person, newest first. */
    @Transactional(readOnly = true)
    public List<NotificationView> of(String login, int limit) {
        return jdbc.sql("SELECT id, space_id, garden_id, kind, process_id, title, body, created_at, read_at, "
                        + "resolved_at, resolved_by, dedup_key, asked_by FROM notification WHERE recipient = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ?")
                .params(normalize(login), limit)
                .query((rs, i) -> {
                    long gardenId = rs.getLong(3);
                    if (rs.wasNull()) {
                        gardenId = 0;
                    }
                    return new NotificationView(rs.getLong(1), rs.getLong(2), gardenId,
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getTimestamp(8).toInstant(), instant(rs.getTimestamp(9)),
                            instant(rs.getTimestamp(10)), rs.getString(11), rs.getString(12), rs.getString(13));
                })
                .list();
    }

    @Transactional(readOnly = true)
    public int unread(String login) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE recipient = ? AND read_at IS NULL")
                .params(normalize(login)).query(Integer.class).single();
    }

    /**
     * A weather alert (#37) for every member of a space, guests included: it informs and asks for no action in
     * the app. dedupKey names the event (garden, rule, day), so the same event never notifies anyone twice.
     * Returns how many people were newly notified.
     */
    @Transactional
    public int weatherAlert(long spaceId, long gardenId, String dedupKey, String title, String body) {
        Set<String> notified = new java.util.HashSet<>();
        for (Member member : spaces.membersForSystem(spaceId)) {
            int added = jdbc.sql("INSERT INTO notification (recipient, space_id, garden_id, kind, dedup_key, title, "
                            + "body, created_at) VALUES (?, ?, ?, 'WEATHER_ALERT', ?, ?, ?, ?) "
                            + "ON CONFLICT (recipient, dedup_key) DO NOTHING")
                    .params(member.githubLogin(), spaceId, garden(gardenId), dedupKey, truncate(title, 200),
                            truncate(body, 1000),
                            Timestamp.from(clock.instant()))
                    .update();
            if (added > 0) {
                notified.add(member.githubLogin());
            }
        }
        if (!notified.isEmpty()) {
            events.publishEvent(new NotificationsChanged(notified));
        }
        return notified.size();
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
        return jdbc.sql("SELECT email, email_escalations, email_weather, telegram_escalations, telegram_weather, "
                        + "quiet_from, quiet_to FROM notification_preference WHERE github_login = ?")
                .params(normalize(login))
                .query((rs, i) -> new NotificationPreferences(rs.getString(1), rs.getBoolean(2), rs.getBoolean(3),
                        rs.getBoolean(4), rs.getBoolean(5), time(rs.getTime(6)), time(rs.getTime(7))))
                .optional().orElse(NotificationPreferences.DEFAULT);
    }

    @Transactional
    public void save(String login, NotificationPreferences preferences) {
        String email = preferences.email() == null || preferences.email().isBlank() ? null
                : preferences.email().trim().toLowerCase(Locale.ROOT);
        if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
            throw new IllegalArgumentException(preferences.email() + " is not an email address");
        }
        jdbc.sql("INSERT INTO notification_preference (github_login, email, email_escalations, email_weather, "
                        + "telegram_escalations, telegram_weather, quiet_from, quiet_to) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (github_login) DO UPDATE SET "
                        + "email = EXCLUDED.email, email_escalations = EXCLUDED.email_escalations, "
                        + "email_weather = EXCLUDED.email_weather, "
                        + "telegram_escalations = EXCLUDED.telegram_escalations, "
                        + "telegram_weather = EXCLUDED.telegram_weather, quiet_from = EXCLUDED.quiet_from, "
                        + "quiet_to = EXCLUDED.quiet_to")
                .params(normalize(login), email, preferences.emailEscalations(), preferences.emailWeather(),
                        preferences.telegramEscalations(), preferences.telegramWeather(),
                        sqlTime(preferences.quietFrom()), sqlTime(preferences.quietTo()))
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
        Map<String, List<Pending>> byRecipient = pending(EMAIL_MARK, now.minus(groupWindow));
        int sent = 0;
        for (Map.Entry<String, List<Pending>> e : byRecipient.entrySet()) {
            NotificationPreferences prefs = preferences(e.getKey());
            if (prefs.quietAt(LocalTime.now(clock))) {
                continue;
            }
            List<Pending> wanted = e.getValue().stream()
                    .filter(p -> p.weather() ? prefs.emailWeather() : prefs.emailEscalations()).toList();
            if (!wanted.isEmpty() && prefs.email() != null) {
                mail.sendLater(digestMail(prefs.email(), wanted));
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

    /**
     * The same outbox on the phone (#38): one plain-text message per person with everything not yet sent there,
     * promptly (no grouping window), skipped during the shared quiet hours. It says where and links, never the
     * question, the answer or a weather alert's body. A person with no working link is marked and gets nothing.
     * Returns the messages sent.
     */
    @Scheduled(fixedDelayString = "${rootology.telegram.send-every}", initialDelayString = "PT30S")
    @Transactional
    public int telegramDigest() {
        Instant now = clock.instant();
        int sent = 0;
        for (Map.Entry<String, List<Pending>> e : pending(TELEGRAM_MARK, null).entrySet()) {
            NotificationPreferences prefs = preferences(e.getKey());
            if (prefs.quietAt(LocalTime.now(clock))) {
                continue;
            }
            List<Pending> all = e.getValue();
            List<Pending> wanted = all.stream()
                    .filter(p -> p.weather() ? prefs.telegramWeather() : prefs.telegramEscalations()).toList();
            Optional<TelegramLinks.Link> link = telegramLinks.of(e.getKey()).filter(TelegramLinks.Link::working);
            if (link.isEmpty() || wanted.isEmpty()) {
                markTelegramed(all, now);
                continue;
            }
            String text = TelegramMessages.digest(wanted.stream().map(this::telegramItem).toList());
            switch (telegram.send(link.get().chatId(), text)) {
                case SENT -> {
                    sent++;
                    markTelegramed(all, now);
                }
                // One message lost (our own bad request): the chat is fine, and it is not retried.
                case DROPPED -> markTelegramed(all, now);
                // The bot is blocked or the chat is gone: never send there again, and say so in the app.
                case CHAT_GONE -> {
                    telegramLinks.fail(link.get().chatId());
                    markTelegramed(all, now);
                }
                // Rate limited, Telegram down or an open circuit: the next tick tries again.
                case RETRY, UNAVAILABLE -> {
                }
            }
        }
        if (sent > 0) {
            log.info("Sent {} Telegram messages", sent);
        }
        return sent;
    }

    private void markTelegramed(List<Pending> rows, Instant now) {
        jdbc.sql("UPDATE notification SET telegramed_at = :at WHERE id IN (:ids)")
                .param("at", Timestamp.from(now)).param("ids", rows.stream().map(Pending::id).toList()).update();
    }

    /** What one notification says on the phone: where it happened and the link, nothing more. */
    private TelegramMessages.Item telegramItem(Pending p) {
        if (p.accessRequest()) {
            return TelegramMessages.accessRequest(adminLink());
        }
        String page = p.weather() ? "" : p.answered() ? "history" : "escalations";
        String link = link(page, p);
        return p.weather() ? TelegramMessages.weather(p.title(), p.place(), link)
                : p.answered() ? TelegramMessages.answered(p.place(), link)
                : TelegramMessages.waiting(p.place(), link);
    }

    /**
     * The notifications still to deliver through one channel, by recipient, oldest first. {@code mark} is one of
     * this class's own two columns; {@code notAfter} is null when the channel does not group (Telegram, #38).
     */
    private Map<String, List<Pending>> pending(String mark, Instant notAfter) {
        Map<String, List<Pending>> byRecipient = new LinkedHashMap<>();
        // An access request (#65) belongs to no space: the join must not drop it.
        var query = jdbc.sql("SELECT n.id, n.recipient, n.kind, n.title, n.body, n.resolved_by, n.space_id, "
                        + "n.garden_id, s.name, g.name, n.asked_by FROM notification n "
                        + "LEFT JOIN space s ON s.id = n.space_id "
                        + "LEFT JOIN garden g ON g.id = n.garden_id "
                        + "WHERE n." + mark + " IS NULL AND n.resolved_at IS NULL"
                        + (notAfter == null ? "" : " AND n.created_at <= :notAfter")
                        + " ORDER BY n.created_at");
        if (notAfter != null) {
            query = query.param("notAfter", Timestamp.from(notAfter));
        }
        query.query((rs, i) -> new Pending(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), gardenOf(rs, 8), rs.getString(9),
                        rs.getString(10), rs.getString(11))).list()
                .forEach(p -> byRecipient.computeIfAbsent(p.recipient(), k -> new java.util.ArrayList<>()).add(p));
        return byRecipient;
    }

    private OutgoingMail digestMail(String to, List<Pending> pending) {
        List<Pending> questions = pending.stream().filter(Pending::waiting).toList();
        List<Pending> answers = pending.stream().filter(Pending::answered).toList();
        List<Pending> weather = pending.stream().filter(Pending::weather).toList();
        List<Pending> requests = pending.stream().filter(Pending::accessRequest).toList();
        String subject;
        if (!weather.isEmpty()) {
            subject = questions.isEmpty() && answers.isEmpty()
                    ? (weather.size() == 1 ? "Rootology: " + weather.getFirst().title()
                            : "Rootology: " + weather.size() + " avvisi meteo")
                    : answers.isEmpty() ? "Rootology: domande in attesa e avvisi meteo"
                    : questions.isEmpty() ? "Rootology: risposte e avvisi meteo"
                    : "Rootology: risposte, domande in attesa e avvisi meteo";
        } else if (questions.isEmpty() && answers.isEmpty()) {
            // Only invitation requests (#65): the rest of the chain below assumes an answer or a question.
            subject = null;
        } else if (questions.isEmpty()) {
            subject = "Rootology: la tua domanda ha una risposta";
        } else if (answers.isEmpty()) {
            subject = questions.size() == 1 ? "Rootology: una domanda aspetta una risposta"
                    : "Rootology: " + questions.size() + " domande aspettano una risposta";
        } else {
            subject = "Rootology: risposte e domande in attesa";
        }
        if (!requests.isEmpty()) {
            String extra = requests.size() == 1 ? "una richiesta di invito"
                    : requests.size() + " richieste di invito";
            subject = subject == null
                    ? "Rootology: " + extra + (requests.size() == 1 ? " aspetta una decisione"
                            : " aspettano una decisione")
                    : subject + " e " + extra;
        }
        StringBuilder text = new StringBuilder("Ciao,\n\n");
        StringBuilder html = new StringBuilder("<p>Ciao,</p>");
        if (!answers.isEmpty()) {
            text.append(answers.size() == 1 ? "la tua domanda ha una risposta:\n"
                    : "le tue domande hanno una risposta:\n");
            answers.forEach(p -> text.append("- ").append(p.resolvedBy()).append(" ha risposto: ")
                    .append(link("history", p)).append('\n'));
            html.append("<p>").append(answers.size() == 1 ? "La tua domanda ha una risposta:"
                    : "Le tue domande hanno una risposta:").append("</p><ul>");
            answers.forEach(p -> html.append("<li><strong>").append(HtmlUtils.htmlEscape(p.resolvedBy()))
                    .append("</strong> ha risposto — <a href=\"").append(HtmlUtils.htmlEscape(link("history", p)))
                    .append("\">Cronologia</a></li>"));
            html.append("</ul>");
        }
        if (!questions.isEmpty()) {
            text.append(questions.size() == 1 ? "una domanda nel tuo spazio aspetta una risposta:\n"
                    : "alcune domande nei tuoi spazi aspettano una risposta:\n");
            questions.forEach(p -> text.append("- ").append(p.questionLine()).append('\n')
                    .append("  Per rispondere: ").append(link("escalations", p)).append('\n'));
            html.append("<p>").append(questions.size() == 1 ? "Una domanda nel tuo spazio aspetta una risposta:"
                    : "Alcune domande nei tuoi spazi aspettano una risposta:").append("</p><ul>");
            questions.forEach(p -> html.append("<li>").append(HtmlUtils.htmlEscape(p.questionLine()))
                    .append(" — <a href=\"").append(HtmlUtils.htmlEscape(link("escalations", p)))
                    .append("\">Rispondi</a></li>"));
            html.append("</ul>");
        }
        if (!weather.isEmpty()) {
            text.append("Avvisi meteo per i tuoi orti:\n");
            // Each alert links to its own garden: two gardens in one email must not lead to the first one.
            weather.forEach(p -> text.append("- ").append(p.title()).append(": ").append(p.body())
                    .append(" (").append(p.place()).append(")\n  ").append(link("", p)).append('\n'));
            html.append("<p>Avvisi meteo per i tuoi orti:</p><ul>");
            weather.forEach(p -> html.append("<li><strong>").append(HtmlUtils.htmlEscape(p.title())).append("</strong>: ")
                    .append(HtmlUtils.htmlEscape(p.body())).append(" (")
                    .append(HtmlUtils.htmlEscape(p.place())).append(") — <a href=\"")
                    .append(HtmlUtils.htmlEscape(link("", p))).append("\">")
                    .append(HtmlUtils.htmlEscape(p.place())).append("</a></li>"));
            html.append("</ul>");
        }
        if (!requests.isEmpty()) {
            // Never the motivation, the login or the address: only that something waits, and where (#65).
            text.append(requests.size() == 1 ? "una richiesta di invito aspetta una decisione:\n"
                    : "alcune richieste di invito aspettano una decisione:\n");
            text.append("- Amministrazione: ").append(adminLink()).append('\n');
            html.append("<p>").append(requests.size() == 1 ? "Una richiesta di invito aspetta una decisione:"
                    : "Alcune richieste di invito aspettano una decisione:").append("</p><ul><li><a href=\"")
                    .append(HtmlUtils.htmlEscape(adminLink())).append("\">Amministrazione</a></li></ul>");
        }
        text.append("Puoi cambiare queste email nelle preferenze di notifica di Rootology.\n");
        html.append("<p style=\"color:#666\">Puoi cambiare queste email nelle preferenze di notifica di Rootology.</p>");
        return new OutgoingMail(to, subject, text.toString(), html.toString());
    }

    /**
     * The link that opens the page of its own space and garden (#51, #56), whichever the person is looking
     * at. Without a garden: the space's questions when there is no garden to open (a deleted garden), and
     * the space's gardens overview for a weather alert, which is about a garden.
     */
    /** The Administration page, where an invitation request waits (#65). */
    private String adminLink() {
        return baseUrl + "/admin";
    }

    private String link(String page, Pending p) {
        String where = "/spaces/" + p.spaceId();
        if (p.gardenId() > 0) {
            where += "/gardens/" + p.gardenId();
        } else if (page.isEmpty()) {
            where += "/gardens";
        }
        return baseUrl + where + (page.isEmpty() ? "" : "/" + page);
    }

    private record Pending(long id, String recipient, String kind, String title, String body, String resolvedBy,
                           long spaceId, long gardenId, String spaceName, String gardenName, String askedBy) {

        /**
         * A waiting question in the email, in Italian (#94): who asked and the question. A row stored before
         * #94 kept the service's English sentence in body and has no askedBy: it says what it always said.
         */
        String questionLine() {
            return askedBy == null ? body : askedBy + " ha chiesto: " + body;
        }

        /** Where it happened, as the notification page shows it: space · garden, or the space alone. */
        String place() {
            if (accessRequest()) {
                return "Amministrazione";
            }
            return gardenName == null ? spaceName : spaceName + " · " + gardenName;
        }

        boolean accessRequest() {
            return "ACCESS_REQUEST".equals(kind);
        }

        boolean weather() {
            return "WEATHER_ALERT".equals(kind);
        }

        boolean answered() {
            return "ESCALATION_ANSWERED".equals(kind);
        }

        boolean waiting() {
            return !weather() && !answered() && !accessRequest();
        }
    }

    /** The space level is stored as no garden (null), so the column's foreign key can hold. */
    private static Long garden(long gardenId) {
        return gardenId > 0 ? gardenId : null;
    }

    private static String normalize(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** The garden of a notification: 0 (the space level) when the column is null. */
    private static long gardenOf(java.sql.ResultSet rs, int column) throws java.sql.SQLException {
        long gardenId = rs.getLong(column);
        return rs.wasNull() ? 0 : gardenId;
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
