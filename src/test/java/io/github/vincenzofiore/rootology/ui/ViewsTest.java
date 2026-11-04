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
    io.github.vincenzofiore.rootology.experiments.GoldenRuns goldenRuns;

    @Autowired
    io.github.vincenzofiore.rootology.roles.RoleService roles;

    @Autowired
    io.github.vincenzofiore.rootology.usage.UsageService usage;

    @Autowired
    io.github.vincenzofiore.rootology.notifications.NotificationService notificationService;

    @Autowired
    io.github.vincenzofiore.rootology.geocoding.GeoNames geoNames;

    @Autowired
    SourceCatalog sourceCatalog;

    @Autowired
    io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment enrichment;

    @Autowired
    io.github.vincenzofiore.rootology.facts.KnowledgeGraph knowledge;

    @Test
    void everyRouteAndItsLayoutDeclareAccess() {
        // Vaadin denies a view whose route or parent layout has no access annotation.
        for (Class<?> view : List.of(ComparisonView.class, EscalationView.class, HistoryView.class, SpaceView.class,
                GardenView.class, AskView.class, KnowledgeView.class, AdminView.class, NotificationsView.class)) {
            Route route = view.getAnnotation(Route.class);
            assertThat(view.isAnnotationPresent(PermitAll.class)
                    || view.isAnnotationPresent(jakarta.annotation.security.RolesAllowed.class)).as(view.getSimpleName()).isTrue();
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
        assertThat(new GardenView(gardens, crops, geoNames, viewer()).getChildren()).isNotEmpty();
        assertThat(new AskView(pipeline, sourceCatalog, viewer(), enrichment, usage).getChildren()).isNotEmpty();
        assertThat(new SourcesView(sourceCatalog).getChildren()).isNotEmpty();
        assertThat(new NotificationsView(notificationService, viewer()).getChildren()).isNotEmpty();
        assertThat(new KnowledgeView(enrichment, crops, knowledge, viewer()).getChildren()).isNotEmpty();
        assertThat(AdminView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("ADMIN");

        assertThat(SourcesView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class)).isTrue();
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "views-test-user", roles = "ADMIN")
    void adminViewShowsPeopleAndHistoryToAnAdmin() {
        assertThat(new AdminView(roles, usage, viewer()).getChildren().filter(Grid.class::isInstance).count()).isEqualTo(2);
    }

    @Test
    void comparisonViewBuildsAndIsForQaOnly() {
        assertThat(new ComparisonView(runner, pipeline, goldenRuns, viewer()).getChildren()).isNotEmpty();
        assertThat(ComparisonView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("QA");
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "views-test-user", roles = "QA")
    void historyShowsTheArbiterToQaOnly() {
        long suiteColumnsForQa = new HistoryView(history, viewer()).getChildren().filter(Grid.class::isInstance)
                .map(Grid.class::cast).flatMap(g -> g.getColumns().stream())
                .filter(c -> ((Grid.Column<?>) c).isVisible()).count();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        long columnsForMember = new HistoryView(history, viewer()).getChildren().filter(Grid.class::isInstance)
                .map(Grid.class::cast).flatMap(g -> g.getColumns().stream())
                .filter(c -> ((Grid.Column<?>) c).isVisible()).count();

        assertThat(suiteColumnsForQa - columnsForMember).isEqualTo(3);
    }

    @Test
    void escalationViewListsPendingReviews() {
        EscalationView view = new EscalationView(pipeline, viewer());

        assertThat(view.getChildren().anyMatch(Grid.class::isInstance)).isTrue();
    }
}
