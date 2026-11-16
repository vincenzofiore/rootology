package app.rootology.ui;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenPlan;
import app.rootology.garden.GardenViews.PlanArea;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.geocoding.GeoNames;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The plan on the areas page (#90): with a plan, the plan and its boxes above the cards and the areas still to
 * place under it; without one, the invitation for a contributor and nothing for a guest; a change the element
 * reports is stored, and a guest's change is not.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AreasPlanViewTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    CropCatalog crops;

    @Autowired
    GeoNames geoNames;

    @Autowired
    WeatherService weather;

    @Autowired
    ClimateCells cells;

    @Test
    void withAPlanThePageDrawsItAndOffersTheAreasStillToPlace() {
        String login = login("plan-drawn");
        long space = personalSpace(login);
        long garden = garden(space, login, "Orto");
        long bed = area(space, login, garden, "Aiuola", PlotKind.RAISED_BED, null);
        long pot = area(space, login, garden, "Vaso", PlotKind.POT, null);
        gardens.resizePlan(space, login, garden, new BigDecimal("8"), new BigDecimal("4"));
        gardens.placeArea(space, login, bed, new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("2"), new BigDecimal("1"));

        AreasView view = view(login);
        view.beforeEnter(entering(space, garden));

        assertThat(view.planShown()).isTrue();
        assertThat(view.planInvited()).as("a drawn plan is not invited again").isFalse();
        assertThat(view.board().contributor()).isTrue();
        assertThat(boxes(view)).extracting(GardenPlanBoard.Box::id)
                .as("only the placed area is on the plan").containsExactly(String.valueOf(bed));
        assertThat(boxes(view).getFirst().width()).isEqualTo(2);
        assertThat(view.unplacedOnPage()).as("the pot is still to place").containsExactly(pot);

        String guest = login("plan-drawn-guest");
        spaces.addMember(space, login, guest, Role.GUEST);
        AreasView asGuest = view(guest);
        asGuest.beforeEnter(entering(space, garden));
        assertThat(asGuest.planShown()).as("a guest sees the plan").isTrue();
        assertThat(asGuest.board().contributor()).as("but moves nothing on it").isFalse();
        assertThat(asGuest.unplacedOnPage()).as("and is not offered an area to place").isEmpty();
    }

    @Test
    void withoutAPlanAContributorIsInvitedAndAGuestSeesNothing() {
        String login = login("plan-none");
        long space = personalSpace(login);
        long garden = garden(space, login, "Orto");
        area(space, login, garden, "Aiuola", PlotKind.GROUND, null);

        AreasView view = view(login);
        view.beforeEnter(entering(space, garden));
        assertThat(view.planInvited()).isTrue();
        assertThat(view.planShown()).isFalse();
        assertThat(view.board().isVisible()).isFalse();

        String guest = login("plan-none-guest");
        spaces.addMember(space, login, guest, Role.GUEST);
        AreasView asGuest = view(guest);
        asGuest.beforeEnter(entering(space, garden));
        assertThat(asGuest.planInvited()).as("a guest is not asked to draw the plan").isFalse();
        assertThat(asGuest.planShown()).isFalse();
    }

    @Test
    void aMoveReportedByThePlanIsStoredAndAGuestCannotMoveAnything() {
        String login = login("plan-move");
        long space = personalSpace(login);
        long garden = garden(space, login, "Orto");
        long bed = area(space, login, garden, "Aiuola", PlotKind.RAISED_BED, null);
        gardens.resizePlan(space, login, garden, new BigDecimal("8"), new BigDecimal("4"));
        gardens.placeArea(space, login, bed, new BigDecimal("0"), new BigDecimal("0"),
                new BigDecimal("2"), new BigDecimal("1"));

        AreasView view = view(login);
        view.beforeEnter(entering(space, garden));
        GardenPlanBoard board = view.board();

        ComponentUtil.fireEvent(board, new GardenPlanBoard.AreaMovedEvent(board, true,
                String.valueOf(bed), 3, 1, 1.5, 0.8));
        assertThat(area(gardens.plan(space, login, garden), bed))
                .as("the box is where the person let it go")
                .isEqualTo(new PlanArea(bed, "Aiuola", PlotKind.RAISED_BED,
                        new BigDecimal("3.00"), new BigDecimal("1.00"),
                        new BigDecimal("1.50"), new BigDecimal("0.80")));

        ComponentUtil.fireEvent(board, new GardenPlanBoard.AreaMovedEvent(board, true,
                String.valueOf(bed), 7.5, 0, 2, 1));
        assertThat(area(gardens.plan(space, login, garden), bed).x())
                .as("a box pushed past the plan's edge is refused").isEqualByComparingTo("3.00");

        ComponentUtil.fireEvent(board, new GardenPlanBoard.AreaMovedEvent(board, true,
                "not an area of this plan", 0, 0, 1, 1));
        assertThat(area(gardens.plan(space, login, garden), bed).x())
                .as("an area that is not on this plan is ignored").isEqualByComparingTo("3.00");

        String guest = login("plan-move-guest");
        spaces.addMember(space, login, guest, Role.GUEST);
        AreasView asGuest = view(guest);
        asGuest.beforeEnter(entering(space, garden));
        ComponentUtil.fireEvent(asGuest.board(), new GardenPlanBoard.AreaMovedEvent(asGuest.board(), true,
                String.valueOf(bed), 0, 0, 1, 1));
        assertThat(area(gardens.plan(space, login, garden), bed).x())
                .as("a guest moves nothing").isEqualByComparingTo("3.00");
    }

    @Test
    void anAreaChosenOnThePlanMarksItsCard() {
        String login = login("plan-select");
        long space = personalSpace(login);
        long garden = garden(space, login, "Orto");
        long bed = area(space, login, garden, "Aiuola", PlotKind.GROUND, new BigDecimal("4"));
        gardens.resizePlan(space, login, garden, new BigDecimal("8"), new BigDecimal("4"));
        gardens.placeArea(space, login, bed, new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("2"), new BigDecimal("2"));

        AreasView view = view(login);
        view.beforeEnter(entering(space, garden));

        ComponentUtil.fireEvent(view.board(), new GardenPlanBoard.AreaSelectedEvent(view.board(), true,
                String.valueOf(bed)));
        assertThat(view.selectedArea()).isEqualTo(bed);

        ComponentUtil.fireEvent(view.board(), new GardenPlanBoard.AreaSelectedEvent(view.board(), true, "9999"));
        assertThat(view.selectedArea()).as("an area that is not on the plan selects nothing").isEqualTo(bed);
    }

    /** The areas on the plan, read back from the element: what the browser receives is what is checked. */
    private static List<GardenPlanBoard.Box> boxes(AreasView view) {
        return view.board().getElement().getPropertyBean("boxes",
                new tools.jackson.core.type.TypeReference<List<GardenPlanBoard.Box>>() {
                });
    }

    private static PlanArea area(GardenPlan plan, long plotId) {
        return plan.areas().stream().filter(area -> area.plotId() == plotId).findFirst().orElseThrow();
    }

    private AreasView view(String login) {
        return new AreasView(gardens, crops, geoNames, weather, cells, new CurrentUser(spaces, login));
    }

    private long area(long spaceId, String login, long gardenId, String name, PlotKind kind, BigDecimal areaM2) {
        return gardens.savePlot(spaceId, login, gardenId, null, name, kind, Soil.UNKNOWN, areaM2).id();
    }

    private long garden(long spaceId, String login, String name) {
        return gardens.saveGarden(spaceId, login, null, name, "Vasto", null, null, null).id();
    }

    private String login(String prefix) {
        String login = prefix + "-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        return login;
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    private static BeforeEnterEvent entering(long spaceId, long gardenId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE,
                String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/gardens/" + gardenId + "/areas"));
        return event;
    }
}
