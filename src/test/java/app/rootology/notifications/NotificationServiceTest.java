package app.rootology.notifications;

import app.rootology.TestcontainersConfiguration;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.EscalationResolved;
import app.rootology.pipeline.EscalationWaiting;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Notifications of waiting questions (#26): who gets them, their state, and the grouped emails (Mailpit). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.notifications.digest-every=PT1H",
        "rootology.telegram.send-every=PT1H", "rootology.telegram.poll-every=PT1H"})
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
    GardenService gardens;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TelegramLinks telegramLinks;

    /** Never the real Telegram (#38): the tests say what each send answers. */
    @MockitoBean
    TelegramClient telegram;

    @Value("${rootology.test.mailpit-api}")
    String mailpit;

    /**
     * The outbox starts empty: the rows another test class left are pending too, and a digest would word
     * them in the same message (the tests of this class count the digests sent).
     */
    @BeforeEach
    void emptyTheOutbox() {
        jdbc.sql("delete from notification").update();
    }

    /** A shared space: owner, a member and a guest. */
    private long space(String owner, String member, String guest) {
        spaces.signedIn(owner);
        spaces.signedIn(member);
        spaces.signedIn(guest);
        long id = spaces.spacesOf(owner).stream().filter(app.rootology.spaces.SpaceSummary::personal)
                .findFirst().orElseThrow().id();
        spaces.addMember(id, owner, member, Role.MEMBER);
        spaces.addMember(id, owner, guest, Role.GUEST);
        return id;
    }

    private void waiting(String process, long space, String askedBy, String question) {
        events.publishEvent(new EscalationWaiting(process, space, 0, askedBy, question, "lavanda is not in the dataset", now));
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
        long space = space("n-owen", "n-mia", "n-gil");

        waiting("p-1", space, "n-mia", "Quali sono le compagne della lavanda?");

        assertThat(notifications.unread("n-owen")).isEqualTo(1);
        assertThat(notifications.unread("n-mia")).as("the asker too").isEqualTo(1);
        assertThat(notifications.unread("n-gil")).as("a guest cannot answer").isZero();
        assertThat(notifications.of("n-owen", 10)).singleElement().satisfies(n -> {
            // The data only (#94): who asked and the question, no sentence and no decision reason.
            assertThat(n.askedBy()).isEqualTo("n-mia");
            assertThat(n.body()).isEqualTo("Quali sono le compagne della lavanda?");
            assertThat(n.processId()).isEqualTo("p-1");
        });

        notifications.markRead("n-owen", null);
        events.publishEvent(new EscalationResolved("p-1", space, 0, "n-mia", "n-owen", now));

        assertThat(notifications.unread("n-owen")).as("the person who answered gets nothing new").isZero();
        assertThat(notifications.of("n-owen", 10)).singleElement()
                .extracting(NotificationView::resolvedBy).isEqualTo("n-owen");
        assertThat(notifications.of("n-mia", 10)).hasSize(2).anySatisfy(n -> {
            assertThat(n.resolvedBy()).isEqualTo("n-owen");
            assertThat(n.resolvedAt()).isNotNull();
        }).anySatisfy(n -> {
            assertThat(n.answered()).isTrue();
            assertThat(n.processId()).isEqualTo("p-1");
            assertThat(n.body()).as("who answered is in resolvedBy (#94)").isNull();
            assertThat(n.unread()).isTrue();
        });
    }

    /** A waiting notification nobody opened before the answer came leaves the bell: it asks nothing any more. */
    @Test
    void aWaitingNotificationIsReadOnceTheQuestionIsAnswered() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-rita", "n-sara", "n-tea");

        waiting("p-9", space, "n-sara", "Quali sono le compagne del cavolo?");
        assertThat(notifications.unread("n-rita")).isEqualTo(1);

        // Answered by someone else (an owner who never opened the bell's notification, as in production).
        events.publishEvent(new EscalationResolved("p-9", space, 0, "n-sara", "someone", now));

        assertThat(notifications.unread("n-rita")).as("unread before, nothing to do now").isZero();
        assertThat(notifications.of("n-rita", 10)).singleElement().satisfies(n -> {
            assertThat(n.resolvedAt()).isNotNull();
            assertThat(n.unread()).isFalse();
        });
        assertThat(notifications.unread("n-sara")).as("only the answer is new for the asker").isEqualTo(1);
    }

    @Test
    void theAskerGetsTheAnswerInTheAppAndByEmailButTheOthersOnlySeeTheResolution() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-elia", "n-fede", "n-gaia");
        notifications.save("n-elia", new NotificationPreferences("n-elia@example.org", true, true, true, true, null, null));

        waiting("p-5", space, "n-elia", "Quali sono le compagne della salvia?");

        assertThat(notifications.unread("n-elia")).isEqualTo(1);
        assertThat(notifications.unread("n-fede")).isEqualTo(1);
        notifications.markRead("n-elia", null);
        notifications.markRead("n-fede", null);

        events.publishEvent(new EscalationResolved("p-5", space, 0, "n-elia", "n-fede", now));

        assertThat(notifications.unread("n-elia")).as("the new notification is unread").isEqualTo(1);
        assertThat(notifications.of("n-elia", 10)).hasSize(2)
                .anySatisfy(n -> assertThat(n.answered()).isTrue())
                .anySatisfy(n -> assertThat(n.resolvedBy()).isEqualTo("n-fede"));
        assertThat(notifications.unread("n-fede")).as("no new notification").isZero();
        assertThat(notifications.of("n-fede", 10)).singleElement().extracting(NotificationView::resolvedBy)
                .isEqualTo("n-fede");

        now = now.plus(Duration.ofMinutes(3));
        assertThat(notifications.digest()).as("only elia asked, only elia is emailed").isEqualTo(1);
        JsonNode elia = messageTo("n-elia@example.org");
        assertThat(elia.path("Subject").asString()).isEqualTo("Rootology: la tua domanda ha una risposta");
        assertThat(elia.path("Text").asString()).as("the link opens the space and garden of the question (#56)")
                .contains("/spaces/" + space + "/history");
        assertThat(elia.path("HTML").asString()).contains("/spaces/" + space + "/history");
    }

    @Test
    void anAskerWhoLeftTheSpaceIsNotNotified() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-nina", "n-otto", "n-pia");
        notifications.save("n-otto", new NotificationPreferences("n-otto@example.org", true, true, true, true, null, null));

        waiting("p-9", space, "n-otto", "Domanda?");
        notifications.markRead("n-otto", null);
        spaces.removeMember(space, "n-otto", "n-otto"); // he leaves while his question waits

        events.publishEvent(new EscalationResolved("p-9", space, 0, "n-otto", "n-nina", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.of("n-otto", 10)).as("only the waiting one, now resolved").singleElement()
                .extracting(NotificationView::answered).isEqualTo(false);
        assertThat(notifications.digest()).isZero();
    }

    @Test
    void aPersonWhoAnswersTheirOwnQuestionGetsNothingNew() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-ivo", "n-lia", "n-noa");

        waiting("p-6", space, "n-ivo", "Domanda?");
        notifications.markRead("n-ivo", null);

        events.publishEvent(new EscalationResolved("p-6", space, 0, "n-ivo", "n-ivo", now));

        assertThat(notifications.of("n-ivo", 10)).singleElement().extracting(NotificationView::answered).isEqualTo(false);
        assertThat(notifications.unread("n-ivo")).isZero();
    }

    @Test
    void waitingQuestionsAreEmailedTogetherAfterTheGroupingWindowOutsideQuietHours() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-pia", "n-remo", "n-sara");
        notifications.save("n-pia", new NotificationPreferences("n-pia@example.org", true, true, true, true, null, null));
        notifications.save("n-remo", new NotificationPreferences("n-remo@example.org", true, true, true, true, LocalTime.of(9, 0),
                LocalTime.of(12, 0)));

        waiting("p-2", space, "n-remo", "Prima domanda?");
        waiting("p-3", space, "n-remo", "Seconda domanda?");
        assertThat(notifications.digest()).as("not yet: within the grouping window").isZero();

        now = now.plus(Duration.ofMinutes(3)); // 11:03 in Italy: remo is in his quiet hours
        assertThat(notifications.digest()).isEqualTo(1);
        JsonNode pia = messagesTo("n-pia@example.org");
        assertThat(pia.path("messages_count").asInt()).isEqualTo(1);
        assertThat(pia.path("messages").get(0).path("Subject").asString())
                .isEqualTo("Rootology: 2 domande aspettano una risposta");

        now = Instant.parse("2026-11-04T11:30:00Z"); // 12:30 in Italy
        assertThat(notifications.digest()).as("remo, after his quiet hours").isEqualTo(1);
        assertThat(notifications.digest()).as("never twice").isZero();
        messagesTo("n-remo@example.org");
    }

    @Test
    void theAnswererIsNeverEmailed() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-teo", "n-ugo", "n-vera");
        notifications.save("n-teo", new NotificationPreferences("n-teo@example.org", true, true, true, true, null, null));
        notifications.rememberEmail("n-ugo", "N-Ugo@Example.org");
        notifications.rememberEmail("n-ugo", "other@example.org");

        waiting("p-4", space, "n-ugo", "Domanda?");
        events.publishEvent(new EscalationResolved("p-4", space, 0, "n-ugo", "n-teo", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).as("ugo asked, teo only answered").isEqualTo(1);
        assertThat(notifications.preferences("n-ugo").email()).as("the first remembered address stays")
                .isEqualTo("n-ugo@example.org");
        assertThatThrownBy(() -> notifications.save("n-teo", new NotificationPreferences("nope", true, true, true, true, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAskersEmailPreferencesAndQuietHoursAreRespected() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-ada", "n-bea", "n-carla");
        notifications.save("n-ada", new NotificationPreferences("n-ada@example.org", false, true, true, true, null, null));

        waiting("p-7", space, "n-ada", "Domanda?");
        events.publishEvent(new EscalationResolved("p-7", space, 0, "n-ada", "n-bea", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).as("ada turned answer emails off").isZero();

        now = Instant.parse("2026-11-04T10:00:00Z");
        long quiet = space("n-eva", "n-fio", "n-gina");
        notifications.save("n-eva", new NotificationPreferences("n-eva@example.org", true, true, true, true, LocalTime.of(10, 0),
                LocalTime.of(12, 0)));

        waiting("p-8", quiet, "n-eva", "Domanda?");
        events.publishEvent(new EscalationResolved("p-8", quiet, 0, "n-eva", "n-fio", now));
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).as("eva is in her quiet hours").isZero();

        now = now.plus(Duration.ofHours(2)); // 13:03 in Italy: the email waits for the end of the quiet hours
        assertThat(notifications.digest()).isEqualTo(1);
        messagesTo("n-eva@example.org");
    }

    @Test
    void eachWeatherAlertOfOneEmailLinksToItsOwnGarden() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-lino", "n-mara", "n-nia");
        notifications.save("n-lino", new NotificationPreferences("n-lino@example.org", true, true, true, true, null, null));
        long balcony = gardens.saveGarden(space, "n-lino", null, "Balcony", null, null, null).id();
        long allotment = gardens.saveGarden(space, "n-lino", null, "Allotment", null, null, null).id();

        notifications.weatherAlert(space, balcony, "frost:" + balcony, "Rischio gelo", "Minima prevista -2 °C");
        notifications.weatherAlert(space, allotment, "blight:" + allotment, "Rischio peronospora", "Periodo caldo e umido");
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).isEqualTo(1);
        JsonNode mail = messageTo("n-lino@example.org");
        for (String part : new String[]{"Text", "HTML"}) {
            String body = mail.path(part).asString();
            assertThat(body).as(part + ": each alert names and links its own garden")
                    .contains("/spaces/" + space + "/gardens/" + balcony, "/spaces/" + space + "/gardens/" + allotment)
                    .contains("Balcony", "Allotment");
        }
    }

    @Test
    void aWeatherAlertWithoutAGardenLinksToTheGardensOverview() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-olga", "n-pino", "n-qara");
        notifications.save("n-olga", new NotificationPreferences("n-olga@example.org", true, true, true, true, null, null));

        // Stored before #56 in a space with several gardens, or of a deleted garden: no garden (#56).
        notifications.weatherAlert(space, 0, "frost:none", "Rischio gelo", "Minima prevista -2 °C");
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).isEqualTo(1);
        JsonNode mail = messageTo("n-olga@example.org");
        for (String part : new String[]{"Text", "HTML"}) {
            assertThat(mail.path(part).asString()).as(part + ": the overview, where the gardens are")
                    .contains("/spaces/" + space + "/gardens");
        }
    }

    /** The email says it in Italian like the rest of its text, and never the decision's reason (#94). */
    @Test
    void theDigestWordsAWaitingQuestionInItalian() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-vito", "n-wanda", "n-xeno");
        notifications.save("n-vito", new NotificationPreferences("n-vito@example.org", true, true, true, true, null, null));

        waiting("p-10", space, "n-wanda", "Quali sono le compagne della lavanda?");
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).isEqualTo(1);
        JsonNode mail = messageTo("n-vito@example.org");
        for (String part : new String[]{"Text", "HTML"}) {
            assertThat(mail.path(part).asString()).as(part)
                    .contains("wanda ha chiesto: Quali sono le compagne della lavanda?")
                    .doesNotContain("asked:", "lavanda is not in the dataset");
        }
    }

    /** A row stored before #94 keeps the sentence it was stored with: nothing parses it (#94). */
    @Test
    void aWaitingQuestionStoredBeforeTheChangeIsEmailedAsItWas() throws Exception {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("n-yara", "n-zoe", "n-aldo");
        notifications.save("n-yara", new NotificationPreferences("n-yara@example.org", true, true, true, true, null, null));

        jdbc.sql("INSERT INTO notification (recipient, space_id, kind, title, body, created_at) "
                        + "VALUES (?, ?, 'ESCALATION_WAITING', 'A question waits for an answer', ?, ?)")
                .params("n-yara", space, "zoe asked: Domanda vecchia? (lavanda is not in the dataset)",
                        Timestamp.from(now)).update();
        now = now.plus(Duration.ofMinutes(3));

        assertThat(notifications.digest()).isEqualTo(1);
        JsonNode mail = messageTo("n-yara@example.org");
        assertThat(mail.path("Text").asString()).contains("zoe asked: Domanda vecchia? (lavanda is not in the dataset)");
    }

    /** A linked chat, as the /start of a good code leaves it. */
    private void linked(String login, long chatId) {
        jdbc.sql("insert into telegram_link (login, chat_id, linked_at) values (?, ?, now()) "
                + "on conflict (login) do update set chat_id = excluded.chat_id, failed_at = null")
                .params(login, chatId).update();
    }

    @Test
    void aLinkedPersonGetsOneMessageWithThePlaceAndTheLinkAndNeverTheTexts() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("tgo", "tgm", "tgg");
        notifications.save("tgo", new NotificationPreferences(null, true, true, true, true, null, null));
        long garden = gardens.saveGarden(space, "tgo", null, "Balcony", null, null, null).id();
        linked("tgo", 700_000_001L);
        when(telegram.send(anyLong(), anyString())).thenReturn(TelegramClient.Delivery.SENT);

        waiting("tg-1", space, "tgm", "Quali sono le compagne della lavanda?");
        notifications.weatherAlert(space, garden, "frost:" + garden, "Rischio gelo", "Minima prevista -2 °C");

        assertThat(notifications.telegramDigest()).isEqualTo(1);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegram).send(eq(700_000_001L), text.capture());
        assertThat(text.getValue())
                .as("a title, the place and the link")
                .contains("una domanda aspetta una risposta", "Rischio gelo", "Balcony",
                        "/spaces/" + space + "/escalations", "/spaces/" + space + "/gardens/" + garden)
                .as("never the question, the alert body or the plot")
                .doesNotContain("lavanda", "-2 °C");
        assertThat(notifications.telegramDigest()).as("never twice").isZero();
    }

    @Test
    void telegramRespectsTheQuietHoursAndTheKindsThePersonChose() {
        now = Instant.parse("2026-11-04T10:00:00Z"); // 11:00 in Italy
        long space = space("tga", "tgb", "tgc");
        notifications.save("tga", new NotificationPreferences(null, true, true, true, false,
                LocalTime.of(10, 0), LocalTime.of(12, 0)));
        long garden = gardens.saveGarden(space, "tga", null, "Balcony", null, null, null).id();
        linked("tga", 700_000_002L);
        when(telegram.send(anyLong(), anyString())).thenReturn(TelegramClient.Delivery.SENT);

        waiting("tg-2", space, "tgb", "Domanda?");
        notifications.weatherAlert(space, garden, "blight:" + garden, "Rischio peronospora", "Periodo caldo");

        assertThat(notifications.telegramDigest()).as("quiet hours, for Telegram too").isZero();
        verify(telegram, never()).send(anyLong(), anyString());

        now = Instant.parse("2026-11-04T11:30:00Z"); // 12:30 in Italy
        assertThat(notifications.telegramDigest()).as("only the question: weather is off for the phone").isEqualTo(1);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegram).send(eq(700_000_002L), text.capture());
        assertThat(text.getValue()).contains("una domanda aspetta una risposta").doesNotContain("peronospora");
    }

    @Test
    void withoutALinkNothingIsSentAndAnOldQuestionNeverArrives() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("tgd", "tge", "tgf");

        waiting("tg-3", space, "tge", "Domanda?");
        assertThat(notifications.telegramDigest()).isZero();
        verify(telegram, never()).send(anyLong(), anyString());

        linked("tgd", 700_000_003L);
        assertThat(notifications.telegramDigest()).as("what was not delivered then is not delivered now").isZero();
    }

    @Test
    void aBlockedChatIsMarkedAndNeverUsedAgain() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("tgk", "tgl", "tgm2");
        linked("tgk", 700_000_004L);
        when(telegram.send(anyLong(), anyString())).thenReturn(TelegramClient.Delivery.CHAT_GONE);

        waiting("tg-4", space, "tgl", "Domanda?");
        assertThat(notifications.telegramDigest()).as("not delivered").isZero();
        assertThat(telegramLinks.of("tgk")).get()
                .satisfies(link -> assertThat(link.working()).as("the person is warned in the app").isFalse());

        notifications.telegramDigest();
        verify(telegram, times(1)).send(anyLong(), anyString());
    }

    @Test
    void aMessageRefusedForOneChatIsDroppedWithoutUnlinking() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("tgn", "tgp", "tgq");
        linked("tgn", 700_000_005L);
        when(telegram.send(anyLong(), anyString())).thenReturn(TelegramClient.Delivery.DROPPED);

        waiting("tg-5", space, "tgp", "Domanda?");
        assertThat(notifications.telegramDigest()).isZero();

        assertThat(telegramLinks.of("tgn")).get().satisfies(link -> assertThat(link.working()).isTrue());
        assertThat(notifications.telegramDigest()).as("one lost message is not retried").isZero();
    }

    @Test
    void anOutageKeepsTheNotificationsForTheNextTick() {
        now = Instant.parse("2026-11-04T10:00:00Z");
        long space = space("tgr", "tgs", "tgt");
        linked("tgr", 700_000_006L);
        when(telegram.send(anyLong(), anyString()))
                .thenReturn(TelegramClient.Delivery.UNAVAILABLE)
                .thenReturn(TelegramClient.Delivery.SENT);

        waiting("tg-6", space, "tgs", "Domanda?");
        assertThat(notifications.telegramDigest()).as("Telegram is down").isZero();
        assertThat(notifications.telegramDigest()).as("the next tick, the same notification").isEqualTo(1);
    }

    @Test
    void quietHoursMayCrossMidnight() {
        NotificationPreferences night = new NotificationPreferences(null, true, true, true, true, LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertThat(night.quietAt(LocalTime.of(23, 30))).isTrue();
        assertThat(night.quietAt(LocalTime.of(6, 59))).isTrue();
        assertThat(night.quietAt(LocalTime.of(7, 0))).isFalse();
        assertThat(NotificationPreferences.DEFAULT.quietAt(LocalTime.NOON)).isFalse();
    }
}
