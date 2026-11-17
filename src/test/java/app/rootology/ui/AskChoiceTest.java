package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.QuestionContext;
import app.rootology.pipeline.QuestionContext.Choice;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The chips of a question that needs a place or a garden (#107): one chip per option under the field, and a
 * tap runs the same question again with that choice ({@link GardenPipeline#continueWith}). The pipeline is a
 * stub here: what the answer is belongs to its own tests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AskChoiceTest {

    private static final Choice.Place TORINO = new Choice.Place("Torino", "Piemonte", "IT", "Italia",
            new BigDecimal("45.07"), new BigDecimal("7.69"), 239);
    private static final Choice.Place PALERMO = new Choice.Place("Palermo", "Sicilia", "IT", "Italia",
            new BigDecimal("38.12"), new BigDecimal("13.36"), 14);

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

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
    RunHistory runHistory;

    @Autowired
    ClimateCells cells;


    @Test
    void aCalendarQuestionOutsideAGardenOffersItsTownsOneChipEach() {
        String login = login("place-missing");
        long space = personalSpace(login);
        AskView view = ask(mockPicking(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO, PALERMO),
                List.of())), login, space);

        view.beforeEnter(asking(space, "Cosa posso seminare adesso?"));

        assertThat(view.choiceOpen()).isTrue();
        assertThat(view.choiceAsked()).as("the page's texts, without a session, are their keys")
                .contains("ask.choice.place")
                .doesNotContain("notfound", "ambiguous");
        assertThat(texts(view.chips())).containsExactly("Torino, Piemonte", "Palermo, Sicilia");
    }

    @Test
    void aPlaceThatWasNotFoundNamesItAndOffersTheTowns() {
        String login = login("place-not-found");
        long space = personalSpace(login);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.NOT_FOUND, List.of(TORINO), List.of()));
        when(stub.askKnowledgeBase(any(), any(), anyBoolean())).thenReturn(run(
                new Intent(QueryKind.SOWING_TIME, "tomato", null, "it", null, null, null, "Xyzville"), null,
                choice(Choice.Kind.PLACE, Choice.Why.NOT_FOUND, List.of(TORINO), List.of()), null));

        AskView view = ask(stub, login, space);
        view.beforeEnter(asking(space, "Quando semino il pomodoro a Xyzville?"));

        assertThat(view.choiceAsked()).contains("ask.choice.place.notfound");
        assertThat(texts(view.chips())).containsExactly("Torino, Piemonte");
    }

    @Test
    void aPlaceThatMatchesSeveralAsksWhichOneAndOffersTheCandidates() {
        String login = login("place-ambiguous");
        long space = personalSpace(login);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.AMBIGUOUS, List.of(TORINO, PALERMO),
                List.of()));
        when(stub.askKnowledgeBase(any(), any(), anyBoolean())).thenReturn(run(
                new Intent(QueryKind.SOWING_TIME, "tomato", null, "it", null, null, null, "Sant’Angelo"), null,
                choice(Choice.Kind.PLACE, Choice.Why.AMBIGUOUS, List.of(TORINO, PALERMO), List.of()), null));

        AskView view = ask(stub, login, space);
        view.beforeEnter(asking(space, "Quando semino il pomodoro a Sant’Angelo?"));

        assertThat(view.choiceAsked()).contains("ask.choice.place.ambiguous");
        assertThat(texts(view.chips())).containsExactly("Torino, Piemonte", "Palermo, Sicilia");
    }

    @Test
    void aTapOnAPlaceRunsTheSameQuestionWithIt() {
        String login = login("place-tap");
        long space = personalSpace(login);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO, PALERMO),
                List.of()));
        when(stub.continueWith(any(), any(), any(), any(Choice.Place.class)))
                .thenReturn(answered(new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), null));

        AskView view = ask(stub, login, space);
        view.beforeEnter(asking(space, "Cosa posso seminare adesso?"));
        view.chips().getFirst().click();

        verify(stub).continueWith(any(Question.class), eq(new Intent(QueryKind.SOWING_TIME, "tomato", null, "it",
                null, null, null)), any(RunContext.class), eq(TORINO));
        assertThat(view.choiceOpen()).as("the choice is done").isFalse();
        assertThat(view.asking()).isFalse();
    }

    @Test
    void aQuestionAboutAGardenOffersOneChipPerGardenAndContinuesInIt() {
        String login = login("garden-choice");
        long space = personalSpace(login);
        long first = garden(space, login, "Terrazzo grande");
        long second = garden(space, login, "Terrazzo piccolo");
        GardenPipeline stub = mockPicking(choice(Choice.Kind.GARDEN, Choice.Why.AMBIGUOUS, List.of(),
                List.of(new Choice.Garden(first, "Terrazzo grande"), new Choice.Garden(second, "Terrazzo piccolo"))));
        when(stub.continueWith(any(), any(), any(), anyLong()))
                .thenReturn(answered(new Intent(QueryKind.GROWING_NOW, null, null, "it"), null));

        AskView view = ask(stub, login, space);
        view.beforeEnter(asking(space, "Cosa cresce adesso?"));
        assertThat(view.choiceAsked()).contains("ask.choice.garden");
        assertThat(texts(view.chips())).containsExactly("Terrazzo grande", "Terrazzo piccolo");

        view.chips().get(1).click();

        verify(stub).continueWith(any(Question.class), any(Intent.class), any(RunContext.class), eq(second));
        assertThat(view.choiceOpen()).isFalse();
    }

    @Test
    void aSpaceWithoutAGardenOffersToCreateOne() {
        String login = login("no-garden");
        long space = personalSpace(login);
        AskView view = ask(mockPicking(choice(Choice.Kind.GARDEN, Choice.Why.MISSING, List.of(), List.of())), login,
                space);

        view.beforeEnter(asking(space, "Cosa cresce adesso?"));

        assertThat(texts(view.chips())).hasSize(1);
        assertThat(texts(view.chips()).getFirst()).contains("ask.choice.creategarden");
    }

    @Test
    void anotherPlaceIsSearchedWhenGeoNamesKnowsTheWantedPlace() {
        // The search is the garden dialog's (#30): a chosen result continues the question with that place.
        String login = login("another-place");
        long space = personalSpace(login);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(PALERMO), List.of()));
        when(stub.continueWith(any(), any(), any(), any(Choice.Place.class)))
                .thenReturn(answered(new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), null));
        GeoNames searching = mock(GeoNames.class);
        when(searching.enabled()).thenReturn(true);
        when(searching.search(any(), any())).thenReturn(List.of(new Place("Bologna", "Emilia-Romagna", "IT",
                "Italia", 44.49, 11.34, 390_000)));

        AskView view = ask(stub, searching, login, space);
        view.beforeEnter(asking(space, "Cosa posso seminare adesso?"));
        assertThat(texts(view.chips()).getLast()).as("the search of another place").contains("ask.choice.otherplace");

        view.chips().getLast().click();
        ComboBox<Place> search = find(view.getChildren().toList(), ComboBox.class);
        assertThat(search.isVisible()).as("the search opens on demand").isTrue();
        search.setValue(new Place("Bologna", "Emilia-Romagna", "IT", "Italia", 44.49, 11.34, 390_000));

        verify(stub).continueWith(any(Question.class), any(Intent.class), any(RunContext.class),
                eq(new Choice.Place("Bologna", "Emilia-Romagna", "IT", "Italia", BigDecimal.valueOf(44.49),
                        BigDecimal.valueOf(11.34), null)));
    }

    @Test
    void theGardenOfAPlaceCanBeCreatedUnderItsAnswer() {
        String login = login("create-here");
        long space = personalSpace(login);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO), List.of()));
        when(stub.askKnowledgeBase(any(), any(), anyBoolean())).thenReturn(answer(
                new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), new ContextSnapshot("Casa", null, null,
                        null, "NOVEMBER", ContextSnapshot.MonthFrom.DEFAULT, null, 80, 300,
                        ContextSnapshot.ClimateFrom.PLACE, "Torino"), TORINO));

        AskView view = ask(stub, login, space);
        view.beforeEnter(asking(space, "Quando semino l’aglio a Torino?"));

        assertThat(buttons(view.answersShown())).anyMatch(text -> text.contains("ask.createhere"));
    }

    @Test
    void anAnswerThatUsedNoPlaceOffersNoGardenHere() {
        String login = login("no-create-here");
        long space = personalSpace(login);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO), List.of()));
        when(stub.askKnowledgeBase(any(), any(), anyBoolean())).thenReturn(answer(
                new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), new ContextSnapshot("Casa", "Orto", null,
                        null, "NOVEMBER", ContextSnapshot.MonthFrom.DEFAULT, null, 80, 300,
                        ContextSnapshot.ClimateFrom.GARDEN, null), null));

        AskView view = ask(stub, login, space);
        view.beforeEnter(asking(space, "Quando semino l’aglio?"));

        assertThat(buttons(view.answersShown())).noneMatch(text -> text.contains("ask.createhere"));
    }

    @Test
    void aGuestIsOfferedNoChoiceBecauseAGuestAsksNothing() {
        String login = login("guest-choice");
        String host = login("guest-choice-host");
        long shared = personalSpace(host);
        spaces.signedIn(login);
        spaces.addMember(shared, host, login, app.rootology.spaces.Role.GUEST);
        GardenPipeline stub = mockPicking(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO), List.of()));

        AskView view = ask(stub, login, shared);

        assertThat(view.canAsk()).isFalse();
        assertThat(view.choiceOpen()).isFalse();
        assertThat(view.chips()).isEmpty();
    }

    // The pipeline as the page calls it: a stub that says what the question needs.

    private GardenPipeline mockPicking(Choice needed) {
        GardenPipeline stub = mock(GardenPipeline.class);
        when(stub.askKnowledgeBase(any(), any(), anyBoolean())).thenReturn(run(
                new Intent(QueryKind.SOWING_TIME, "tomato", null, "it"), null, needed, null));
        return stub;
    }

    private AskView ask(GardenPipeline pipeline, String login, long spaceId) {
        // No place search: whether a person may search another place is its own test (#107).
        GeoNames noSearch = mock(GeoNames.class);
        when(noSearch.enabled()).thenReturn(false);
        return ask(pipeline, noSearch, login, spaceId);
    }

    private AskView ask(GardenPipeline pipeline, GeoNames search, String login, long spaceId) {
        AskView view = new AskView(pipeline, sources, new CurrentUser(spaces, login), enrichment, usage, gardens,
                crops, knowledge, search, cells, runHistory);
        view.beforeEnter(spaceLevel(spaceId));
        return view;
    }

    private static Choice choice(Choice.Kind kind, Choice.Why why, List<Choice.Place> places, List<Choice.Garden> gardens) {
        return new Choice(kind, why, places, gardens);
    }

    private static GardenPipeline.Run run(Intent intent, PipelineAnswer answer, Choice needed,
                                          Choice.Place place) {
        return new GardenPipeline.Run(answer, null, intent, false, needed, "p-1", place);
    }

    /** An answered run whose context is this snapshot: no escalation, the answer on the page. */
    private static GardenPipeline.Run answer(Intent intent, ContextSnapshot context, Choice.Place place) {
        PipelineAnswer answer = new PipelineAnswer("ask", List.of("Aglio"), 0.8, false, false, null,
                "Seminare l’aglio.", List.of(), List.of(), context);
        return new GardenPipeline.Run(answer, null, intent, false, null, "p-9", place);
    }

    private static GardenPipeline.Run answered(Intent intent, Choice.Place place) {
        PipelineAnswer answer = new PipelineAnswer("ask", List.of("Aglio"), 0.8, false, false, null, "Seminare l’aglio.",
                List.of(), List.of(), null);
        return new GardenPipeline.Run(answer, null, intent, false, null, "p-9", place);
    }

    private static List<String> texts(List<Button> buttons) {
        return buttons.stream().map(Button::getText).toList();
    }

    /** Every button of a component and of its children, by text: the "create a garden here" link is deep (#107). */
    private static List<String> buttons(Component root) {
        List<String> texts = new java.util.ArrayList<>();
        java.util.Deque<Component> left = new java.util.ArrayDeque<>(List.of(root));
        while (!left.isEmpty()) {
            Component component = left.pop();
            if (component instanceof Button button) {
                texts.add(button.getText());
            }
            component.getChildren().forEach(left::push);
        }
        return texts;
    }

    /** The first component of a kind under this one, for the search field of "another place". */
    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(List<com.vaadin.flow.component.Component> children, Class<T> kind) {
        java.util.Deque<Component> left = new java.util.ArrayDeque<>(children);
        while (!left.isEmpty()) {
            Component component = left.pop();
            if (kind.isInstance(component)) {
                return (T) component;
            }
            component.getChildren().forEach(left::push);
        }
        return null;
    }

    private long personalSpace(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    private long garden(long spaceId, String login, String name) {
        GardenSummary garden = gardens.saveGarden(spaceId, login, null, name, null, null, null);
        return garden.id();
    }

    private static String login(String prefix) {
        return prefix + "-" + Long.toString(System.nanoTime(), 36);
    }

    private static BeforeEnterEvent asking(long spaceId, String question) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/ask",
                QueryParameters.of(AskView.QUESTION, question)));
        return event;
    }

    private static BeforeEnterEvent spaceLevel(long spaceId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/ask"));
        return event;
    }
}
