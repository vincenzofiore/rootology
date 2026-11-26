package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
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
import app.rootology.geocoding.GeoNames;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.spaces.SpaceService;
import app.rootology.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A past answer opened from the history or a notification is the same card as a fresh one (#110): its
 * warnings and its sources are there too, not only the sentence and the values.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PastAnswerTest {

    @Autowired SpaceService spaces;
    @Autowired GardenService gardens;
    @Autowired GardenPipeline pipeline;
    @Autowired RunHistory runHistory;
    @Autowired SourceCatalog sources;
    @Autowired KnowledgeEnrichment enrichment;
    @Autowired UsageService usage;
    @Autowired CropCatalog crops;
    @Autowired KnowledgeGraph knowledge;
    @Autowired ClimateCells cells;
    @Autowired GeoNames geoNames;
    @Autowired JdbcClient jdbc;

    @Test
    void aPastAnswerShowsItsNotesAndSources() {
        String login = "past-answer-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        String processId = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, answer, "
                        + "answered_by_human, answer_text, notes_json, sources, off_domain, finished_at) "
                        + "VALUES (?, ?, ?, 'ask', 'Cosa semino?', 0, 0, 0, false, 0, '{}'::text[], 0.9, false, "
                        + "'rule', '{tomato}'::text[], false, 'Oggi sì: pomodoro.', ?, '{agera5}'::text[], false, ?)")
                .params(processId, space, login, "[{\"kind\":\"FROST_UNKNOWN\",\"args\":[\"basil\"]}]",
                        Timestamp.from(Instant.now()))
                .update();
        AskView ask = new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, geoNames, cells, runHistory);

        ask.beforeEnter(opening(space, processId));

        String shown = text(ask);
        assertThat(shown).contains("Oggi sì: pomodoro.");
        assertThat(shown).as("the note of the answer").contains("note.FROST_UNKNOWN");
        assertThat(shown).as("the source behind it").containsAnyOf("source.agera5", "AgERA5");
    }

    @Test
    void aPastAnswerStoredWithoutSourcesSaysNoneWasRecorded() {
        String login = "past-nosource-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        String processId = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, answer, "
                        + "answered_by_human, answer_text, notes_json, sources, off_domain, finished_at) "
                        + "VALUES (?, ?, ?, 'ask', 'Cosa semino?', 0, 0, 0, false, 0, '{}'::text[], 0.9, false, "
                        + "'rule', '{tomato}'::text[], false, 'Oggi sì: pomodoro.', null, null, false, ?)")
                .params(processId, space, login, Timestamp.from(Instant.now()))
                .update();
        AskView ask = new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, geoNames, cells, runHistory);

        ask.beforeEnter(opening(space, processId));

        // A row stored before notes and sources were kept (#111): the fold says it was saved before them (#114).
        assertThat(text(ask)).contains("ask.sources", "ask.sources.before");
        // The answer is the first thing read (#114): the suggestions go after the stack (#119).
        assertThat(ask.suggestionsUnderAnswer()).isTrue();
        long id = jdbc.sql("SELECT id FROM pipeline_run WHERE process_id = ?").param(processId).query(Long.class).single();
        assertThat(ask.historyPanel().isOpen("past-" + id)).as("its history card says it is open (#119)").isTrue();

        // Closing it takes it off the page, and the history no longer marks it (#119).
        button(ask, "ask.close").click();
        assertThat(text(ask)).doesNotContain("Oggi sì: pomodoro.");
        assertThat(ask.historyPanel().isOpen("past-" + id)).isFalse();
    }

    @Test
    void aCalendarCardInTheHistoryShowsItsVerdictAndNoCheck() {
        String login = "past-verdict-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, answer, "
                        + "answered_by_human, answer_text, notes_json, sources, off_domain, finished_at) "
                        + "VALUES (?, ?, ?, 'ask', 'Posso piantare le fragole oggi?', 0, 0, 0, false, 0, '{}'::text[], "
                        + "0.7, false, 'rule', '{AUGUST,SEPTEMBER}'::text[], false, 'Sì, ad agosto.', null, null, false, ?)")
                .params(UUID.randomUUID().toString(), space, login,
                        Timestamp.from(Instant.parse("2026-11-15T10:00:00Z")))
                .update();
        AskView ask = new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, geoNames, cells, runHistory);

        ask.beforeEnter(opening(space, "none"));

        // #122: asked in November, the card says "not now" instead of a check beside the months.
        List<Component> nodes = all(ask.historyPanel()).toList();
        assertThat(nodes).anyMatch(c -> c.getClassNames().contains("rootology-history-card-verdict")
                && c.getClassNames().contains("rootology-verdict-later"));
        assertThat(text(ask.historyPanel())).contains("verdict.now.no").doesNotContain("agosto");
        assertThat(nodes).noneMatch(c -> "history.answered".equals(c.getElement().getAttribute("aria-label"))
                || "!{history.answered}!".equals(c.getElement().getAttribute("aria-label")));
    }

    @Test
    void aPastOffDomainOrEscalatedCardLeavesOutTheGardenOfThePageItIsShownIn() {
        // #123: the same rule as #121 for the two cards that are not answers.
        String login = "past-refused-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        long garden = gardens.saveGarden(space, login, null, "Orto sul tetto", null, null, null).id();
        String offDomain = insertRun(space, garden, login, "Cosa pensi del calcio?", "Orto sul tetto", true, false);
        String escalated = insertRun(space, garden, login, "Quando semino la rucola?", "Orto sul tetto", false, true);
        // The space level: a run whose garden is gone keeps the space and its context (#56).
        String goneGarden = insertRun(space, null, login, "Cosa pensi del calcio?", "Orto sul tetto", true, false);

        AskView inGarden = askView(login);
        inGarden.beforeEnter(opening(space, garden, offDomain));
        assertThat(contextLines(card(inGarden))).as("the page already names its garden").containsExactly("novembre");

        AskView otherInGarden = askView(login);
        otherInGarden.beforeEnter(opening(space, garden, escalated));
        assertThat(contextLines(card(otherInGarden))).as("the page already names its garden").containsExactly("novembre");
        assertThat(text(card(otherInGarden))).doesNotContain("Orto sul tetto");

        AskView spaceLevel = askView(login);
        spaceLevel.beforeEnter(opening(space, goneGarden));
        assertThat(contextLines(card(spaceLevel))).as("at the space level the garden is named")
                .containsExactly("Orto sul tetto · novembre");
    }

    @Test
    void aControlMethodAnswerCardSaysNoEnglishMethodAndKeepsTheCropValues() {
        // #183: a control method is a sentence the answer's text says, so its English label is no badge; the
        // other values stay, in the page's language.
        String login = "past-controls-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        insertAnsweredRun(space, login, "Cosa posso fare contro gli afidi?",
                "'{\"Encourage ladybirds; hoverflies; lacewings; parasitoid wasps and earwigs\","
                        + "\"Two-spot ladybird released as biological control under cover\"}'");
        insertAnsweredRun(space, login, "Quando semino il pomodoro?", "'{tomato}'");
        AskView ask = askView(login);

        ask.beforeEnter(opening(space, "none"));

        Component methods = historyCard(ask, "Cosa posso fare contro gli afidi?");
        assertThat(text(methods)).as("no control-method label in the card")
                .doesNotContain("Encourage ladybirds", "Two-spot ladybird");
        assertThat(all(methods)).as("no empty values row either")
                .noneMatch(c -> c.getClassNames().contains("rootology-history-card-values"));
        Component crops = historyCard(ask, "Quando semino il pomodoro?");
        assertThat(all(crops)).as("the values row of a crop answer stays")
                .anyMatch(c -> c.getClassNames().contains("rootology-history-card-values"));
        assertThat(text(crops)).as("the crop in the page's language").contains("Pomodoro")
                .doesNotContain("tomato");
    }

    /** The card of one question in the history (#183). */
    private static Component historyCard(AskView ask, String question) {
        return all(ask.historyPanel())
                .filter(c -> c.getClassNames().contains("rootology-history-card"))
                .filter(c -> text(c).contains(question))
                .findFirst().orElseThrow();
    }

    /** A finished answer as the pipeline stores it, with the answer's values as a SQL array literal (#183). */
    private void insertAnsweredRun(long space, String login, String question, String answer) {
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, answer, "
                        + "answered_by_human, answer_text, off_domain, finished_at) "
                        + "VALUES (?, ?, ?, 'ask', ?, 0, 0, 0, false, 0, '{}'::text[], 0.9, false, 'rule', "
                        + answer + "::text[], false, 'La risposta.', false, ?)")
                .params(UUID.randomUUID().toString(), space, login, question, Timestamp.from(Instant.now()))
                .update();
    }

    private AskView askView(String login) {
        return new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, geoNames, cells, runHistory);
    }

    /** A finished run as the pipeline stores it, with the garden's name in its stored context (#55). */
    private String insertRun(long space, Long garden, String login, String question, String gardenName,
                             boolean offDomain, boolean escalated) {
        String processId = UUID.randomUUID().toString();
        String context = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                new app.rootology.pipeline.ContextSnapshot("Casa", gardenName, null, null, "NOVEMBER",
                        app.rootology.pipeline.ContextSnapshot.MonthFrom.DEFAULT, null));
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, garden_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, answer, "
                        + "answered_by_human, answer_text, context_json, off_domain, finished_at) "
                        + "VALUES (?, ?, ?, ?, 'ask', ?, 0, 0, 0, false, 0, '{}'::text[], 0.4, ?, "
                        + "'confidence below threshold', '{}'::text[], false, null, ?, ?, ?)")
                .params(processId, space, garden, login, question, escalated, context, offDomain,
                        Timestamp.from(Instant.now()))
                .update();
        return processId;
    }

    /** The card of the answer on Ask's left. */
    private static AnswerCard card(AskView ask) {
        return all(ask.answersShown()).filter(AnswerCard.class::isInstance).map(AnswerCard.class::cast)
                .findFirst().orElseThrow();
    }

    /** The line above a card: its own spans, not the page's. */
    private static List<String> contextLines(AnswerCard card) {
        return card.getChildren().filter(com.vaadin.flow.component.html.Span.class::isInstance)
                .map(c -> ((com.vaadin.flow.component.html.Span) c).getText()).toList();
    }

    private static com.vaadin.flow.component.button.Button button(Component root, String label) {
        return all(root).filter(com.vaadin.flow.component.button.Button.class::isInstance)
                .map(com.vaadin.flow.component.button.Button.class::cast)
                .filter(b -> String.valueOf(b.getElement().getAttribute("aria-label")).contains(label))
                .findFirst().orElseThrow();
    }

    private static java.util.stream.Stream<Component> all(Component root) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(root),
                root.getChildren().flatMap(PastAnswerTest::all));
    }

    private static BeforeEnterEvent opening(long spaceId, String processId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/ask",
                QueryParameters.of(AskView.RUN, processId)));
        return event;
    }

    private static BeforeEnterEvent opening(long spaceId, long gardenId, String processId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/gardens/" + gardenId + "/ask",
                QueryParameters.of(AskView.RUN, processId)));
        return event;
    }

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
}
