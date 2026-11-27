package app.rootology.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Ask says what the answer will assume before the question (#110): inside a garden its name, the last frost
 * of its site and the month; at the space level the month and that a place is asked for when needed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AskContextTest {

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

    @Test
    void insideAGardenItsNameItsLastFrostAndTheMonth() {
        String login = "ask-context-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        long garden = gardens.saveGarden(space, login, null, "Terrace", "Milano", new BigDecimal("45.46"),
                new BigDecimal("9.19"), "IT", 120).id();

        AskView ask = view(login);
        ask.beforeEnter(at(space, garden));

        assertThat(ask.contextLine()).startsWith("Terrace · ").contains("ask.context.lastfrost");
    }

    @Test
    void aGardenWithNoPositionHasItsNameAndTheMonth() {
        String login = "ask-context-nopos-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);
        long garden = gardens.saveGarden(space, login, null, "Balcony", null, null, null).id();

        AskView ask = view(login);
        ask.beforeEnter(at(space, garden));

        assertThat(ask.contextLine()).startsWith("Balcony · ").doesNotContain("lastfrost");
    }

    @Test
    void atTheSpaceLevelTheMonthAndAPlaceWhenNeeded() {
        String login = "ask-context-space-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = app.rootology.TestSpaces.personal(spaces, login);

        AskView ask = view(login);
        ask.beforeEnter(at(space, 0));

        assertThat(ask.contextLine()).contains("ask.context.nogarden");
    }

    private AskView view(String login) {
        return new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens, crops,
                knowledge, geoNames, cells, runHistory, java.time.Clock.systemUTC());
    }

    private static BeforeEnterEvent at(long spaceId, long gardenId) {
        Map<String, String> parameters = new HashMap<>(Map.of(Routed.SPACE, String.valueOf(spaceId)));
        if (gardenId > 0) {
            parameters.put(Routed.GARDEN, String.valueOf(gardenId));
        }
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(parameters));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/ask"));
        return event;
    }
}
