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
        long id = spaces.spacesOf(owner).stream().filter(io.github.vincenzofiore.rootology.spaces.SpaceSummary::personal)
                .findFirst().orElseThrow().id();
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

    /** The full message Mailpit received for this address (subject, Text, HTML). */
    private JsonNode messageTo(String address) throws InterruptedException {
        JsonNode found = messagesTo(address);
        String id = found.path("messages").get(0).path("ID").asString();
        return JsonMapper.builder().build().readTree(RestClient.create(mailpit).get()
                .uri("/api/v1/message/" + id).retrieve().body(String.class));
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
        events.publishEvent(new EscalationResolved("p-1", space, "mia", "owen", now));

        assertThat(notifications.unread("owen")).as("the person who answered gets nothing new").isZero();
        assertThat(notifications.of("owen", 10)).singleElement()
                .extracting(NotificationView::resolvedBy).isEqualTo("owen");
        assertThat(notifications.of("mia", 10)).hasSize(2).anySatisfy(n -> {
            assertThat(n.resolvedBy()).isEqualTo("owen");
            assertThat(n.resolvedAt()).isNotNull();
        }).anySatisfy(n -> {
            assertThat(n.answered()).isTrue();
            assertThat(n.processId()).isEqualTo("p-1");
            assertThat(n.body()).isEqualTo("owen answered your question");
            assertThat(n.unread()).isTrue();
        });
    }

    @Test
    void theAskerGetsTheAnswerInTheAppAndByEmailButTheOthersOnlySeeTheResolution() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("elia", "fede", "gaia");
        notifications.save("elia", new NotificationPreferences("elia@example.org", true, true, null, null));

        waiting("p-5", space, "elia", "Quali sono le compagne della salvia?");

        assertThat(notifications.unread("elia")).isEqualTo(1);
        assertThat(notifications.unread("fede")).isEqualTo(1);
        notifications.markRead("elia", null);
        notifications.markRead("fede", null);

        events.publishEvent(new EscalationResolved("p-5", space, "elia", "fede", now));

        assertThat(notifications.unread("elia")).as("the new notification is unread").isEqualTo(1);
        assertThat(notifications.of("elia", 10)).hasSize(2)
                .anySatisfy(n -> assertThat(n.answered()).isTrue())
                .anySatisfy(n -> assertThat(n.resolvedBy()).isEqualTo("fede"));
        assertThat(notifications.unread("fede")).as("no new notification").isZero();
        assertThat(notifications.of("fede", 10)).singleElement().extracting(NotificationView::resolvedBy)
                .isEqualTo("fede");

        now = now.plus(Duration.ofMinutes(3));
        assertThat(notifications.digest()).as("only elia asked, only elia is emailed").isEqualTo(1);
        JsonNode elia = messageTo("elia@example.org");
        assertThat(elia.path("Subject").asString()).isEqualTo("Rootology: la tua domanda ha una risposta");
        assertThat(elia.path("Text").asString()).as("the link opens the space of the question")
                .contains("/history?space=" + space);
        assertThat(elia.path("HTML").asString()).contains("/history?space=" + space);
    }

    @Test
    void anAskerWhoLeftTheSpaceIsNotNotified() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("nina", "otto", "pia");
        notifications.save("otto", new NotificationPreferences("otto@example.org", true, true, null, null));

        waiting("p-9", space, "otto", "Domanda?");
        notifications.markRead("otto", null);
        spaces.removeMember(space, "otto", "otto"); // he leaves while his question waits

        events.publishEvent(new EscalationResolved("p-9", space, "otto", "nina", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.of("otto", 10)).as("only the waiting one, now resolved").singleElement()
                .extracting(NotificationView::answered).isEqualTo(false);
        assertThat(notifications.digest()).isZero();
    }

    @Test
    void aPersonWhoAnswersTheirOwnQuestionGetsNothingNew() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("ivo", "lia", "noa");

        waiting("p-6", space, "ivo", "Domanda?");
        notifications.markRead("ivo", null);

        events.publishEvent(new EscalationResolved("p-6", space, "ivo", "ivo", now));

        assertThat(notifications.of("ivo", 10)).singleElement().extracting(NotificationView::answered).isEqualTo(false);
        assertThat(notifications.unread("ivo")).isZero();
    }

    @Test
    void waitingQuestionsAreEmailedTogetherAfterTheGroupingWindowOutsideQuietHours() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("pia", "remo", "sara");
        notifications.save("pia", new NotificationPreferences("pia@example.org", true, true, null, null));
        notifications.save("remo", new NotificationPreferences("remo@example.org", true, true, LocalTime.of(9, 0),
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
    void theAnswererIsNeverEmailed() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("teo", "ugo", "vera");
        notifications.save("teo", new NotificationPreferences("teo@example.org", true, true, null, null));
        notifications.rememberEmail("ugo", "Ugo@Example.org");
        notifications.rememberEmail("ugo", "other@example.org");

        waiting("p-4", space, "ugo", "Domanda?");
        events.publishEvent(new EscalationResolved("p-4", space, "ugo", "teo", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).as("ugo asked, teo only answered").isEqualTo(1);
        assertThat(notifications.preferences("ugo").email()).as("the first remembered address stays")
                .isEqualTo("ugo@example.org");
        assertThatThrownBy(() -> notifications.save("teo", new NotificationPreferences("nope", true, true, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAskersEmailPreferencesAndQuietHoursAreRespected() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("ada", "bea", "carla");
        notifications.save("ada", new NotificationPreferences("ada@example.org", false, true, null, null));

        waiting("p-7", space, "ada", "Domanda?");
        events.publishEvent(new EscalationResolved("p-7", space, "ada", "bea", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).as("ada turned answer emails off").isZero();

        now = Instant.parse("2026-11-04T10:00:00Z");
        long quiet = space("eva", "fio", "gina");
        notifications.save("eva", new NotificationPreferences("eva@example.org", true, true, LocalTime.of(10, 0),
                LocalTime.of(12, 0)));

        waiting("p-8", quiet, "eva", "Domanda?");
        events.publishEvent(new EscalationResolved("p-8", quiet, "eva", "fio", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).as("eva is in her quiet hours").isZero();

        now = now.plus(Duration.ofHours(2)); // 13:03 in Italy: the email waits for the end of the quiet hours
        assertThat(notifications.digest()).isEqualTo(1);
        messagesTo("eva@example.org");
    }

    @Test
    void quietHoursMayCrossMidnight() {
        NotificationPreferences night = new NotificationPreferences(null, true, true, LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertThat(night.quietAt(LocalTime.of(23, 30))).isTrue();
        assertThat(night.quietAt(LocalTime.of(6, 59))).isTrue();
        assertThat(night.quietAt(LocalTime.of(7, 0))).isFalse();
        assertThat(NotificationPreferences.DEFAULT.quietAt(LocalTime.NOON)).isFalse();
    }
}
