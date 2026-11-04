package io.github.vincenzofiore.rootology.notifications;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.pipeline.EscalationResolved;
import io.github.vincenzofiore.rootology.pipeline.EscalationWaiting;
import io.github.vincenzofiore.rootology.spaces.Role;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Notifications of waiting questions (#26): who gets them, their state, and the grouped emails (Mailpit). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "rootology.notifications.digest-every=PT1H")
class NotificationServiceTest {

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

    @Value("${rootology.test.mailpit-api}")
    String mailpit;

    /** A shared space: owner, a member and a guest. */
    private long space(String owner, String member, String guest) {
        spaces.signedIn(owner);
        spaces.signedIn(member);
        spaces.signedIn(guest);
        long id = spaces.spacesOf(owner).getFirst().id();
        spaces.addMember(id, owner, member, Role.MEMBER);
        spaces.addMember(id, owner, guest, Role.GUEST);
        return id;
    }

    private void waiting(String process, long space, String askedBy, String question) {
        events.publishEvent(new EscalationWaiting(process, space, askedBy, question, "lavanda is not in the dataset", now));
    }

    private JsonNode messagesTo(String address) throws InterruptedException {
        RestClient api = RestClient.create(mailpit);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = JsonMapper.builder().build().readTree(api.get()
                    .uri(u -> u.path("/api/v1/search").queryParam("query", "to:\"" + address + "\"").build())
                    .retrieve().body(String.class));
            if (found.path("messages_count").asInt() > 0) {
                return found;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No email to " + address);
    }

    @Test
    void aWaitingQuestionNotifiesOwnersAndMembersButNotGuestsAndIsMarkedWhenAnswered() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("owen", "mia", "gil");

        waiting("p-1", space, "mia", "Quali sono le compagne della lavanda?");

        assertThat(notifications.unread("owen")).isEqualTo(1);
        assertThat(notifications.unread("mia")).as("the asker too").isEqualTo(1);
        assertThat(notifications.unread("gil")).as("a guest cannot answer").isZero();
        assertThat(notifications.of("owen", 10)).singleElement().satisfies(n -> {
            assertThat(n.body()).contains("mia asked: Quali sono le compagne della lavanda?");
            assertThat(n.processId()).isEqualTo("p-1");
        });

        notifications.markRead("owen", null);
        events.publishEvent(new EscalationResolved("p-1", space, "owen", now));

        assertThat(notifications.unread("owen")).isZero();
        assertThat(notifications.of("mia", 10)).singleElement().extracting(NotificationView::resolvedBy).isEqualTo("owen");
    }

    @Test
    void waitingQuestionsAreEmailedTogetherAfterTheGroupingWindowOutsideQuietHours() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("pia", "remo", "sara");
        notifications.save("pia", new NotificationPreferences("pia@example.org", true, null, null));
        notifications.save("remo", new NotificationPreferences("remo@example.org", true, LocalTime.of(9, 0),
                LocalTime.of(12, 0)));

        waiting("p-2", space, "remo", "Prima domanda?");
        waiting("p-3", space, "remo", "Seconda domanda?");
        assertThat(notifications.digest()).as("not yet: within the grouping window").isZero();

        now = now.plus(Duration.ofMinutes(3)); // 11:03 in Italy: remo is in his quiet hours
        assertThat(notifications.digest()).isEqualTo(1);
        JsonNode pia = messagesTo("pia@example.org");
        assertThat(pia.path("messages_count").asInt()).isEqualTo(1);
        assertThat(pia.path("messages").get(0).path("Subject").asString())
                .isEqualTo("Rootology: 2 domande aspettano una risposta");

        now = Instant.parse("2026-11-04T11:30:00Z"); // 12:30 in Italy
        assertThat(notifications.digest()).as("remo, after his quiet hours").isEqualTo(1);
        assertThat(notifications.digest()).as("never twice").isZero();
        messagesTo("remo@example.org");
    }

    @Test
    void answeredQuestionsAndPeopleWhoDoNotWantEmailsGetNone() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("teo", "ugo", "vera");
        notifications.save("teo", new NotificationPreferences("teo@example.org", false, null, null));
        notifications.rememberEmail("ugo", "Ugo@Example.org");
        notifications.rememberEmail("ugo", "other@example.org");

        waiting("p-4", space, "ugo", "Domanda?");
        events.publishEvent(new EscalationResolved("p-4", space, "teo", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).isZero();
        assertThat(notifications.preferences("ugo").email()).as("the first remembered address stays")
                .isEqualTo("ugo@example.org");
        assertThatThrownBy(() -> notifications.save("teo", new NotificationPreferences("nope", true, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quietHoursMayCrossMidnight() {
        NotificationPreferences night = new NotificationPreferences(null, true, LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertThat(night.quietAt(LocalTime.of(23, 30))).isTrue();
        assertThat(night.quietAt(LocalTime.of(6, 59))).isTrue();
        assertThat(night.quietAt(LocalTime.of(7, 0))).isFalse();
        assertThat(NotificationPreferences.DEFAULT.quietAt(LocalTime.NOON)).isFalse();
    }
}
