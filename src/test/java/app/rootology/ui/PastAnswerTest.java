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

    private static BeforeEnterEvent opening(long spaceId, String processId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/ask",
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
