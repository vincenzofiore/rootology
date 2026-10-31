package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.grid.Grid;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** The views build with the real beans; asking the pipeline is covered by GardenPipelineAgentTest. */
@SpringBootTest
class ViewsTest {

    @Autowired
    ExperimentRunner runner;

    @Autowired
    GardenPipeline pipeline;

    @Test
    void comparisonViewBuilds() {
        assertThat(new ComparisonView(runner, pipeline).getChildren()).isNotEmpty();
    }

    @Test
    void escalationViewListsPendingReviews() {
        EscalationView view = new EscalationView(pipeline);

        assertThat(view.getChildren().anyMatch(Grid.class::isInstance)).isTrue();
    }
}
