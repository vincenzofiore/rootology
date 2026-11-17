package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The hours of direct sun of an area (#89) through the page: the dialog saves them and the card shows them,
 * with the level of the month of the visit.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AreasSunHoursTest {

    @Autowired
    GardenService gardens;

    @Autowired
    SpaceService spaces;

    @Autowired
    CropCatalog crops;

    @Autowired
    GeoNames geoNames;

    @Autowired
    WeatherService weather;

    @Autowired
    ClimateCells climateCells;

    @Test
    void theDialogSavesTheHoursAndTheCardShowsThemBesideTheSun() {
        String login = "areas-sun-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        long space = personalSpace(login);
        long garden = gardens.saveGarden(space, login, null, "Balcone", null, null, null).id();
        AreasView view = new AreasView(gardens, crops, geoNames, weather, climateCells,
                new CurrentUser(spaces, login));
        view.beforeEnter(entering(space, garden));

        Dialog dialog = view.areaDialogOf(null);
        assertThat(dialog).as("a garden is open, so the dialog is built").isNotNull();
        field(dialog, TextField.class, "garden.name").setValue("Vasi sul balcone");
        Div toggle = all(dialog).filter(c -> c.hasClassName("rootology-sun-toggle")).map(Div.class::cast)
                .findFirst().orElseThrow();
        field(dialog, IntegerField.class, "area.sun.summer.label").setValue(7);
        assertThat(toggle.hasClassName("rootology-sun-derived"))
                .as("one season only: the level still counts for the other one").isFalse();
        field(dialog, IntegerField.class, "area.sun.winter.label").setValue(2);
        assertThat(toggle.hasClassName("rootology-sun-derived"))
                .as("both seasons: the level comes from the hours").isTrue();
        assertThat(toggle.getChildren().map(Button.class::cast)).as("and is not chosen by hand")
                .noneMatch(Button::isEnabled);
        assertThat(toggle.getChildren().filter(c -> c.hasClassName("rootology-sun-chosen")).count())
                .as("the level of this month's hours is shown").isEqualTo(1);
        save(dialog).click();

        PlotSummary area = gardens.plots(space, login, garden).get(0);
        assertThat(area.sunHoursSummer()).isEqualTo(7);
        assertThat(area.sunHoursWinter()).isEqualTo(2);
        assertThat(area.sunDeclaredBy()).as("who declared the hours").isEqualTo(login);
        assertThat(area.sunDeclaredAt()).isNotNull();

        // The card shows the hours next to the sun, and the tooltip says who declared them.
        Div card = card(view);
        Div sun = all(card).filter(Div.class::isInstance).map(Div.class::cast)
                .filter(fact -> fact.getClassNames().contains("rootology-fact"))
                .filter(fact -> fact.getChildren().anyMatch(c -> c instanceof Icon drawn
                        && String.valueOf(drawn.getElement().getAttribute("aria-label"))
                        .contains("area.sun.tooltip")))
                .findFirst().orElseThrow();
        Icon icon = (Icon) sun.getChildren().filter(Icon.class::isInstance).findFirst().orElseThrow();
        assertThat(icon.getElement().getAttribute("icon"))
                .as("the level of the season of the visit")
                .isEqualTo(glyph(area.directSunIn(LocalDate.now(DateText.ITALY).getMonth())));
        assertThat(String.valueOf(icon.getElement().getAttribute("aria-label")))
                .as("the same sentence as the tooltip")
                .isEqualTo(icon.getTooltip().getText())
                .contains("area.sun.tooltip", "area.sun.declared");
        List<com.vaadin.flow.component.html.Span> seasons = sun.getChildren()
                .filter(c -> c.hasClassName("rootology-season-hours"))
                .map(com.vaadin.flow.component.html.Span.class::cast).toList();
        assertThat(seasons.stream().map(part -> part.getChildren()
                        .filter(c -> c.hasClassName("rootology-season-value"))
                        .map(c -> c.getElement().getText()).findFirst().orElseThrow()).toList())
                .as("the hours, summer then winter").containsExactly("7 h", "2 h");
        assertThat(seasons.stream().map(part -> part.getChildren()
                        .filter(com.vaadin.flow.component.icon.SvgIcon.class::isInstance)
                        .map(c -> c.getElement().getAttribute("src")).findFirst().orElseThrow()).toList())
                .as("a sun for summer, a snowflake for winter")
                .containsExactly("icons/seasons/summer.svg", "icons/seasons/winter.svg");
        assertThat(seasons).as("the sun icon already says it to a screen reader")
                .allMatch(part -> "true".equals(part.getElement().getAttribute("aria-hidden")));
    }

    /** "vaadin:sun-o" and the two others: the drawing of a level, as the card picks it. */
    private static String glyph(DirectSun level) {
        return Map.of(DirectSun.FULL_SUN, "vaadin:sun-o", DirectSun.PART_SHADE, "vaadin:cloud-o",
                DirectSun.SHADE, "vaadin:umbrella").get(level);
    }

    /** Every component under this one, the component itself first. */
    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(AreasSunHoursTest::all));
    }

    private static <T extends Component> T field(Dialog dialog, Class<T> type, String label) {
        return all(dialog).filter(type::isInstance).map(type::cast)
                .filter(field -> field instanceof TextField text ? labelled(text.getLabel(), label)
                        : field instanceof IntegerField number && labelled(number.getLabel(), label))
                .findFirst().orElseThrow(() -> new AssertionError("No field " + label + " in the dialog"));
    }

    /** Without a Vaadin session a label is the key itself, wrapped ("!{garden.name}!"): both contain it. */
    private static boolean labelled(String shown, String key) {
        return shown != null && shown.contains(key);
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
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(AreasSunHoursTest::elements));
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
