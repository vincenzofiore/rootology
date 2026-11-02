package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
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
    AuthenticationContext authentication;

    @Test
    void everyRouteAndItsLayoutDeclareAccess() {
        // Vaadin denies a view whose route or parent layout has no access annotation.
        for (Class<?> view : List.of(ComparisonView.class, EscalationView.class, HistoryView.class)) {
            Route route = view.getAnnotation(Route.class);
            assertThat(view.isAnnotationPresent(PermitAll.class)).as(view.getSimpleName()).isTrue();
            assertThat(route.layout().isAnnotationPresent(PermitAll.class)).as(route.layout().getSimpleName()).isTrue();
        }
    }

    @Test
    void comparisonViewBuilds() {
        assertThat(new ComparisonView(runner, pipeline).getChildren()).isNotEmpty();
    }

    @Test
    void escalationViewListsPendingReviews() {
        EscalationView view = new EscalationView(pipeline, authentication);

        assertThat(view.getChildren().anyMatch(Grid.class::isInstance)).isTrue();
    }
}
