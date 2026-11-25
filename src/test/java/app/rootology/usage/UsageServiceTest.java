package app.rootology.usage;

import app.rootology.notifications.SettableClockContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsageServiceTest extends SettableClockContext {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");

    @Autowired
    UsageService usage;

    @Value("${rootology.usage.daily-questions}")
    int dailyLimit;

    private static final RunUsage QUESTION = new RunUsage(2, 1200, 80, 0.0049, 1);

    @Test
    void theDailyLimitStopsQuestionsUntilMidnightInItaly() {
        now = Instant.parse("2026-11-04T22:30:00Z"); // 23:30 in Italy: still today
        usage.record("Mara", 1, UsageKind.QUESTION, QUESTION);
        usage.record("mara", 1, UsageKind.REVIEW, new RunUsage(1, 300, 40, 0.001, 0));
        for (int i = 1; i < dailyLimit; i++) {
            usage.requireQuestionAvailable("mara");
            usage.record("mara", 1, UsageKind.QUESTION, QUESTION);
        }

        assertThat(usage.today("MARA")).isEqualTo(new UsageService.Today(dailyLimit, dailyLimit));
        assertThatThrownBy(() -> usage.requireQuestionAvailable("mara")).isInstanceOf(UsageLimitException.class)
                .hasMessageContaining(dailyLimit + " questions for today");

        now = Instant.parse("2026-11-04T23:05:00Z"); // 00:05 in Italy: a new day
        assertThat(usage.today("mara").used()).isZero();
        usage.requireQuestionAvailable("mara");
    }

    @Test
    @WithMockUser(username = "root", roles = "ADMIN")
    void anAdminSeesUsageAndSetsPersonalLimits() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        usage.record("nico", 3, UsageKind.QUESTION, QUESTION);
        usage.record("nico", 3, UsageKind.QUESTION, QUESTION);
        usage.setLimit("root", "Nico", 5);

        assertThat(usage.today("nico")).isEqualTo(new UsageService.Today(2, 5));
        assertThat(usage.report(7)).filteredOn(u -> u.login().equals("nico")).singleElement().satisfies(u -> {
            assertThat(u.questionsToday()).isEqualTo(2);
            assertThat(u.promptTokens()).isEqualTo(2400);
            assertThat(u.costUsd()).isEqualTo(0.0098, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(u.jevCalls()).isEqualTo(2);
            assertThat(u.limit()).isEqualTo(5);
            assertThat(u.personalLimit()).isTrue();
        });

        usage.setLimit("root", "nico", null);
        assertThat(usage.today("nico").limit()).isEqualTo(dailyLimit);
        assertThatThrownBy(() -> usage.setLimit("root", "nico", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithMockUser(username = "olaf")
    void onlyAnAdminSeesUsageOrChangesLimits() {
        assertThatThrownBy(() -> usage.report(7)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> usage.setLimit("olaf", "olaf", 100)).isInstanceOf(AccessDeniedException.class);
    }
}
