package app.rootology.notifications;

import app.rootology.TestcontainersConfiguration;
import app.rootology.mail.MailService;
import app.rootology.pipeline.EscalationWaiting;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The digest does not lose emails when SMTP fails (#153): the rows are marked only after a successful send,
 * so a failed send leaves them for the next run.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.notifications.digest-every=PT1H",
        "rootology.telegram.send-every=PT1H", "rootology.telegram.poll-every=PT1H"})
class DigestFailureTest {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    static Instant now = Instant.parse("2026-11-04T10:00:00Z");

    @TestBean
    Clock clock;

    static Clock clock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ITALY;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }

    @Autowired
    NotificationService notifications;

    @Autowired
    SpaceService spaces;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    JdbcClient jdbc;

    /** The sender, mocked so the test can make it fail and then work. */
    @MockitoBean
    MailService mail;

    @BeforeEach
    void emptyTheOutbox() {
        jdbc.sql("delete from notification").update();
    }

    private long space(String owner, String member) {
        spaces.signedIn(owner);
        spaces.signedIn(member);
        long id = spaces.spacesOf(owner).stream().filter(app.rootology.spaces.SpaceSummary::personal)
                .findFirst().orElseThrow().id();
        spaces.addMember(id, owner, member, Role.MEMBER);
        return id;
    }

    private int unemailed(String recipient) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE recipient = ? AND emailed_at IS NULL")
                .params(recipient).query(Integer.class).single();
    }

    @Test
    void aFailedSendLeavesTheRowsUnmarkedAndTheNextRunSendsThem() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("d-ada", "d-bo");
        notifications.save("d-ada", new NotificationPreferences("d-ada@example.org", true, true, true, true, null, null));
        events.publishEvent(new EscalationWaiting("d-1", space, 0, "d-bo", "Quali sono le compagne della lavanda?", "lavanda", now));

        now = now.plus(Duration.ofMinutes(3));
        when(mail.sendNow(any())).thenReturn(false);
        assertThat(notifications.digest()).as("nothing went out").isZero();
        assertThat(unemailed("d-ada")).as("the row stays pending for the next run").isEqualTo(1);

        when(mail.sendNow(any())).thenReturn(true);
        assertThat(notifications.digest()).as("the next run sends it").isEqualTo(1);
        assertThat(unemailed("d-ada")).as("now marked").isZero();
        assertThat(notifications.digest()).as("never twice").isZero();
    }
}
