package app.rootology;

import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.enrichment.ProposalScope;
import app.rootology.facts.Contribution;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.ui.CurrentUser;
import app.rootology.ui.GardenMap;
import app.rootology.ui.GardensView;
import app.rootology.ui.Ungardened;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Broken access control (#41, OWASP A01): a member of one space tries the ids of another space's gardens,
 * plots, plantings, proposals, invitations, history and escalations, and is refused every time; the other
 * space's data stays as it was.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CrossSpaceAccessTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeEnrichment enrichment;

    @Autowired
    InvitationService invitations;

    @Autowired
    RunHistory history;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    ClimateCells cells;

    @Autowired
    Ungardened ungardened;

    @Autowired
    app.rootology.weather.WeatherService weather;

    @Autowired
    app.rootology.geocoding.GeoNames geoNames;

    String victim;
    String intruder;
    long victimSpace;
    long intruderSpace;
    long garden;
    long plot;
    long planting;

    @BeforeEach
    void twoSpaces() {
        String suffix = Long.toString(System.nanoTime(), 36);
        victim = "victim-" + suffix;
        intruder = "intruder-" + suffix;
        spaces.signedIn(victim);
        spaces.signedIn(intruder);
        victimSpace = app.rootology.TestSpaces.personal(spaces, victim);
        intruderSpace = app.rootology.TestSpaces.personal(spaces, intruder);
        garden = gardens.saveGarden(victimSpace, victim, null, "Victim garden", null, null, null).id();
        plot = gardens.savePlot(victimSpace, victim, garden, null, "A", PlotKind.GROUND, Soil.LOAMY, null).id();
        planting = gardens.plant(victimSpace, victim, plot, "tomato", PlantingMethod.SOWN, LocalDate.of(2026, 4, 1), null)
                .id();
    }

    @Test
    void anotherSpacesIdsAreRefusedFromTheIntrudersOwnSpace() {
        // The intruder uses their own space, where they may change things, with the victim's ids.
        assertThatThrownBy(() -> gardens.plots(intruderSpace, intruder, garden)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deleteGarden(intruderSpace, intruder, garden)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.savePlot(intruderSpace, intruder, garden, plot, "Mine", PlotKind.GROUND, Soil.CLAY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deletePlot(intruderSpace, intruder, plot)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.plantings(intruderSpace, intruder, plot)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.endPlanting(intruderSpace, intruder, planting, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deletePlanting(intruderSpace, intruder, planting)).isInstanceOf(IllegalArgumentException.class);

        long proposal = enrichment.propose(victimSpace, victim, ProposalScope.SPACE,
                new Contribution.Alias("tomato", "pomodorino di casa", "it"), null).id();
        assertThatThrownBy(() -> enrichment.withdraw(intruderSpace, intruder, proposal)).isInstanceOf(IllegalArgumentException.class);
        assertThat(enrichment.source(intruderSpace, intruder, "proposal-" + proposal)).isEmpty();

        invitations.create(victimSpace, victim, Role.MEMBER, null);
        long invitation = invitations.pending(victimSpace, victim).getFirst().id();
        assertThatThrownBy(() -> invitations.revoke(intruderSpace, intruder, invitation)).isInstanceOf(RuntimeException.class);

        // Nothing of the victim's changed.
        assertThat(gardens.plantings(victimSpace, victim, plot)).singleElement()
                .satisfies(p -> assertThat(p.endedOn()).isNull());
        assertThat(enrichment.ofSpace(victimSpace, victim)).hasSize(1);
        assertThat(invitations.pending(victimSpace, victim)).hasSize(1);
    }

    @Test
    void aGardenOfAnotherSpaceIsUnknown() {
        // #56: the garden id from another space is never found, whichever space the person uses it with.
        long otherGarden = gardens.saveGarden(intruderSpace, intruder, null, "Intruder garden", null, null, null).id();

        assertThat(gardens.garden(victimSpace, victim, otherGarden)).isEmpty();
        assertThat(gardens.garden(intruderSpace, intruder, garden)).isEmpty();
        assertThatThrownBy(() -> gardens.garden(victimSpace, intruder, garden)).isInstanceOf(RuntimeException.class);
        // The questions of the victim's garden never show under a garden id of the intruder's space.
        assertThat(pipeline.pending(intruderSpace, garden, intruder)).isEmpty();
        assertThat(history.recent(intruderSpace, garden, intruder)).isEmpty();
    }

    @Test
    void anotherSpacesGardenNeverReachesTheMap() {
        // #66: the map of an overview only ever carries that page's own space, so the intruder's map never
        // shows the victim's garden, and the map refuses to select it.
        long located = gardens.saveGarden(victimSpace, victim, null, "Victim map garden", "Milan",
                new java.math.BigDecimal("45.07"), new java.math.BigDecimal("9.19"), "IT").id();
        GardensView intrudersOverview = new GardensView(gardens, geoNames, ungardened, session(intruder, intruderSpace),
                cells, pipeline, weather, "https://tiles.openfreemap.org/styles/liberty");
        intrudersOverview.beforeEnter(entering(intruderSpace));
        assertThat(mapIds(intrudersOverview)).doesNotContain(located);
        assertThat(intrudersOverview.map().select(Long.toString(located)))
                .as("and the map refuses to select it").isEmpty();

        GardensView victimsOverview = new GardensView(gardens, geoNames, ungardened, session(victim, victimSpace),
                cells, pipeline, weather, "https://tiles.openfreemap.org/styles/liberty");
        victimsOverview.beforeEnter(entering(victimSpace));
        assertThat(victimsOverview.map().select(Long.toString(located))).hasValue(located);
    }

    @Test
    void anotherSpaceIsRefusedOutright() {
        assertThatThrownBy(() -> gardens.gardens(victimSpace, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> gardens.plots(victimSpace, intruder, garden)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> enrichment.ofSpace(victimSpace, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> invitations.pending(victimSpace, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> history.recent(victimSpace, garden, intruder)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> pipeline.pending(victimSpace, garden, intruder)).isInstanceOf(RuntimeException.class);
        assertThat(enrichment.source(victimSpace, intruder, "proposal-1")).isEmpty();
    }

    /** A signed-in person of one space: the UI's own session object, without a Vaadin session (#66). */
    private CurrentUser session(String login, long spaceId) {
        CurrentUser user = org.mockito.Mockito.mock(CurrentUser.class);
        org.mockito.Mockito.when(user.login()).thenReturn(login);
        org.mockito.Mockito.when(user.memberOf(spaceId)).thenReturn(true);
        org.mockito.Mockito.when(user.canContribute(spaceId)).thenReturn(true);
        org.mockito.Mockito.when(user.spaces()).thenReturn(spaces.spacesOf(login));
        return user;
    }

    /** The garden ids behind the map's markers, as the browser would receive them (#66). */
    private static java.util.List<Long> mapIds(GardensView view) {
        java.util.List<GardenMap.Group> groups = view.map().getElement().getPropertyBean("groups",
                new tools.jackson.core.type.TypeReference<java.util.List<GardenMap.Group>>() {
                });
        return groups.stream().flatMap(group -> group.gardens().stream()).map(pin -> Long.parseLong(pin.id()))
                .toList();
    }

    private static BeforeEnterEvent entering(long spaceId) {
        BeforeEnterEvent event = org.mockito.Mockito.mock(BeforeEnterEvent.class);
        org.mockito.Mockito.when(event.getRouteParameters())
                .thenReturn(new RouteParameters(java.util.Map.of("spaceId", String.valueOf(spaceId))));
        org.mockito.Mockito.when(event.getLocation()).thenReturn(new Location("spaces/" + spaceId + "/gardens"));
        return event;
    }
}
