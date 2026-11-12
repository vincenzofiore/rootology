package app.rootology.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The URL is the only source of the context (#56): two pages of the same session, one on each of two
 * gardens, ask in their own garden; a space or a garden the person cannot see is not found.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GardenContextTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SourceCatalog sources;

    @Autowired
    KnowledgeEnrichment enrichment;

    @Autowired
    UsageService usage;

    @Autowired
    Ungardened ungardened;

    @Autowired
    CropCatalog crops;

    @Autowired
    KnowledgeGraph knowledge;

    @Autowired
    ClimateCells cells;

    @Autowired
    app.rootology.weather.WeatherService weather;

    @Test
    void twoPagesOfOneSessionOnTwoGardensAskInTheirOwnGarden() {
        String login = "two-tabs-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long first = gardens.saveGarden(space, login, null, "Balcony", null, null, null).id();
        long second = gardens.saveGarden(space, login, null, "Allotment", null, null, null).id();
        CurrentUser session = new CurrentUser(spaces, login);

        AskView balcony = askView(session);
        balcony.beforeEnter(entering(space, first));
        AskView allotment = askView(session);
        allotment.beforeEnter(entering(space, second));

        assertThat(balcony.context()).isEqualTo(new RunContext(space, first, login));
        assertThat(allotment.context()).isEqualTo(new RunContext(space, second, login));
        assertThat(balcony.context()).as("the session keeps no garden of its own").isNotEqualTo(allotment.context());
    }

    @Test
    void theSpaceLevelRouteAsksWithoutAGarden() {
        String login = "space-level-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        CurrentUser session = new CurrentUser(spaces, login);

        AskView spaceLevel = askView(session);
        spaceLevel.beforeEnter(entering(space));

        assertThat(spaceLevel.context()).isEqualTo(new RunContext(space, 0, login));
    }

    @Test
    void aGardenOfAnotherSpaceIsNotFound() {
        String login = "not-found-garden-" + Long.toString(System.nanoTime(), 36);
        String other = "not-found-other-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        spaces.signedIn(other);
        long space = personalSpace(login);
        long strangerGarden = gardens.saveGarden(personalSpace(other), other, null, "Someone else's", null, null, null).id();
        CurrentUser session = new CurrentUser(spaces, login);

        BeforeEnterEvent event = entering(space, strangerGarden);
        askView(session).beforeEnter(event);

        verify(event).rerouteToError(NotFoundException.class);
    }

    @Test
    void aSpaceThePersonIsNotInIsNotFound() {
        String login = "not-found-space-" + Long.toString(System.nanoTime(), 36);
        String other = "not-found-space-other-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        spaces.signedIn(other);
        long strangerSpace = personalSpace(other);
        long strangerGarden = gardens.saveGarden(strangerSpace, other, null, "Theirs", null, null, null).id();
        CurrentUser session = new CurrentUser(spaces, login);

        BeforeEnterEvent event = entering(strangerSpace, strangerGarden);
        askView(session).beforeEnter(event);

        verify(event).rerouteToError(NotFoundException.class);
    }

    @Test
    void theRoleOfTheRoutesSpaceDecidesTheActionsWhicheverPageWasOpenedFirst() {
        String login = "mixed-" + Long.toString(System.nanoTime(), 36);
        String host = "mixed-host-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login); // owner of their personal space
        spaces.signedIn(host);
        long own = personalSpace(login);
        long shared = personalSpace(host);
        spaces.addMember(shared, host, login, app.rootology.spaces.Role.GUEST);
        CurrentUser session = new CurrentUser(spaces, login);

        // Opened guest-first: the session's space ends up being the shared one, then the own one.
        GardensView guestOverview = new GardensView(gardens, null, ungardened, session, cells, pipeline, weather, STYLE, "");
        guestOverview.beforeEnter(enteringSpace(shared));
        assertThat(guestOverview.showsGardenActions()).as("a guest changes nothing").isFalse();
        GardensView ownOverview = new GardensView(gardens, null, ungardened, session, cells, pipeline, weather, STYLE, "");
        ownOverview.beforeEnter(enteringSpace(own));
        assertThat(ownOverview.showsGardenActions()).as("the owner does").isTrue();
        assertThat(guestOverview.showsGardenActions()).as("the other page keeps its own answer").isFalse();

        // Same for the question field, opened owner-first this time.
        AskView ownAsk = askView(session);
        ownAsk.beforeEnter(enteringSpace(own));
        AskView guestAsk = askView(session);
        guestAsk.beforeEnter(enteringSpace(shared));
        assertThat(ownAsk.canAsk()).isTrue();
        assertThat(guestAsk.canAsk()).as("a guest asks nothing").isFalse();
    }

    @Test
    void theSuggestedQuestionsFollowTheRoleOfTheSpace() {
        String login = "suggested-" + Long.toString(System.nanoTime(), 36);
        String host = "suggested-host-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        spaces.signedIn(host);
        long own = personalSpace(login);
        long shared = personalSpace(host);
        spaces.addMember(shared, host, login, app.rootology.spaces.Role.GUEST);
        CurrentUser session = new CurrentUser(spaces, login);

        AskView ownAsk = askView(session);
        ownAsk.beforeEnter(enteringSpace(own));
        AskView guestAsk = askView(session);
        guestAsk.beforeEnter(enteringSpace(shared));

        assertThat(ownAsk.suggestionCount()).as("the month's general questions").isEqualTo(3);
        assertThat(guestAsk.suggestionCount()).as("a guest asks nothing, so nothing suggests it (#33)").isZero();
    }

    @Test
    void aSecondQuestionWhileOneRunsStartsNothing() {
        String login = "one-at-a-time-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long garden = gardens.saveGarden(space, login, null, "Balcony", null, null, null).id();
        CurrentUser session = new CurrentUser(spaces, login);
        Queued queued = new Queued();
        AskView view = new AskView(pipeline, sources, session, enrichment, usage, gardens, crops, knowledge, queued);

        view.beforeEnter(asking(space, garden, "Cosa posso seminare adesso?"));
        assertThat(queued.queued).isEqualTo(1);
        assertThat(view.asking()).isTrue();
        assertThat(view.typed()).as("the question moved into its card").isEmpty();
        // The button is disabled, so this is a reused link, the hand-off of the home page, or a chip.
        view.beforeEnter(asking(space, garden, "Cosa posso seminare adesso?"));

        assertThat(queued.queued).as("one question at a time").isEqualTo(1);
    }

    /** An executor that keeps the question: nothing runs, so the page stays as it is while it waits. */
    private static final class Queued implements Executor {
        private int queued;

        @Override
        public void execute(Runnable command) {
            queued++;
        }
    }

    /** The hand-off of the garden home (#31, #82): the question comes in the address. */
    private static BeforeEnterEvent asking(long spaceId, long gardenId, String question) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))));
        when(event.getLocation()).thenReturn(new Location(
                "spaces/" + spaceId + "/gardens/" + gardenId + "/ask",
                QueryParameters.of(AskView.QUESTION, question)));
        return event;
    }

    private static BeforeEnterEvent enteringSpace(long spaceId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/gardens"));
        return event;
    }

    private AskView askView(CurrentUser session) {
        return new AskView(pipeline, sources, session, enrichment, usage, gardens, crops, knowledge);
    }

    /** The map's style: nothing is fetched here, the map is inspected as data (#66). */
    private static final String STYLE = "https://tiles.openfreemap.org/styles/liberty";

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    private static BeforeEnterEvent entering(long spaceId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/ask"));
        return event;
    }

    private static BeforeEnterEvent entering(long spaceId, long gardenId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))));
        when(event.getLocation()).thenReturn(new Location(
                "spaces/" + spaceId + "/gardens/" + gardenId + "/ask"));
        return event;
    }
}
