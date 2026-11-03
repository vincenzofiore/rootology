package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The views build with the real beans; asking the pipeline is covered by GardenPipelineAgentTest. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ViewsTest {

    @Autowired
    ExperimentRunner runner;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    RunHistory history;

    @Autowired
    InvitationService invitations;

    @Autowired
    GardenService gardens;

    @Autowired
    CropCatalog crops;

    @Autowired
    SourceCatalog sourceCatalog;

    @Test
    void everyRouteAndItsLayoutDeclareAccess() {
        // Vaadin denies a view whose route or parent layout has no access annotation.
        for (Class<?> view : List.of(ComparisonView.class, EscalationView.class, HistoryView.class, SpaceView.class,
                GardenView.class, AskView.class)) {
            Route route = view.getAnnotation(Route.class);
            assertThat(view.isAnnotationPresent(PermitAll.class)).as(view.getSimpleName()).isTrue();
            assertThat(route.layout().isAnnotationPresent(PermitAll.class)).as(route.layout().getSimpleName()).isTrue();
        }
    }

    private CurrentUser viewer() {
        return new CurrentUser(spaces, "views-test-user");
    }

    @Test
    void historyAndSpaceViewsBuild() {
        assertThat(new HistoryView(history, viewer()).getChildren()).isNotEmpty();
        assertThat(new SpaceView(spaces, invitations, viewer()).getChildren()).isNotEmpty();
        assertThat(new GardenView(gardens, crops, viewer()).getChildren()).isNotEmpty();
        assertThat(new AskView(pipeline, sourceCatalog, viewer()).getChildren()).isNotEmpty();
        assertThat(new SourcesView(sourceCatalog).getChildren()).isNotEmpty();
        assertThat(SourcesView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class)).isTrue();
    }

    @Test
    void comparisonViewBuilds() {
        assertThat(new ComparisonView(runner, pipeline, viewer()).getChildren()).isNotEmpty();
    }

    @Test
    void escalationViewListsPendingReviews() {
        EscalationView view = new EscalationView(pipeline, viewer());

        assertThat(view.getChildren().anyMatch(Grid.class::isInstance)).isTrue();
    }
}
