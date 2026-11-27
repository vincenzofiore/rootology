package app.rootology.ui;

import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.ClimateWindows;
import app.rootology.facts.CropCatalog;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.PlotKind;
import app.rootology.geocoding.GeoNames;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.weather.WeatherService;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The soil depth of a container area (#186) through the page: the field shows only for a pot, a planter and a
 * raised bed, the dialog saves it, the card shows it beside the sun, and open ground clears it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AreasDepthTest {

    @Autowired
    GardenService gardens;

    @Autowired
    SpaceService spaces;

    @Autowired
    CropCatalog crops;

    @Autowired
    app.rootology.garden.CropProposalService proposals;

    @Autowired
    GeoNames geoNames;

    @Autowired
    WeatherService weather;

    @Autowired
    ClimateCells climateCells;

    @Autowired
    ClimateWindows climateWindows;

    @Autowired
    java.time.Clock clock;

    @Test
    void theDialogAsksADepthOnlyForAContainerAndTheCardShowsIt() {
        String login = "areas-depth-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long garden = gardens.saveGarden(space, login, null, "Balcone", null, null, null).id();
        AreasView view = view(login, space, garden);

        Dialog dialog = view.areaDialogOf(null);
        assertThat(dialog).as("a garden is open, so the dialog is built").isNotNull();
        IntegerField depth = field(dialog, IntegerField.class, "area.depth.label");
        assertThat(depth.isVisible()).as("open ground by default: no depth to declare").isFalse();
        assertThat(depth.getMin()).isEqualTo(5);
        assertThat(depth.getMax()).isEqualTo(200);

        field(dialog, TextField.class, "garden.name").setValue("Vaso sul balcone");
        kind(dialog).setValue(PlotKind.POT);
        assertThat(depth.isVisible()).as("a pot has a soil depth").isTrue();
        depth.setValue(30);
        save(dialog).click();

        PlotSummary area = gardens.plots(space, login, garden).get(0);
        assertThat(area.depthCm()).isEqualTo(30);
        assertThat(texts(card(view))).as("the depth on the card, as a fact of the area")
                .anyMatch(text -> text.contains("area.depth"));

        // Editing it as open ground hides the field, clears it, and the area keeps no depth.
        Dialog edit = view.areaDialogOf(area);
        IntegerField editing = field(edit, IntegerField.class, "area.depth.label");
        assertThat(editing.getValue()).as("the saved depth is in the field").isEqualTo(30);
        kind(edit).setValue(PlotKind.GROUND);
        assertThat(editing.isVisible()).isFalse();
        assertThat(editing.getValue()).as("and goes with the field").isNull();
        save(edit).click();

        assertThat(gardens.plots(space, login, garden).get(0).depthCm()).isNull();
        assertThat(texts(card(view))).as("nothing left to show")
                .noneMatch(text -> text.contains("area.depth"));
    }

    private AreasView view(String login, long space, long garden) {
        AreasView view = new AreasView(gardens, crops, proposals, geoNames, weather, climateCells, climateWindows,
                new CurrentUser(spaces, login), clock);
        view.beforeEnter(entering(space, garden));
        return view;
    }

    /** Every component under this one, the component itself first. */
    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(AreasDepthTest::all));
    }

    private static <T extends Component> T field(Dialog dialog, Class<T> type, String label) {
        return all(dialog).filter(type::isInstance).map(type::cast)
                .filter(field -> field instanceof TextField text ? labelled(text.getLabel(), label)
                        : field instanceof IntegerField number && labelled(number.getLabel(), label))
                .findFirst().orElseThrow(() -> new AssertionError("No field " + label + " in the dialog"));
    }

    /** Without a Vaadin session a label is the key itself, wrapped ("!{garden.kind}!"): both contain it. */
    private static boolean labelled(String shown, String key) {
        return shown != null && shown.contains(key);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<PlotKind> kind(Dialog dialog) {
        return (ComboBox<PlotKind>) all(dialog).filter(ComboBox.class::isInstance)
                .filter(box -> labelled(((ComboBox<?>) box).getLabel(), "garden.kind"))
                .findFirst().orElseThrow();
    }

    /** The footer's buttons are slotted into the dialog's element, not its children (#89). */
    private static Button save(Dialog dialog) {
        return elements(dialog.getElement())
                .map(element -> element.getComponent().orElse(null))
                .filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getText() != null && button.getText().contains("common.save"))
                .findFirst().orElseThrow();
    }

    private static Stream<com.vaadin.flow.dom.Element> elements(com.vaadin.flow.dom.Element root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(AreasDepthTest::elements));
    }

    /** The words of everything under a component: without a Vaadin session a label is the key itself. */
    private static java.util.List<String> texts(Component component) {
        return all(component).filter(Span.class::isInstance).map(Span.class::cast)
                .map(Span::getText).toList();
    }

    private static Div card(AreasView view) {
        Div cards = all(view).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.getClassNames().contains("rootology-area-cards")).findFirst().orElseThrow();
        return cards.getChildren().filter(Div.class::isInstance).map(Div.class::cast)
                .filter(div -> div.getClassNames().contains("rootology-area-card"))
                .findFirst().orElseThrow();
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    private static BeforeEnterEvent entering(long spaceId, long gardenId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))));
        when(event.getLocation()).thenReturn(new Location(
                "spaces/" + spaceId + "/gardens/" + gardenId + "/areas"));
        return event;
    }
}
