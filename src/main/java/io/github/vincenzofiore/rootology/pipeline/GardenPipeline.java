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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Entry point to the pipeline contestant, so callers never touch Embabel directly. */
@Service
public class GardenPipeline {

    /** An escalated question whose agent is paused, waiting for a member of its space. */
    public record PendingReview(String processId, RunContext context, Question question, Decision decision) {
    }

    /** Outcome of a run in HUMAN review mode: an answer, or a review waiting for a person. */
    public record Run(PipelineAnswer answer, PendingReview pending) {
    }

    private final AgentPlatform agentPlatform;
    private final ApplicationEventPublisher events;
    private final Map<String, PendingReview> pending = new ConcurrentHashMap<>();

    public GardenPipeline(AgentPlatform agentPlatform, ApplicationEventPublisher events) {
        this.agentPlatform = agentPlatform;
        this.events = events;
    }

    /** Full pipeline on clean facts: Claude reads the question text. */
    public PipelineAnswer ask(Question question) {
        return ask(question, NoiseProfile.NONE);
    }

    /** Full pipeline on degraded facts. Escalations are only recorded, never wait. */
    public PipelineAnswer ask(Question question, NoiseProfile noise) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(question, noise, ReviewMode.AUTOMATIC, FactScope.SUITE, RunContext.NONE);
    }

    /** Skips question understanding: the given intent is used as is, on clean facts. */
    public PipelineAnswer ask(Question question, Intent intent) {
        return AgentInvocation.create(agentPlatform, PipelineAnswer.class)
                .invoke(question, intent, NoiseProfile.NONE, ReviewMode.AUTOMATIC, FactScope.SUITE, RunContext.NONE);
    }

    /**
     * Runs in HUMAN review mode: an escalated question pauses its agent and joins {@link #pending()}.
     * Every run that finishes publishes a {@link PipelineRunFinished}.
     */
    public Run start(Question question, NoiseProfile noise, RunContext context) {
        return start(question, noise, context, FactScope.SUITE);
    }

    /** A real question from a person, answered from the curated knowledge base. */
    public Run askKnowledgeBase(Question question, RunContext context) {
        return start(question, NoiseProfile.NONE, context, FactScope.KNOWLEDGE_BASE);
    }

    private Run start(Question question, NoiseProfile noise, RunContext context, FactScope scope) {
        AgentProcess process = agentPlatform.createAgentProcessFrom(
                agent(), ProcessOptions.DEFAULT, question, noise, ReviewMode.HUMAN, scope, context);
        return settle(process.run(), question, context);
    }

    /** Escalated questions of one space waiting for a person. */
    public List<PendingReview> pending(long spaceId) {
        return pending.values().stream().filter(r -> r.context().spaceId() == spaceId).toList();
    }

    /**
     * Hands a person's answer to a paused agent and lets it finish. The review must belong to
     * the given space, so nobody can answer another space's escalation by guessing its id.
     */
    public PipelineAnswer respond(String processId, long spaceId, HumanAnswer human) {
        PendingReview review = pending.get(processId);
        if (review == null || review.context().spaceId() != spaceId) {
            throw new NoSuchElementException("No review waiting for process " + processId + " in this space");
        }
        pending.remove(processId);
        AgentProcess process = agentPlatform.getAgentProcess(processId);
        @SuppressWarnings("unchecked")
        TypeRequest<HumanAnswer> request = process.last(TypeRequest.class);
        request.onResponse(new TypeResponse<>(human, request.getId(), UUID.randomUUID().toString(),
                Instant.now(), false), process);
        Run run = settle(process.run(), review.question(), review.context());
        if (run.answer() == null) {
            throw new IllegalStateException("Process " + processId + " is still waiting after the answer");
        }
        return run.answer();
    }

    private Run settle(AgentProcess process, Question question, RunContext context) {
        if (process.getStatus() == AgentProcessStatusCode.WAITING) {
            PendingReview review = new PendingReview(process.getId(), context, question, process.last(Decision.class));
            pending.put(process.getId(), review);
            return new Run(null, review);
        }
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED) {
            throw new IllegalStateException("Pipeline ended as " + process.getStatus());
        }
        PipelineAnswer answer = process.resultOfType(PipelineAnswer.class);
        events.publishEvent(new PipelineRunFinished(process.getId(), context, question, process.last(NoiseProfile.class),
                process.last(Decision.class), answer, Instant.now()));
        return new Run(answer, null);
    }

    private Agent agent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(GardenPipelineAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow();
    }
}
