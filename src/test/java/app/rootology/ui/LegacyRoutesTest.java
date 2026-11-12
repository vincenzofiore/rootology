package app.rootology.ui;

import com.vaadin.flow.router.QueryParameters;
import app.rootology.TestcontainersConfiguration;
import app.rootology.garden.GardenService;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The links sent before #56 still lead somewhere sensible (#56): the space's only garden, the overview when
 * it has several (never "the first garden"), the space level when the old page is about questions without
 * a garden.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LegacyRoutesTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    RunHistory history;

    @Autowired
    GardenPipeline pipeline;

    @Test
    void oneGardenTakesTheOldPagesIntoIt() {
        CurrentUser user = person("legacy-one");
        long space = personalSpace(user.login());
        long garden = gardens.saveGarden(space, user.login(), null, "Only garden", null, null, null).id();

        assertThat(target(LegacyRoutes.ASK, QueryParameters.empty(), user))
                .isEqualTo("/spaces/" + space + "/gardens/" + garden + "/ask");
        assertThat(target(LegacyRoutes.GARDEN, QueryParameters.empty(), user))
                .isEqualTo("/spaces/" + space + "/gardens/" + garden + "/areas");
        assertThat(target(LegacyRoutes.KNOWLEDGE, QueryParameters.empty(), user))
                .isEqualTo("/spaces/" + space + "/gardens/" + garden + "/knowledge");
        assertThat(target(LegacyRoutes.ESCALATIONS, QueryParameters.empty(), user))
                .isEqualTo("/spaces/" + space + "/gardens/" + garden + "/escalations");
        assertThat(target(LegacyRoutes.HISTORY, QueryParameters.empty(), user))
                .isEqualTo("/spaces/" + space + "/gardens/" + garden + "/history");
        assertThat(target(LegacyRoutes.SPACE, QueryParameters.empty(), user))
                .as("space management left the old page (#56)").isEqualTo("/spaces/" + space + "/members");
    }

    @Test
    void severalGardensLeadToTheOverviewAndNoGardenIsChosenForThePerson() {
        CurrentUser user = person("legacy-many");
        long space = personalSpace(user.login());
        gardens.saveGarden(space, user.login(), null, "Balcony", null, null, null);
        gardens.saveGarden(space, user.login(), null, "Allotment", null, null, null);

        assertThat(target(LegacyRoutes.GARDEN, QueryParameters.empty(), user)).isEqualTo("/spaces/" + space + "/gardens");
        assertThat(target(LegacyRoutes.KNOWLEDGE, QueryParameters.empty(), user)).isEqualTo("/spaces/" + space + "/gardens");
        // No questions without a garden in a fresh space: the overview, not a page that would be empty.
        assertThat(target(LegacyRoutes.ESCALATIONS, QueryParameters.empty(), user))
                .isEqualTo("/spaces/" + space + "/gardens");
        assertThat(target(LegacyRoutes.HISTORY, QueryParameters.empty(), user)).isEqualTo("/spaces/" + space + "/gardens");
    }

    @Test
    void aSpaceThePersonIsNotInIsIgnored() {
        CurrentUser user = person("legacy-stranger");
        CurrentUser other = person("legacy-someone-else");
        long theirSpace = personalSpace(other.login());

        assertThat(target(LegacyRoutes.ASK, QueryParameters.of("space", String.valueOf(theirSpace)), user))
                .isEqualTo("/spaces/" + personalSpace(user.login()) + "/ask");
        assertThat(target(LegacyRoutes.ASK, QueryParameters.of("space", "nonsense"), user))
                .isEqualTo("/spaces/" + personalSpace(user.login()) + "/ask");
    }

    private String target(String path, QueryParameters query, CurrentUser user) {
        return LegacyRoutes.target(path, query, gardens, history, pipeline, user);
    }

    private CurrentUser person(String login) {
        spaces.signedIn(login);
        return new CurrentUser(spaces, login);
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }
}
