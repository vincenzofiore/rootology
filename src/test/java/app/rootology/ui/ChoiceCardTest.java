package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.facts.ClimateCells;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.QuestionContext.Choice;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The place or garden choice of a question (#107) on its own: one chip per option, what a tap continues, the
 * search of another place, and the garden a place's answer can create. The page that runs the continuation is
 * {@link AskView}, whose own tests cover it: here it is a recorder.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChoiceCardTest {

    private static final Choice.Place TORINO = new Choice.Place("Torino", "Piemonte", "IT", "Italia",
            new BigDecimal("45.07"), new BigDecimal("7.69"), 239);
    private static final Choice.Place PALERMO = new Choice.Place("Palermo", "Sicilia", "IT", "Italia",
            new BigDecimal("38.12"), new BigDecimal("13.36"), 14);

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    ClimateCells cells;

    /** What a tap handed to the page: the question again with that place, or in that garden. */
    private record Continued(String asked, Intent intent, Choice.Place place, long gardenId) {
    }

    private final List<Continued> continued = new ArrayList<>();
    private String login;
    private long space;
    private boolean busy;
    private boolean mayAsk;

    @BeforeEach
    void aPersonWithASpace() {
        login = "choice-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        space = spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
        busy = false;
        mayAsk = true;
    }

    @Test
    void aPlaceChoiceOffersOneChipPerPlaceAndNamesWhatItLookedFor() {
        ChoiceCard card = card(searching(false));
        card.show(run(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO, PALERMO), List.of())), "Domanda",
                space);

        assertThat(card.showing()).isTrue();
        assertThat(card.line()).as("the page's texts, without a session, are their keys")
                .contains("ask.choice.place")
                .doesNotContain("notfound", "ambiguous");
        assertThat(texts(card.chips())).containsExactly("Torino, Piemonte", "Palermo, Sicilia");
        assertThat(card.getChildren().noneMatch(ComboBox.class::isInstance))
                .as("no search when GeoNames is not configured").isTrue();
    }

    @Test
    void theLineNamesThePlaceTheQuestionCouldNotSettle() {
        ChoiceCard card = card(searching(false));

        card.show(run(new Intent(QueryKind.SOWING_TIME, "tomato", null, "it", null, null, null, "Xyzville"),
                choice(Choice.Kind.PLACE, Choice.Why.NOT_FOUND, List.of(TORINO), List.of())), "Domanda", space);
        assertThat(card.line()).contains("ask.choice.place.notfound");

        card.show(run(new Intent(QueryKind.SOWING_TIME, "tomato", null, "it", null, null, null, "Sant’Angelo"),
                choice(Choice.Kind.PLACE, Choice.Why.AMBIGUOUS, List.of(TORINO, PALERMO), List.of())), "Domanda",
                space);
        assertThat(card.line()).contains("ask.choice.place.ambiguous");
    }

    @Test
    void aTapOnAPlaceContinuesTheSameQuestionWithIt() {
        ChoiceCard card = card(searching(false));
        Intent intent = new Intent(QueryKind.SOWING_TIME, "tomato", null, "it");
        card.show(run(intent, choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO, PALERMO), List.of())),
                "Cosa posso seminare adesso?", space);

        card.chips().get(1).click();

        assertThat(continued).containsExactly(new Continued("Cosa posso seminare adesso?", intent, PALERMO, 0));
        assertThat(card.showing()).as("the choice goes with the question it was made for").isFalse();
        assertThat(card.chips()).as("nothing else can be tapped meanwhile").allMatch(c -> !c.isEnabled());
    }

    @Test
    void aGardenChoiceOffersOneChipPerGardenAndContinuesInIt() {
        long first = garden("Terrazzo grande");
        long second = garden("Terrazzo piccolo");
        ChoiceCard card = card(searching(false));
        Intent intent = new Intent(QueryKind.GROWING_NOW, null, null, "it");
        card.show(run(intent, choice(Choice.Kind.GARDEN, Choice.Why.AMBIGUOUS, List.of(),
                List.of(new Choice.Garden(first, "Terrazzo grande"), new Choice.Garden(second, "Terrazzo piccolo")))),
                "Cosa cresce adesso?", space);

        assertThat(card.line()).contains("ask.choice.garden");
        assertThat(texts(card.chips())).containsExactly("Terrazzo grande", "Terrazzo piccolo");

        card.chips().get(1).click();

        assertThat(continued).containsExactly(new Continued("Cosa cresce adesso?", intent, null, second));
        assertThat(card.showing()).isFalse();
    }

    @Test
    void aSpaceWithoutAGardenOffersToCreateOne() {
        ChoiceCard card = card(searching(false));
        card.show(run(choice(Choice.Kind.GARDEN, Choice.Why.MISSING, List.of(), List.of())), "Cosa cresce adesso?",
                space);

        assertThat(texts(card.chips())).hasSize(1);
        assertThat(texts(card.chips()).getFirst()).contains("ask.choice.creategarden");
    }

    @Test
    void anotherPlaceIsSearchedWhenGeoNamesKnowsTheWantedPlace() {
        // The search is the garden dialog's (#30): a chosen result continues the question with that place.
        GeoNames searching = searching(true);
        Place bologna = new Place("Bologna", "Emilia-Romagna", "IT", "Italia", 44.49, 11.34, 390_000);
        when(searching.search(any(), any())).thenReturn(List.of(bologna));
        ChoiceCard card = card(searching);
        card.show(run(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(PALERMO), List.of())), "Domanda", space);

        assertThat(texts(card.chips()).getLast()).as("the search of another place").contains("ask.choice.otherplace");
        card.chips().getLast().click();
        ComboBox<Place> search = find(card, ComboBox.class);
        assertThat(search.isVisible()).as("the search opens on demand").isTrue();

        search.setValue(bologna);

        assertThat(continued).containsExactly(new Continued("Domanda",
                new Intent(QueryKind.SOWING_TIME, "tomato", null, "it"),
                new Choice.Place("Bologna", "Emilia-Romagna", "IT", "Italia", BigDecimal.valueOf(44.49),
                        BigDecimal.valueOf(11.34), null), 0));
    }

    @Test
    void theGardenOfAPlaceCanBeCreatedUnderItsAnswer() {
        ChoiceCard card = card(searching(false));

        assertThat(card.gardenHere(answerOfPlace(TORINO), space)).as("a place with no garden: create one here")
                .isPresent();
    }

    @Test
    void anAnswerThatUsedNoPlaceOffersNoGardenHere() {
        ChoiceCard card = card(searching(false));

        assertThat(card.gardenHere(answerOfGarden(), space)).isEmpty();
    }

    @Test
    void aPlaceThatAlreadyHasAGardenOffersNone() {
        garden("Terrazzo", "Torino");
        ChoiceCard card = card(searching(false));

        assertThat(card.gardenHere(answerOfPlace(TORINO), space)).isEmpty();
    }

    @Test
    void aGuestIsOfferedNoSearchAndNoGardenToCreate() {
        mayAsk = false;
        ChoiceCard card = card(searching(true));
        card.show(run(choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(TORINO), List.of())), "Domanda", space);

        assertThat(texts(card.chips())).containsExactly("Torino, Piemonte");
        assertThat(card.getChildren().noneMatch(ComboBox.class::isInstance))
                .as("a guest searches nothing").isTrue();
        assertThat(card.gardenHere(answerOfPlace(TORINO), space)).as("and creates no garden")
                .isEmpty();
    }

    // The card as AskView builds it: the page runs the continuation, so here it records what it was given.

    private ChoiceCard card(GeoNames search) {
        return new ChoiceCard(gardens, search, cells, new CurrentUser(spaces, login), () -> busy, () -> mayAsk,
                (asked, intent, place, gardenId) -> continued.add(new Continued(asked, intent, place, gardenId)));
    }

    private static GeoNames searching(boolean enabled) {
        GeoNames search = mock(GeoNames.class);
        when(search.enabled()).thenReturn(enabled);
        return search;
    }

    private static Choice choice(Choice.Kind kind, Choice.Why why, List<Choice.Place> places,
                                 List<Choice.Garden> gardens) {
        return new Choice(kind, why, places, gardens);
    }

    private static GardenPipeline.Run run(Choice needed) {
        return run(new Intent(QueryKind.SOWING_TIME, "tomato", null, "it"), needed);
    }

    private static GardenPipeline.Run run(Intent intent, Choice needed) {
        return new GardenPipeline.Run(null, null, intent, false, needed, "p-1", null);
    }

    /** An answered run whose climate came from the place: the "create a garden here" case. */
    private static GardenPipeline.Run answerOfPlace(Choice.Place place) {
        return answer(new ContextSnapshot("Casa", null, null, null, "NOVEMBER", ContextSnapshot.MonthFrom.DEFAULT,
                null, 80, 300, ContextSnapshot.ClimateFrom.PLACE, place.name()), place);
    }

    private static GardenPipeline.Run answerOfGarden() {
        return answer(new ContextSnapshot("Casa", "Orto", null, null, "NOVEMBER",
                ContextSnapshot.MonthFrom.DEFAULT, null, 80, 300, ContextSnapshot.ClimateFrom.GARDEN, null), null);
    }

    private static GardenPipeline.Run answer(ContextSnapshot context, Choice.Place place) {
        PipelineAnswer answer = new PipelineAnswer("ask", List.of("Aglio"), 0.8, false, false, null,
                "Seminare l’aglio.", List.of(), List.of(), context);
        return new GardenPipeline.Run(answer, null, new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), false,
                null, "p-9", place);
    }

    private static List<String> texts(List<Button> buttons) {
        return buttons.stream().map(Button::getText).toList();
    }

    /** The first component of a kind under this one, for the search field of "another place". */
    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(Component root, Class<T> kind) {
        java.util.Deque<Component> left = new java.util.ArrayDeque<>(List.of(root));
        while (!left.isEmpty()) {
            Component component = left.pop();
            if (kind.isInstance(component)) {
                return (T) component;
            }
            component.getChildren().forEach(left::push);
        }
        return null;
    }

    private long garden(String name) {
        return garden(name, null);
    }

    private long garden(String name, String town) {
        GardenSummary garden = gardens.saveGarden(space, login, null, name, town, null, null);
        return garden.id();
    }
}
