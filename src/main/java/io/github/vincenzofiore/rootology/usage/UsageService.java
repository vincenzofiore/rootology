package io.github.vincenzofiore.rootology.usage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Records what each run consumed and enforces the daily question limit (#19). Days follow the app's clock
 * (Italian time). Logins are compared in lower case, as everywhere else.
 */
@Service
public class UsageService {

    /** Today's questions of one person against their limit. */
    public record Today(int used, int limit) {

        public boolean exhausted() {
            return used >= limit;
        }
    }

    /** One person's usage over a period, with their limit, for the admin. */
    public record PersonUsage(String login, int questionsToday, int questions, int reviews, long promptTokens,
                              long completionTokens, double costUsd, int jevCalls, int limit, boolean personalLimit) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int defaultLimit;

    UsageService(JdbcClient jdbc, Clock clock, @Value("${rootology.usage.daily-questions}") int defaultLimit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.defaultLimit = defaultLimit;
    }

    /** Refuses a new question once today's limit is reached; called before any paid call. */
    @Transactional(readOnly = true)
    public void requireQuestionAvailable(String login) {
        Today today = today(login);
        if (today.exhausted()) {
            throw new UsageLimitException("You have asked your %d questions for today; you can ask again from midnight."
                    .formatted(today.limit()), today.limit());
        }
    }

    @Transactional(readOnly = true)
    public Today today(String login) {
        String who = normalize(login);
        int used = jdbc.sql("SELECT count(*) FROM usage_record WHERE github_login = ? AND kind = 'QUESTION' AND at >= ?")
                .params(who, startOfDay(0)).query(Integer.class).single();
        return new Today(used, limitOf(who));
    }

    @Transactional
    public void record(String login, long spaceId, UsageKind kind, RunUsage usage) {
        jdbc.sql("INSERT INTO usage_record (github_login, space_id, kind, at, claude_calls, prompt_tokens, "
                        + "completion_tokens, cost_usd, jev_calls) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(normalize(login), spaceId, kind.name(), Timestamp.from(clock.instant()), usage.claudeCalls(),
                        usage.promptTokens(), usage.completionTokens(), usage.costUsd(), usage.jevCalls())
                .update();
    }

    /** Everyone with usage in the last days or a personal limit, most consuming first. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<PersonUsage> report(int days) {
        return jdbc.sql("""
                        SELECT p.login,
                               coalesce(sum(CASE WHEN r.kind = 'QUESTION' AND r.at >= ? THEN 1 ELSE 0 END), 0) AS today,
                               coalesce(sum(CASE WHEN r.kind = 'QUESTION' THEN 1 ELSE 0 END), 0) AS questions,
                               coalesce(sum(CASE WHEN r.kind = 'REVIEW' THEN 1 ELSE 0 END), 0) AS reviews,
                               coalesce(sum(r.prompt_tokens), 0) AS prompt, coalesce(sum(r.completion_tokens), 0) AS completion,
                               coalesce(sum(r.cost_usd), 0) AS cost, coalesce(sum(r.jev_calls), 0) AS jev,
                               l.daily_questions AS personal
                        FROM (SELECT github_login AS login FROM usage_record WHERE at >= ?
                              UNION SELECT github_login FROM usage_limit) p
                        LEFT JOIN usage_record r ON r.github_login = p.login AND r.at >= ?
                        LEFT JOIN usage_limit l ON l.github_login = p.login
                        GROUP BY p.login, l.daily_questions
                        ORDER BY cost DESC, questions DESC, p.login""")
                .params(startOfDay(0), startOfDay(days - 1), startOfDay(days - 1))
                .query((rs, i) -> {
                    Integer personal = (Integer) rs.getObject("personal");
                    return new PersonUsage(rs.getString("login"), rs.getInt("today"), rs.getInt("questions"),
                            rs.getInt("reviews"), rs.getLong("prompt"), rs.getLong("completion"), rs.getDouble("cost"),
                            rs.getInt("jev"), personal == null ? defaultLimit : personal, personal != null);
                }).list();
    }

    /** A personal daily limit; null goes back to the default. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void setLimit(String admin, String login, Integer dailyQuestions) {
        String who = normalize(login);
        if (dailyQuestions == null) {
            jdbc.sql("DELETE FROM usage_limit WHERE github_login = ?").params(who).update();
            return;
        }
        if (dailyQuestions < 0) {
            throw new IllegalArgumentException("A daily limit cannot be negative");
        }
        jdbc.sql("INSERT INTO usage_limit (github_login, daily_questions, set_by, set_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (github_login) DO UPDATE SET daily_questions = EXCLUDED.daily_questions, "
                        + "set_by = EXCLUDED.set_by, set_at = EXCLUDED.set_at")
                .params(who, dailyQuestions, normalize(admin), Timestamp.from(clock.instant())).update();
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    private int limitOf(String who) {
        return jdbc.sql("SELECT daily_questions FROM usage_limit WHERE github_login = ?").params(who)
                .query(Integer.class).optional().orElse(defaultLimit);
    }

    /** Midnight, in the app's time zone, daysAgo days before today. */
    private Timestamp startOfDay(int daysAgo) {
        ZonedDateTime midnight = LocalDate.now(clock).minusDays(daysAgo).atStartOfDay(clock.getZone());
        return Timestamp.from(midnight.toInstant());
    }

    private static String normalize(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }
}
