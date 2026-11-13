package app.rootology.access;

import app.rootology.TestcontainersConfiguration;
import app.rootology.privacy.RetentionJob;
import app.rootology.spaces.IdentityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invitation requests (#65) on a real database: one open request per identity, the daily cap, the motivation's
 * rules, the waiting time after a rejection, who may decide, and the retention. Each test uses its own
 * identities.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccessRequestServiceTest {

    static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(NOW, ZoneId.of("Europe/Rome"));
    }

    @Autowired
    AccessRequestService requests;

    @Autowired
    RetentionJob retentionJob;

    @Autowired
    JdbcClient jdbc;

    /** Every test starts from no request, so the cap and the lists are only its own. */
    @BeforeEach
    void clean() {
        jdbc.sql("delete from access_request").update();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    @Test
    void oneOpenRequestPerIdentityWithTheMotivationTrimmed() {
        requests.request(IdentityProvider.GITHUB, "req-1", "req-one", "one@example.org",
                "  Vorrei un orto condiviso.\n");

        assertThatThrownBy(() -> requests.request(IdentityProvider.GITHUB, "req-1", "req-one", "one@example.org",
                "Ancora?")).isInstanceOf(IllegalStateException.class);

        assertThat(count("select count(*) from access_request where subject = 'req-1'")).isEqualTo(1);
        assertThat(jdbc.sql("select motivation from access_request where subject = 'req-1'")
                .query(String.class).single()).isEqualTo("Vorrei un orto condiviso.");
        assertThat(requests.state(IdentityProvider.GITHUB, "req-1").pending()).isTrue();
    }

    @Test
    void theMotivationIsRequiredAndBounded() {
        assertThatThrownBy(() -> requests.request(IdentityProvider.GITHUB, "req-2", "req-two", null, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> requests.request(IdentityProvider.GITHUB, "req-2", "req-two", null, "a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(count("select count(*) from access_request where subject = 'req-2'")).isZero();

        requests.request(IdentityProvider.GITHUB, "req-2", "req-two", null, "a".repeat(500));

        assertThat(count("select count(*) from access_request where subject = 'req-2'")).isEqualTo(1);
    }

    @Test
    void withdrawDeletesTheOpenRequest() {
        requests.request(IdentityProvider.GOOGLE, "req-3", "req-three", "three@example.org", "Per favore");
        requests.withdraw(IdentityProvider.GOOGLE, "req-3");

        assertThat(requests.state(IdentityProvider.GOOGLE, "req-3").none()).isTrue();
        assertThat(count("select count(*) from access_request where subject = 'req-3'")).isZero();
    }

    @Test
    void theDailyCapRefusesTheRequestAfterItAndNotifiesNobody() {
        // Today is already full: the cap is the default of ten requests.
        for (int i = 0; i < 10; i++) {
            jdbc.sql("insert into access_request (provider, subject, login, motivation, status, created_at) "
                            + "values ('GITHUB', ?, ?, 'x', 'PENDING', ?)")
                    .params("cap-" + i, "cap-" + i, Timestamp.from(NOW)).update();
        }

        long notificationsBefore = count("select count(*) from notification where kind = 'ACCESS_REQUEST'");

        assertThatThrownBy(() -> requests.request(IdentityProvider.GITHUB, "req-4", "req-four", null, "Please"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("select count(*) from access_request where subject = 'req-4'")).isZero();
        assertThat(requests.state(IdentityProvider.GITHUB, "req-4").capReached()).isTrue();
        assertThat(count("select count(*) from notification where kind = 'ACCESS_REQUEST'"))
                .as("a refused request notifies nobody").isEqualTo(notificationsBefore);
    }

    @Test
    void aRejectedPersonWaitsBeforeAskingAgain() {
        requests.request(IdentityProvider.GITHUB, "req-5", "req-five", null, "Please");
        long id = jdbc.sql("select id from access_request where subject = 'req-5'").query(Long.class).single();
        jdbc.sql("update access_request set status = 'REJECTED', decided_by = 'admin', decided_at = ? where id = ?")
                .params(Timestamp.from(NOW), id).update();

        assertThat(requests.state(IdentityProvider.GITHUB, "req-5").rejected()).isTrue();
        assertThat(requests.state(IdentityProvider.GITHUB, "req-5").retryAfter())
                .isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
        assertThatThrownBy(() -> requests.request(IdentityProvider.GITHUB, "req-5", "req-five", null, "Please"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("select count(*) from access_request where subject = 'req-5'")).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "plain-person", roles = "USER")
    void onlyAnAdminListsAndDecides() {
        requests.request(IdentityProvider.GITHUB, "req-6", "req-six", null, "Please");

        assertThatThrownBy(() -> requests.pending("plain-person")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> requests.decided("plain-person")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "admin-ada", roles = "ADMIN")
    void approvingAndRejectingRecordWhoAndWhen() {
        requests.request(IdentityProvider.GITHUB, "req-7", "req-seven", null, "Please");
        requests.request(IdentityProvider.GITHUB, "req-8", "req-eight", null, "Please");
        long seven = jdbc.sql("select id from access_request where subject = 'req-7'").query(Long.class).single();
        long eight = jdbc.sql("select id from access_request where subject = 'req-8'").query(Long.class).single();

        assertThat(requests.pending("admin-ada")).hasSize(2);

        requests.approve("admin-ada", seven);
        requests.reject("ADMIN-ADA", eight);

        assertThat(requests.isApproved(IdentityProvider.GITHUB, "req-7")).isTrue();
        assertThat(requests.state(IdentityProvider.GITHUB, "req-8").rejected()).isTrue();
        assertThat(requests.decided("admin-ada")).hasSize(2)
                .allSatisfy(view -> {
                    assertThat(view.decidedBy()).isEqualTo("admin-ada");
                    assertThat(view.decidedAt()).isEqualTo(NOW);
                });
        assertThat(requests.pending("admin-ada")).isEmpty();
    }

    @Test
    void retentionDeletesRejectedPendingAndUnusedApprovalsAndNothingElse() {
        insert("old-rejected", "REJECTED", NOW.minusSeconds(40L * 24 * 3600), null);
        insert("new-rejected", "REJECTED", NOW.minusSeconds(10L * 24 * 3600), null);
        insert("old-pending", "PENDING", null, NOW.minusSeconds(100L * 24 * 3600));
        insert("new-pending", "PENDING", null, NOW.minusSeconds(10L * 24 * 3600));
        insert("old-approved", "APPROVED", null, NOW.minusSeconds(100L * 24 * 3600));
        insert("new-approved", "APPROVED", null, NOW.minusSeconds(10L * 24 * 3600));

        retentionJob.run();

        assertThat(jdbc.sql("select subject from access_request where subject like '%-rejected' or subject like '%-pending' "
                + "or subject like '%-approved'").query(String.class).list())
                .containsExactlyInAnyOrder("new-rejected", "new-pending", "new-approved");
    }

    private void insert(String subject, String status, Instant decidedAt, Instant createdAt) {
        jdbc.sql("insert into access_request (provider, subject, login, motivation, status, created_at, decided_by, "
                        + "decided_at) values ('GITHUB', ?, ?, 'x', ?, ?, ?, ?)")
                .params(subject, subject, status, Timestamp.from(createdAt != null ? createdAt : NOW), 
                        decidedAt == null ? null : "admin", decidedAt == null ? null : Timestamp.from(decidedAt))
                .update();
    }
}
