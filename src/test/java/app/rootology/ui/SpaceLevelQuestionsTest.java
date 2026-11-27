package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.history.RunHistory;
import app.rootology.notifications.NotificationsChanged;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.pipeline.HumanAnswer;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The space level is one context for the whole space (#109): waiting questions of every garden on one page,
 * answered in the garden they were asked in, and a history that holds every garden's questions with a garden
 * filter. Nothing of another space ever appears.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpaceLevelQuestionsTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    RunHistory runHistory;

    @Autowired
    SourceCatalog sources;

    @Autowired
    KnowledgeEnrichment enrichment;

    @Autowired
    UsageService usage;

    @Autowired
    CropCatalog crops;

    @Autowired
    KnowledgeGraph knowledge;

    @Autowired
    ClimateCells cells;

    @Autowired
    app.rootology.geocoding.GeoNames geoNames;

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbc;

    @Autowired
    NotificationBroadcaster broadcaster;

    @Test
    void theSpaceLevelListsEveryGardenAndAnswersInTheQuestionsOwnGarden() {
        String login = "space-waiting-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long garden = gardens.saveGarden(space, login, null, "Balcony", null, null, null).id();
        PendingReview withoutGarden = review(space, 0, login, "Che luna c'è stasera?", null);
        // Asked later, so its card is on top: the newest first (#132).
        PendingReview inGarden = review(space, garden, login, "Quando semino il basilico?", "Balcony");

        GardenPipeline mocked = mock(GardenPipeline.class);
        when(mocked.pendingInSpace(space, login)).thenReturn(List.of(withoutGarden, inGarden));
        when(mocked.respond(eq(inGarden.processId()), eq(space), eq(garden), any(HumanAnswer.class)))
                .thenReturn(new PipelineAnswer("ask", List.of("Basilico"), 0.8, true, true, login, "Fatto",
                        List.of(), List.of(), null));
        AskView view = new AskView(mocked, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, geoNames, cells, runHistory, java.time.Clock.systemUTC());

        // Questions with the waiting ones open (#132), where the old waiting questions page now leads.
        view.beforeEnter(spaceLevel(space, AskView.WAITING));

        List<Component> cards = waitingCards(view);
        assertThat(cards).as("every garden's waiting question and those without one").hasSize(2);
        assertThat(text(cards.get(0))).as("the garden's name is on its card").contains("Balcony");
        assertThat(text(cards.get(1))).as("no garden, said").contains("history.nogarden");

        // Answering on the page of the whole space answers in the garden the question was asked in.
        answerButton(cards.get(0)).click();
        verify(mocked).respond(eq(inGarden.processId()), eq(space), eq(garden), any(HumanAnswer.class));
    }

    @Test
    void anotherSpacesWaitingQuestionIsNeverListed() {
        String login = "space-waiting-mine-" + Long.toString(System.nanoTime(), 36);
        String stranger = "space-waiting-theirs-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        spaces.signedIn(stranger);
        long mine = personalSpace(login);
        long theirs = personalSpace(stranger);
        long myGarden = gardens.saveGarden(mine, login, null, "Mine", null, null, null).id();
        insertWaiting(mine, myGarden, login, "Quando semino la rucola?");
        insertWaiting(theirs, 0, stranger, "Quando semino la lattuga?");

        List<PendingReview> waiting = pipeline.pendingInSpace(mine, login);

        assertThat(waiting).hasSize(1);
        assertThat(waiting.get(0).question().text()).isEqualTo("Quando semino la rucola?");
        assertThatThrownBy(() -> pipeline.pendingInSpace(theirs, login))
                .as("a space the person is not in is not readable (#41)")
                .isInstanceOf(SpaceAccessException.class);
    }

    @Test
    void theSpaceLevelHistoryHoldsEveryGardenAndTheGardenFilterNarrowsIt() {
        String login = "space-history-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long balcony = gardens.saveGarden(space, login, null, "Balcony", null, null, null).id();
        long allotment = gardens.saveGarden(space, login, null, "Allotment", null, null, null).id();
        insertRun(space, balcony, login, "Cosa cresce sul balcone?", "Balcony");
        insertRun(space, allotment, login, "Cosa cresce nell'orto?", "Allotment");
        insertRun(space, 0, login, "Cosa seminare a novembre?", null);

        AskView ask = new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, geoNames, cells, runHistory, java.time.Clock.systemUTC());
        ask.beforeEnter(spaceLevel(space));

        HistoryPanel history = ask.historyPanel();
        assertThat(history.count()).as("every question of the space, in any garden and without one").isEqualTo(3);
        assertThat(history.shown()).isEqualTo(3);

        // The garden filter: one chip per garden in the list, plus "no garden".
        Div gardens = descendant(ask, "rootology-history-gardens");
        assertThat(gardens.getChildren().filter(Button.class::isInstance).map(c -> ((Button) c).getText()).toList())
                .containsExactly("Allotment", "Balcony", "!{history.nogarden}!");

        history.filter(new HistoryPanel.Filters(null, false, null, "Balcony"));
        assertThat(history.shown()).as("one garden at a time").isEqualTo(1);
        history.filter(new HistoryPanel.Filters(null, false, null, HistoryPanel.NO_GARDEN));
        assertThat(history.shown()).as("the questions without a garden").isEqualTo(1);
        history.filter(new HistoryPanel.Filters(null, false, null));
        assertThat(history.shown()).as("a chip tapped again shows every garden").isEqualTo(3);
    }

    @Test
    void theMenuOffersNoQuestionsWithoutAGardenAnyMore() throws Exception {
        Properties italian = messages("messages.properties");
        Properties english = messages("messages_en.properties");

        assertThat(italian).as("the space level has one waiting questions page (#109)")
                .doesNotContainKey("nav.ungardened");
        assertThat(english).doesNotContainKey("nav.ungardened");
        assertThat(italian).as("no refresh button to name").doesNotContainKey("escalations.refresh");
        assertThat(english).doesNotContainKey("escalations.refresh");
    }

    private static Properties messages(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = SpaceLevelQuestionsTest.class.getResourceAsStream("/i18n/" + name)) {
            properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return properties;
    }

    /** A waiting question as the pipeline stores it (#36), with the garden's name in its context (#55). */
    private void insertWaiting(long space, long garden, String login, String question) {
        tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        jdbc.sql("INSERT INTO pending_escalation (process_id, space_id, garden_id, started_by, question_id, "
                        + "question_text, decision_json, facts_json, noise_json, context_json, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(UUID.randomUUID().toString(), space, garden > 0 ? garden : null, login,
                        "ui-" + System.nanoTime(), question,
                        json.writeValueAsString(new app.rootology.decision.Decision(List.of(), 0.4, true,
                                "confidence below threshold")),
                        json.writeValueAsString(new app.rootology.decision.GraphFacts(List.of(), List.of())),
                        json.writeValueAsString(app.rootology.pipeline.NoiseProfile.NONE), null,
                        Timestamp.from(Instant.now()))
                .update();
    }

    /** A finished question of one garden (or of none), with the garden's name in its stored context (#55). */
    private void insertRun(long space, long garden, String login, String question, String gardenName) {
        tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();
        String context = gardenName == null ? null : json.writeValueAsString(new ContextSnapshot("Casa", gardenName,
                "CENTRO", ContextSnapshot.ZoneFrom.DEFAULT, "NOVEMBER", ContextSnapshot.MonthFrom.DEFAULT, null));
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, garden_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, answer, "
                        + "answered_by_human, answer_text, context_json, off_domain, finished_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, false, 0, '{}'::text[], 0.9, false, 'rule', "
                        + "'{}'::text[], false, 'Testo della risposta', ?, false, ?)")
                .params(UUID.randomUUID().toString(), space, garden > 0 ? garden : null, login,
                        "ui-" + System.nanoTime(), question, context, Timestamp.from(Instant.now()))
                .update();
    }

    /** A waiting question in memory, for the page that only reads and answers (#109). */
    private static PendingReview review(long space, long garden, String login, String question, String gardenName) {
        ContextSnapshot snapshot = gardenName == null ? null
                : new ContextSnapshot("Casa", gardenName, "CENTRO", ContextSnapshot.ZoneFrom.DEFAULT,
                "NOVEMBER", ContextSnapshot.MonthFrom.DEFAULT, null);
        return new PendingReview(UUID.randomUUID().toString(),
                new app.rootology.pipeline.RunContext(space, garden, login),
                new app.rootology.pipeline.Question("ask", question),
                new app.rootology.decision.Decision(List.of(), 0.4, true, "confidence below threshold"),
                snapshot, List.of(), Instant.now());
    }

    private static BeforeEnterEvent spaceLevel(long spaceId) {
        return spaceLevel(spaceId, null);
    }

    /** The space level's Questions, with a query parameter when one is given. */
    private static BeforeEnterEvent spaceLevel(long spaceId, String parameter) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(parameter == null ? new Location("spaces/" + spaceId + "/ask")
                : new Location("spaces/" + spaceId + "/ask", QueryParameters.simple(Map.of(parameter, ""))));
        return event;
    }

    /** The waiting cards of the page, newest first. */
    private static List<Component> waitingCards(Component root) {
        List<Component> found = new java.util.ArrayList<>();
        root.getChildren().forEach(child -> {
            if (child.getClassNames().contains("rootology-waiting-card")) {
                found.add(child);
            } else {
                found.addAll(waitingCards(child));
            }
        });
        return found;
    }

    /** The card's answer button: the one that hands the answer to the pipeline. */
    private static Button answerButton(Component card) {
        return buttons(card).stream().filter(b -> b.getText().contains("escalations.answer")).findFirst()
                .orElseThrow(() -> new AssertionError("no answer button on the card"));
    }

    private static List<Button> buttons(Component root) {
        List<Button> found = new java.util.ArrayList<>();
        root.getChildren().forEach(child -> {
            if (child instanceof Button button) {
                found.add(button);
            }
            found.addAll(buttons(child));
        });
        return found;
    }

    /** The text a component and its children show, as the page's language words it. */
    private static String text(Component root) {
        StringBuilder all = new StringBuilder();
        collect(root, all);
        return all.toString();
    }

    private static void collect(Component root, StringBuilder all) {
        if (root instanceof HasText hasText) {
            all.append(' ').append(hasText.getText());
        }
        root.getChildren().forEach(child -> collect(child, all));
    }

    /** The first element of this class anywhere under the page. */
    private static Div descendant(Component root, String className) {
        if (root instanceof Div div && div.getClassNames().contains(className)) {
            return div;
        }
        for (Component child : root.getChildren().toList()) {
            try {
                return descendant(child, className);
            } catch (AssertionError ignored) {
                // Not under this child: the next one.
            }
        }
        throw new AssertionError("no " + className + " on the page");
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }
}
