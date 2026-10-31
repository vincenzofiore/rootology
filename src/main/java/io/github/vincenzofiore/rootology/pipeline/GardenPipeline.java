package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import io.github.vincenzofiore.rootology.decision.Intent;
import org.springframework.stereotype.Service;

/** Entry point to the pipeline contestant, so callers never touch Embabel directly. */
@Service
public class GardenPipeline {

    private final AgentPlatform agentPlatform;

    public GardenPipeline(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    /** Full pipeline on clean facts: Claude reads the question text. */
    public PipelineAnswer ask(Question question) {
        return ask(question, NoiseProfile.NONE);
    }

    /** Full pipeline on degraded facts. */
    public PipelineAnswer ask(Question question, NoiseProfile noise) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(question, noise);
    }

    /** Skips question understanding: the given intent is used as is, on clean facts. */
    public PipelineAnswer ask(Question question, Intent intent) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(question, intent, NoiseProfile.NONE);
    }
}
