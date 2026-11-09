package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Process ids are keys (#61): pending escalations, their notifications and history rows are found by them,
 * so Embabel must hand out ids that cannot repeat. The ids are taken from the platform, the way Embabel
 * creates every agent process, so this also proves the platform really uses our generator.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "rootology.jev.api-key=")
class ProcessIdTest extends EmbabelMockitoIntegrationTest {

    @Test
    void everyAgentProcessGetsItsOwnUuid() {
        Agent agent = agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(GardenPipelineAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow();

        String first = agentPlatform.createAgentProcess(agent, ProcessOptions.DEFAULT, Map.of()).getId();
        String second = agentPlatform.createAgentProcess(agent, ProcessOptions.DEFAULT, Map.of()).getId();

        assertThat(first).isNotEqualTo(second);
        assertThat(UUID.fromString(first).toString()).isEqualTo(first);
        assertThat(UUID.fromString(second).toString()).isEqualTo(second);
    }
}
