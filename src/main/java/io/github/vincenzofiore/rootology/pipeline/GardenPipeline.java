package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.hitl.TypeRequest;
import com.embabel.agent.core.hitl.TypeResponse;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.Intent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Entry point to the pipeline contestant, so callers never touch Embabel directly. */
@Service
public class GardenPipeline {

    /** An escalated question whose agent is paused, waiting for a person. */
    public record PendingReview(String processId, Question question, Decision decision) {
    }

    /** Outcome of a run in HUMAN review mode: an answer, or a review waiting for a person. */
    public record Run(PipelineAnswer answer, PendingReview pending) {
    }

    private final AgentPlatform agentPlatform;
    private final Map<String, PendingReview> pending = new ConcurrentHashMap<>();

    public GardenPipeline(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    /** Full pipeline on clean facts: Claude reads the question text. */
    public PipelineAnswer ask(Question question) {
        return ask(question, NoiseProfile.NONE);
    }

    /** Full pipeline on degraded facts. Escalations are only recorded, never wait. */
    public PipelineAnswer ask(Question question, NoiseProfile noise) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(question, noise, ReviewMode.AUTOMATIC);
    }

    /** Skips question understanding: the given intent is used as is, on clean facts. */
    public PipelineAnswer ask(Question question, Intent intent) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(question, intent, NoiseProfile.NONE, ReviewMode.AUTOMATIC);
    }

    /** Runs in HUMAN review mode: an escalated question pauses its agent and joins {@link #pending()}. */
    public Run start(Question question, NoiseProfile noise) {
        AgentProcess process = agentPlatform.createAgentProcessFrom(
                agent(), ProcessOptions.DEFAULT, question, noise, ReviewMode.HUMAN);
        return settle(process.run(), question);
    }

    /** Escalated questions waiting for a person, oldest first. */
    public List<PendingReview> pending() {
        return new ArrayList<>(pending.values());
    }

    /** Hands a person's answer to a paused agent and lets it finish. */
    public PipelineAnswer respond(String processId, HumanAnswer human) {
        PendingReview review = pending.remove(processId);
        if (review == null) {
            throw new NoSuchElementException("No review waiting for process " + processId);
        }
        AgentProcess process = agentPlatform.getAgentProcess(processId);
        @SuppressWarnings("unchecked")
        TypeRequest<HumanAnswer> request = process.last(TypeRequest.class);
        request.onResponse(new TypeResponse<>(human, request.getId(), UUID.randomUUID().toString(),
                Instant.now(), false), process);
        Run run = settle(process.run(), review.question());
        if (run.answer() == null) {
            throw new IllegalStateException("Process " + processId + " is still waiting after the answer");
        }
        return run.answer();
    }

    private Run settle(AgentProcess process, Question question) {
        if (process.getStatus() == AgentProcessStatusCode.WAITING) {
            PendingReview review = new PendingReview(process.getId(), question, process.last(Decision.class));
            pending.put(process.getId(), review);
            return new Run(null, review);
        }
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED) {
            throw new IllegalStateException("Pipeline ended as " + process.getStatus());
        }
        return new Run(process.resultOfType(PipelineAnswer.class), null);
    }

    private Agent agent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(GardenPipelineAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow();
    }
}
