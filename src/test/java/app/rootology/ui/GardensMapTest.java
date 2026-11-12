package app.rootology.ui;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.ClimateCells;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gardens overview with the map (#66): the markers are the gardens of this page's space, gardens on the
 * same rounded point share one, a garden with no coordinates stays in the table alone, and only the gardens
 * the map was given may be selected or opened through it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GardensMapTest {

    /** The style the page hands the map; nothing is fetched in these tests. */
    private static final String STYLE = "https://tiles.openfreemap.org/styles/liberty";

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    ClimateCells cells;

    @Autowired
    GeoNames geoNames;

    @Autowired
    Ungardened ungardened;

    @Autowired
    app.rootology.weather.WeatherService weather;

    @Autowired
    JdbcClient jdbc;

    @Test
    void gardensOnTheSameRoundedPointShareOneMarkerAndTheOthersKeepTheirOwn() {
        String login = login("map-groups");
        long space = personalSpace(login);
        long balcony = garden(space, login, "Balcony", "45.07", "9.19");
        long terrace = garden(space, login, "Terrace", "45.07", "9.19");
        long allotment = garden(space, login, "Allotment", "42.11", "14.71");
        long nowhere = garden(space, login, "Nowhere", null, null);

        GardensView view = view(login);
        view.beforeEnter(entering(space));

        List<GardenMap.Group> groups = groups(view);
        assertThat(groups).as("two points, the garden with no coordinates has no marker").hasSize(2);

        GardenMap.Group shared = groups.stream().filter(g -> g.gardens().size() == 2).findFirst().orElseThrow();
        assertThat(shared.gardens()).extracting(GardenMap.Pin::id)
                .containsExactly(String.valueOf(balcony), String.valueOf(terrace));
        assertThat(shared.latitude()).isEqualTo(45.07);
        assertThat(shared.longitude()).isEqualTo(9.19);
        assertThat(shared.title()).as("the marker says how many gardens are there").isNotEmpty();
        // The coordinates are in the popup, not in the table (texts are keys here: these tests have no service).
        assertThat(shared.position()).contains("gardens.map.position");

        assertThat(groups).flatExtracting(GardenMap.Group::gardens).extracting(GardenMap.Pin::id)
                .contains(String.valueOf(allotment)).doesNotContain(String.valueOf(nowhere));

        assertThat(view.table().getListDataView().getItems().map(GardenSummary::id))
                .as("the table keeps every garden, the one without coordinates too")
                .containsExactlyInAnyOrder(balcony, terrace, allotment, nowhere);
    }

    @Test
    void aPopupCarriesWhatGrowsAndWaits() {
        String login = login("map-popup");
        long space = personalSpace(login);
        garden(space, login, "Balcony", "45.07", "9.19");

        GardensView view = view(login);
        view.beforeEnter(entering(space));

        GardenMap.Pin pin = groups(view).getFirst().gardens().getFirst();
        assertThat(pin.name()).isEqualTo("Balcony");
        assertThat(pin.town()).isEqualTo("Milan");
        assertThat(pin.crops()).isZero();
        assertThat(pin.waiting()).isZero();
    }

    @Test
    void aPopupShowsTheWeatherNowOnlyFromAStoredForecast() {
        String login = login("map-weather");
        long space = personalSpace(login);
        garden(space, login, "Balcony", "46.31", "11.11");
        garden(space, login, "Shed", "46.41", "11.21");

        // A copy the 7:00 alerts job (or a question) left behind, with the hour of now (#67).
        java.time.Instant now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        jdbc.sql("insert into weather_location (latitude, longitude, fetched_at, expires_at, last_modified) "
                        + "values (?, ?, ?, ?, null) on conflict (latitude, longitude) do update set "
                        + "fetched_at = excluded.fetched_at, expires_at = excluded.expires_at")
                .params(new BigDecimal("46.31"), new BigDecimal("11.11"), java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now.plusSeconds(1800))).update();
        jdbc.sql("insert into weather_hour (latitude, longitude, valid_at, air_temperature, relative_humidity, "
                        + "precipitation_mm, step_hours, symbol_code) values (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (latitude, longitude, valid_at) do update set "
                        + "air_temperature = excluded.air_temperature, symbol_code = excluded.symbol_code")
                .params(new BigDecimal("46.31"), new BigDecimal("11.11"), java.sql.Timestamp.from(now),
                        12.4, 70.0, 0.4, 1, "rain").update();

        GardensView view = view(login);
        view.beforeEnter(entering(space));

        List<GardenMap.Pin> pins = groups(view).stream().flatMap(group -> group.gardens().stream()).toList();
        GardenMap.Pin withWeather = pins.stream().filter(pin -> pin.name().equals("Balcony")).findFirst().orElseThrow();
        assertThat(withWeather.weatherSymbol()).isEqualTo("rain");
        assertThat(withWeather.weatherText())
                .as("the wording is a message pattern here: these tests have no UI").isNotNull();

        GardenMap.Pin without = pins.stream().filter(pin -> pin.name().equals("Shed")).findFirst().orElseThrow();
        assertThat(without.weatherSymbol()).as("no stored copy, no weather line").isNull();
        assertThat(without.weatherText()).isNull();
    }

    @Test
    void onlyTheGardensOfThisPageCanBeChosenThroughTheMap() {
        String login = login("map-select");
        String stranger = login("map-select-stranger");
        long space = personalSpace(login);
        long mine = garden(space, login, "Mine", "45.07", "9.19");
        long theirs = garden(personalSpace(stranger), stranger, "Theirs", "45.07", "9.19");

        GardensView view = view(login);
        view.beforeEnter(entering(space));

        assertThat(view.map().select(String.valueOf(mine))).hasValue(mine);
        assertThat(view.map().select(String.valueOf(theirs)))
                .as("a garden of another space is not one of this page's").isEmpty();
        assertThat(view.map().select("not a number")).isEmpty();
        assertThat(view.map().select(null)).isEmpty();
    }

    @Test
    void aMarkerClickSelectsItsRowAndAnotherSpacesIdSelectsNothing() {
        String login = login("map-event");
        String stranger = login("map-event-stranger");
        long space = personalSpace(login);
        long mine = garden(space, login, "Mine", "45.07", "9.19");
        long theirs = garden(personalSpace(stranger), stranger, "Theirs", "45.07", "9.19");

        GardensView view = view(login);
        view.beforeEnter(entering(space));

        com.vaadin.flow.component.ComponentUtil.fireEvent(view.map(),
                new GardenMap.GardenSelectedEvent(view.map(), true, String.valueOf(theirs)));
        assertThat(view.table().getSelectedItems()).as("another space's id is ignored").isEmpty();

        com.vaadin.flow.component.ComponentUtil.fireEvent(view.map(),
                new GardenMap.GardenSelectedEvent(view.map(), true, String.valueOf(mine)));
        assertThat(view.table().getSelectedItems()).extracting(GardenSummary::id).containsExactly(mine);
    }

    @Test
    void addingEditingAndDeletingNeedAContributor() {
        String login = login("map-add");
        String guest = login("map-add-guest");
        long space = personalSpace(login);
        spaces.addMember(space, login, guest, Role.GUEST);

        GardensView owner = view(login);
        long balcony = garden(space, login, "Balcony", "45.07", "9.19");
        owner.beforeEnter(entering(space));
        assertThat(owner.map().contributor()).isTrue();
        assertThat(owner.map().change(String.valueOf(balcony))).hasValue(balcony);
        assertThat(menuItems(owner, balcony)).hasSize(3);
        assertThat(owner.map().point(45.07, 9.19)).hasValueSatisfying(p -> {
            assertThat(p.latitude()).isEqualTo(45.07);
            assertThat(p.longitude()).isEqualTo(9.19);
        });
        assertThat(owner.map().point(91, 0)).as("a latitude off the globe").isEmpty();
        assertThat(owner.map().point(0, 181)).as("a longitude off the globe").isEmpty();

        GardensView asGuest = view(guest);
        asGuest.beforeEnter(entering(space));
        assertThat(asGuest.map().contributor()).as("a guest adds nothing").isFalse();
        assertThat(asGuest.map().point(45.07, 9.19)).as("and the server refuses the point anyway").isEmpty();
        assertThat(asGuest.map().change(String.valueOf(balcony)))
                .as("nor edits or deletes through the popup").isEmpty();
        assertThat(asGuest.map().select(String.valueOf(balcony))).as("but opens it").hasValue(balcony);
        assertThat(menuItems(asGuest, balcony)).as("the row's menu only opens").hasSize(1);
    }

    @Test
    void withoutAGardenTheMapStillOpensAndTheTableStaysAway() {
        String login = login("map-empty");
        long space = personalSpace(login);

        GardensView view = view(login);
        view.beforeEnter(entering(space));

        assertThat(groups(view)).isEmpty();
        assertThat(view.map().isVisible()).as("a view of Italy, so the map is there").isTrue();
        assertThat(view.table().isVisible()).isFalse();
    }

    @Test
    void theMapComesFirstAndTheToggleShowsTheTableOrBoth() {
        String login = login("map-toggle");
        long space = personalSpace(login);
        garden(space, login, "Balcony", "45.07", "9.19");

        GardensView view = view(login);
        view.beforeEnter(entering(space));
        assertThat(view.map().isVisible()).isTrue();
        assertThat(view.table().isVisible()).as("the map alone by default").isFalse();

        view.viewMode().setValue(GardensView.ViewMode.TABLE);
        assertThat(view.map().isVisible()).isFalse();
        assertThat(view.table().isVisible()).isTrue();

        view.viewMode().setValue(GardensView.ViewMode.MAP);
        assertThat(view.map().isVisible()).isTrue();
        assertThat(view.table().isVisible()).isFalse();

        view.viewMode().setValue(GardensView.ViewMode.BOTH);
        assertThat(view.map().isVisible()).isTrue();
        assertThat(view.table().isVisible()).isTrue();
    }

    @Test
    void deletingAGardenTakesItsPlotsAndKeepsItsQuestionsInTheSpace() {
        String login = login("map-delete");
        long space = personalSpace(login);
        long balcony = garden(space, login, "Balcony", "45.07", "9.19");
        long terrace = garden(space, login, "Terrace", "42.11", "14.71");
        long pot = gardens.savePlot(space, login, balcony, null, "Pot", PlotKind.POT, Soil.POTTING_MIX,
                null).id();
        gardens.plant(space, login, pot, "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 4, 20), null);

        GardensView view = view(login);
        view.beforeEnter(entering(space));
        view.deleteGarden(balcony);

        assertThat(view.table().getListDataView().getItems().map(GardenSummary::id)).containsExactly(terrace);
        assertThat(groups(view)).flatExtracting(GardenMap.Group::gardens).extracting(GardenMap.Pin::id)
                .containsExactly(String.valueOf(terrace));
        assertThat(jdbc.sql("select count(*) from plot where id = :id").param("id", pot).query(Long.class).single())
                .as("plots and plantings go with the garden").isZero();
        assertThat(jdbc.sql("select count(*) from planting where plot_id = :id").param("id", pot)
                .query(Long.class).single()).isZero();

        // A garden with a question: the question stays in the space, among those without a garden. Deleted
        // through the service, since the page would then link them and these tests have no router.
        long run = jdbc.sql("""
                        insert into pipeline_run (process_id, question_id, question, noise_dropout, noise_spurious,
                            noise_mislabel, noise_near_subject, noise_seed, decision_answer, decision_confidence,
                            decision_escalated, decision_reason, answer, answered_by_human, finished_at, space_id,
                            started_by, garden_id)
                        values ('p', 'q', 'What grows?', 0, 0, 0, false, 0, '{}', 1, false, '', '{}', false, now(),
                            :space, :login, :garden)
                        returning id""")
                .param("space", space).param("login", login).param("garden", terrace).query(Long.class).single();

        gardens.deleteGarden(space, login, terrace);
        assertThat(jdbc.sql("select space_id, garden_id from pipeline_run where id = :id").param("id", run)
                .query((rs, n) -> new Object[]{rs.getLong(1), rs.getObject(2)}).single())
                .as("the question stays in the space, without a garden").containsExactly(space, null);
    }

    /** The items of a row's gear menu, as the page builds them for that garden. */
    private static List<?> menuItems(GardensView view, long gardenId) {
        GardenSummary garden = view.table().getListDataView().getItems().filter(g -> g.id() == gardenId)
                .findFirst().orElseThrow();
        return view.actions(garden).getItems();
    }

    /** The map's own data, read back from the element: what the browser receives is what is checked (#66). */
    private static List<GardenMap.Group> groups(GardensView view) {
        return view.map().getElement().getPropertyBean("groups",
                new tools.jackson.core.type.TypeReference<List<GardenMap.Group>>() {
                });
    }

    private GardensView view(String login) {
        return new GardensView(gardens, geoNames, ungardened, new CurrentUser(spaces, login), cells, pipeline,
                weather, STYLE);
    }

    private long garden(long spaceId, String login, String name, String latitude, String longitude) {
        return gardens.saveGarden(spaceId, login, null, name, "Milan",
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude), "IT").id();
    }

    private String login(String prefix) {
        String login = prefix + "-" + Long.toString(System.nanoTime(), 36);
        spaces.signedIn(login);
        return login;
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    private static BeforeEnterEvent entering(long spaceId) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getRouteParameters()).thenReturn(new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))));
        when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/gardens"));
        return event;
    }
}
