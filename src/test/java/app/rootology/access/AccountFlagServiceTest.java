package app.rootology.access;

import app.rootology.notifications.SettableClockContext;
import app.rootology.TestSpaces;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The nightly signal that flags an account creating only useless traffic (#165) on a real database: each edge
 * of the signal, the two decisions (an admin only), and the rule that an account is flagged once. Each test
 * uses its own logins, so the shared database never confuses two of them.
 */
class AccountFlagServiceTest extends SettableClockContext {

    /** The signal reads "now" from the Clock bean, set here to speak of days precisely. */
    @BeforeEach
    void setTheClock() {
        now = Instant.parse("2026-11-20T10:00:00Z");
    }

    @Autowired
    AccountFlagService flags;

    @Autowired
    AccountBlockService blocks;

    @Autowired
    SpaceService spaces;

    @Autowired
    JdbcClient jdbc;

    /** A person who signed in {@code days} days ago, whose personal space has no garden. */
    private String signedIn(String login, int days) {
        spaces.signedIn(login);
        jdbc.sql("update app_user set first_login_at = :at where login = :l")
                .param("at", Timestamp.from(now.minus(Duration.ofDays(days)))).param("l", login).update();
        return login;
    }

    /** One finished question the filter judged, off-domain or not. */
    private void question(String login, boolean offDomain, Instant at) {
        question(login, offDomain, false, false, at);
    }

    /** A finished question: judged by the filter or not (it could not), and answered by a person or not. */
    private void question(String login, boolean offDomain, boolean unjudged, boolean answeredByHuman, Instant at) {
        jdbc.sql("""
                insert into pipeline_run (process_id, question_id, question, noise_dropout, noise_spurious,
                    noise_mislabel, noise_near_subject, noise_seed, decision_answer, decision_confidence,
                    decision_escalated, decision_reason, answer, answered_by_human, answer_text, finished_at,
                    started_by, off_domain, domain_unjudged)
                values (gen_random_uuid()::text, 'free', 'quanto costa una lavatrice?', 0, 0, 0, false, 0, '{}', 1,
                    false, 'test', '{}', :human, 'an answer', :at, :login, :off, :unjudged)""")
                .param("human", answeredByHuman).param("at", Timestamp.from(at)).param("login", login)
                .param("off", offDomain).param("unjudged", unjudged).update();
    }

    private void offDomain(String login, int many) {
        for (int i = 0; i < many; i++) {
            question(login, true, Instant.parse("2026-11-19T09:00:00Z").plusSeconds(i));
        }
    }

    /** The open flags of one login, read from the table so no test needs the admin role just to look. */
    private List<AccountFlagView> openFor(String login) {
        return jdbc.sql("""
                        select id, login, flagged_at, judged_questions, off_domain_questions, days_without_garden,
                               status, decided_by, decided_at
                        from account_flag where login = :l and status = 'OPEN' order by id""")
                .param("l", login)
                .query((rs, row) -> new AccountFlagView(rs.getLong(1), rs.getString(2),
                        rs.getTimestamp(3).toInstant(), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                        AccountFlagStatus.valueOf(rs.getString(7)), rs.getString(8),
                        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant()))
                .list();
    }

    private String statusOf(String login) {
        return jdbc.sql("select status from account_flag where login = :l order by id desc limit 1")
                .param("l", login).query(String.class).optional().orElse(null);
    }

    private boolean flagged(String login) {
        return jdbc.sql("select count(*) from account_flag where login = :l").param("l", login)
                .query(Long.class).single() > 0;
    }

    @Test
    void anAccountYoungerThanTheDaysIsNotFlagged() {
        signedIn("flg-young", 2);
        offDomain("flg-young", 10);

        flags.evaluate();

        assertThat(flagged("flg-young")).isFalse();
    }

    @Test
    void aGardenInASharedSpaceKeepsTheSignalAway() {
        String host = signedIn("flg-host", 30);
        String guest = signedIn("flg-guest", 30);
        long shared = TestSpaces.personal(spaces, host);
        spaces.addMember(shared, host, guest, Role.MEMBER);
        jdbc.sql("insert into garden (space_id, name, created_at) values (:s, 'Orto condiviso', now())")
                .param("s", shared).update();
        offDomain(guest, 10);

        flags.evaluate();

        assertThat(flagged(guest)).as("a garden in a space it belongs to").isFalse();
    }

    @Test
    void fewerJudgedQuestionsThanTheMinimumIsNotEnough() {
        signedIn("flg-few", 30);
        offDomain("flg-few", 4);

        flags.evaluate();

        assertThat(flagged("flg-few")).isFalse();
    }

    @Test
    void runsTheFilterCouldNotJudgeAreNotCounted() {
        signedIn("flg-unjudged", 30);
        for (int i = 0; i < 10; i++) {
            question("flg-unjudged", true, true, false, Instant.parse("2026-11-19T09:00:00Z").plusSeconds(i));
        }

        flags.evaluate();

        assertThat(flagged("flg-unjudged")).isFalse();
    }

    @Test
    void answersToWaitingQuestionsAreNotCounted() {
        signedIn("flg-answered", 30);
        for (int i = 0; i < 10; i++) {
            question("flg-answered", true, false, true, Instant.parse("2026-11-19T09:00:00Z").plusSeconds(i));
        }

        flags.evaluate();

        assertThat(flagged("flg-answered")).as("a waiting question is not what a person asks").isFalse();
    }

    @Test
    void theShareJustBelowTheThresholdIsNotEnoughAndAtItIs() {
        signedIn("flg-below", 30);
        signedIn("flg-at", 30);
        for (int i = 0; i < 10; i++) {
            question("flg-below", i < 6, Instant.parse("2026-11-19T09:00:00Z").plusSeconds(i));
            question("flg-at", i < 7, Instant.parse("2026-11-19T09:00:00Z").plusSeconds(i));
        }

        flags.evaluate();

        assertThat(flagged("flg-below")).as("6 of 10 is below 70%").isFalse();
        assertThat(flagged("flg-at")).as("7 of 10 is at it").isTrue();
    }

    @Test
    void aBlockedAccountAndAnAdminAreNeverFlagged() {
        signedIn("flg-blocked", 30);
        offDomain("flg-blocked", 10);
        jdbc.sql("update app_user set blocked_at = now() where login = 'flg-blocked'").update();

        signedIn("flg-admin", 30);
        offDomain("flg-admin", 10);
        jdbc.sql("""
                        insert into app_role_grant (github_login, role, granted_by, granted_at)
                        values ('flg-admin', 'ADMIN', 'flg-setup', now())""").update();
        try {
            flags.evaluate();

            assertThat(flagged("flg-blocked")).as("already blocked").isFalse();
            assertThat(flagged("flg-admin")).as("an admin").isFalse();
        } finally {
            jdbc.sql("delete from app_role_grant where github_login = 'flg-admin' and granted_by = 'flg-setup'")
                    .update();
        }
    }

    @Test
    @WithMockUser(username = "flg-admin", roles = "ADMIN")
    void anAccountIsFlaggedOnceAndNotAgainAfterADismissal() {
        signedIn("flg-once", 30);
        offDomain("flg-once", 10);

        flags.evaluate();
        assertThat(openFor("flg-once")).singleElement().satisfies(flag -> {
            assertThat(flag.judgedQuestions()).isEqualTo(10);
            assertThat(flag.offDomainQuestions()).isEqualTo(10);
            assertThat(flag.daysWithoutGarden()).isEqualTo(30);
        });

        flags.evaluate();
        assertThat(openFor("flg-once")).as("no second flag while one is open").hasSize(1);

        flags.dismiss("flg-admin", openFor("flg-once").getFirst().id());
        assertThat(statusOf("flg-once")).isEqualTo("DISMISSED");

        flags.evaluate();
        assertThat(openFor("flg-once")).as("an admin chose to leave it").isEmpty();
        assertThat(statusOf("flg-once")).isEqualTo("DISMISSED");
    }

    @Test
    @WithMockUser(username = "flg-admin", roles = "ADMIN")
    void blockingAFlagClosesItAndAnUnblockDoesNotFlagItAgain() {
        signedIn("flg-blockme", 30);
        offDomain("flg-blockme", 10);
        flags.evaluate();
        long flagId = openFor("flg-blockme").getFirst().id();

        flags.block("flg-admin", flagId, "domande fuori tema");

        assertThat(flags.open()).noneMatch(f -> f.login().equals("flg-blockme"));
        assertThat(statusOf("flg-blockme")).isEqualTo("BLOCKED");
        assertThat(blocks.isBlocked("flg-blockme")).isTrue();

        blocks.unblock("flg-admin", "flg-blockme");

        flags.evaluate();
        assertThat(statusOf("flg-blockme")).as("an unblocked account is not raised again").isEqualTo("BLOCKED");
        assertThat(flags.open()).noneMatch(f -> f.login().equals("flg-blockme"));
        assertThat(flagged("flg-blockme")).isTrue();
    }

    @Test
    @WithMockUser(username = "flg-nobody")
    void onlyAnAdminReadsDecidesAndBlocks() {
        signedIn("flg-nobody", 30);
        offDomain("flg-nobody", 10);
        flags.evaluate();
        long flagId = openFor("flg-nobody").getFirst().id();

        assertThatThrownBy(flags::open).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> flags.dismiss("flg-nobody", flagId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> flags.block("flg-nobody", flagId, null)).isInstanceOf(AccessDeniedException.class);
        assertThat(statusOf("flg-nobody")).as("nothing decided").isEqualTo("OPEN");
    }

    @Test
    @WithMockUser(username = "flg-admin", roles = "ADMIN")
    void aFlagIsDecidedOnlyOnce() {
        signedIn("flg-twice", 30);
        offDomain("flg-twice", 10);
        flags.evaluate();
        long flagId = openFor("flg-twice").getFirst().id();

        flags.dismiss("flg-admin", flagId);

        assertThatThrownBy(() -> flags.dismiss("flg-admin", flagId)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been decided");
        assertThatThrownBy(() -> flags.block("flg-admin", flagId, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already been decided");
    }

    @Test
    void theSettingsAreValidated() {
        assertThatThrownBy(() -> new SuspiciousAccounts(0, 10, 5, 0.7))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("days-without-garden");
        assertThatThrownBy(() -> new SuspiciousAccounts(7, 10, 11, 0.7))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("min-judged");
        assertThatThrownBy(() -> new SuspiciousAccounts(7, 10, 5, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("off-domain-share");
        assertThat(new SuspiciousAccounts(7, 10, 5, 1).offDomainShare()).isEqualTo(1);
    }
}
