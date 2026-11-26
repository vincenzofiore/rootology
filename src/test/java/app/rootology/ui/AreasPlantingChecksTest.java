package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.ClimateWindows;
import app.rootology.facts.Crop;
import app.rootology.facts.CropCatalog;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.geocoding.GeoNames;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The planting dialog of #185: a crop chosen shows its category and the checks the rules give, each warning
 * with its sentence as tooltip and aria label, and nothing blocks the save.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AreasPlantingChecksTest {

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
    void aChosenCropWithAnAntagonistInTheAreaShowsTheWarningWithItsAriaLabel() {
        String login = "areas-checks-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long garden = gardens.saveGarden(space, login, null, "Orto", null, null, null).id();
        long area = gardens.savePlot(space, login, garden, null, "Aiuola", PlotKind.GROUND, Soil.LOAMY, null,
                null, DirectSun.FULL_SUN, null, null).id();
        gardens.plant(space, login, area, "potato", PlantingMethod.SOWN, LocalDate.now(DateText.ITALY), null);

        AreasView view = new AreasView(gardens, crops, proposals, geoNames, weather, climateCells,
                climateWindows, new CurrentUser(spaces, login), clock);
        view.beforeEnter(entering(space, garden));
        PlotSummary bed = gardens.plots(space, login, garden).getFirst();
        Dialog dialog = view.plantingDialogOf(bed);

        assertThat(all(dialog).filter(Component::isVisible)
                .filter(c -> c.hasClassName("rootology-planting-checks")).count())
                .as("no crop chosen yet: no checks").isZero();

        @SuppressWarnings("unchecked")
        ComboBox<Crop> crop = (ComboBox<Crop>) all(dialog).filter(ComboBox.class::isInstance)
                .filter(box -> String.valueOf(((ComboBox<?>) box).getLabel()).contains("garden.crop"))
                .findFirst().orElseThrow();
        crop.setValue(crops.byId("tomato").orElseThrow());

        Span warning = all(dialog).filter(Span.class::isInstance).map(Span.class::cast)
                .filter(span -> span.hasClassName("rootology-planting-check-warning"))
                .findFirst().orElseThrow();
        String sentence = String.valueOf(warning.getElement().getAttribute("aria-label"));
        assertThat(sentence).as("the warning names the antagonist").contains("garden.check.antagonist");
        assertThat(warning.getElement().getAttribute("title")).isEqualTo(sentence);
        assertThat(warning.getElement().getAttribute("role")).isEqualTo("img");
        assertThat(all(dialog).filter(Component::isVisible)
                .filter(c -> c.hasClassName("rootology-planting-category")).count())
                .as("the crop's category is there too").isEqualTo(1);
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    /** Every component under this one, the component itself first. */
    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(AreasPlantingChecksTest::all));
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