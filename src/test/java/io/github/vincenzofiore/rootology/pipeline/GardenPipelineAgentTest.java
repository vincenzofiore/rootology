package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the pipeline agent on the real Embabel platform for each experiment case.
 * No LLM call is made yet, so no API key is needed.
 */
@SpringBootTest
class GardenPipelineAgentTest {

    @Autowired
    AgentPlatform agentPlatform;

    private PipelineAnswer ask(String id, String text, Intent intent) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(new Question(id, text), intent);
    }

    @Test
    void easy1CompanionsOfTomato() {
        var answer = ask("easy-1", "Which plants are companions of Tomato?",
                Intent.of(QueryKind.COMPANIONS, "Tomato"));

        assertThat(answer.answer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(answer.escalated()).isFalse();
    }

    @Test
    void easy2RelevantPestsForBasilInSummer() {
        var answer = ask("easy-2", "Which pests are relevant for Basil in summer?",
                new Intent(QueryKind.RELEVANT_PESTS, "Basil", "SUMMER"));

        assertThat(answer.answer()).containsExactly("Nematode");
        assertThat(answer.escalated()).isFalse();
    }

    @Test
    void hard1CompanionsOfSharedRepellers() {
        var answer = ask("hard-1", "Find companions of plants that repel the same pest repelled by Tomato",
                Intent.of(QueryKind.COMPANIONS_OF_SHARED_REPELLERS, "Tomato"));

        // Same artifact as Jena: Basil shares Aphid with Tomato, and Basil's only companion is Tomato.
        assertThat(answer.answer()).containsExactly("Tomato");
        assertThat(answer.escalated()).isFalse();
    }

    @Test
    void hard2MosquitoSeasonIsEscalated() {
        var answer = ask("hard-2", "In which season is Mosquito active?",
                Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"));

        assertThat(answer.answer()).isEmpty();
        assertThat(answer.escalated()).isTrue();
        assertThat(answer.text()).contains("not declared");
    }

    @Test
    void hard3LavenderIsEscalated() {
        var answer = ask("hard-3", "Which plants are companions of Lavender?",
                Intent.of(QueryKind.COMPANIONS, "Lavender"));

        assertThat(answer.answer()).isEmpty();
        assertThat(answer.escalated()).isTrue();
        assertThat(answer.text()).contains("not in the dataset");
    }
}
