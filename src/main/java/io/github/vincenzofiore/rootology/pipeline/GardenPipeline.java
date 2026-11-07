package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.LlmInvocation;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.hitl.TypeRequest;
import com.embabel.agent.core.hitl.TypeResponse;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.JevDecisionLayer;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.usage.RunUsage;
import io.github.vincenzofiore.rootology.usage.UsageKind;
import io.github.vincenzofiore.rootology.usage.UsageService;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Entry point to the pipeline contestant, so callers never touch Embabel directly. */
@Service
public class GardenPipeline {

    /** An escalated question whose agent is paused, waiting for a member of its space. */
    public record PendingReview(String processId, RunContext context, Question question, Decision decision) {
    }

    /** Outcome of a run in HUMAN review mode: an answer, or a review waiting for a person. */
    /** intent: how Claude understood the question (with defaults applied), for the quality suite (#20). */
    public record Run(PipelineAnswer answer, PendingReview pending, Intent intent) {
    }

    private final AgentPlatform agentPlatform;
    private final ApplicationEventPublisher events;
    private final PendingEscalations pending;
    private final TransactionTemplate transaction;
    private final ClaudeCircuit claude;
    private final SpaceService spaces;
    private final UsageService usage;
    private final DecisionLayer decisionLayer;
    private final PipelineMetrics metrics;

    GardenPipeline(AgentPlatform agentPlatform, ApplicationEventPublisher events, ClaudeCircuit claude,
                   SpaceService spaces, UsageService usage, DecisionLayer decisionLayer, PendingEscalations pending,
                   PlatformTransactionManager transactions, PipelineMetrics metrics) {
        this.agentPlatform = agentPlatform;
        this.pending = pending;
        this.transaction = new TransactionTemplate(transactions);
        this.events = events;
        this.claude = claude;
        this.spaces = spaces;
        this.usage = usage;
        this.decisionLayer = decisionLayer;
        this.metrics = metrics;
    }

    /** Fails at once while Claude's circuit is open, instead of waiting for Claude's retries (#29). */
    private void requireClaude() {
        if (claude.breaker.state() == CircuitBreaker.State.OPEN) {
            throw new ServiceUnavailableException(ClaudeCircuit.UNAVAILABLE);
        }
    }

    /** Full pipeline on clean facts: Claude reads the question text. */
    public PipelineAnswer ask(Question question) {
        return ask(question, NoiseProfile.NONE);
    }

    /** Full pipeline on degraded facts. Escalations are only recorded, never wait. */
    public PipelineAnswer ask(Question question, NoiseProfile noise) {
        requireClaude();
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
        if (question.text() == null || question.text().isBlank() || question.text().length() > Question.MAX_LENGTH) {
            throw new IllegalArgumentException("A question needs between 1 and " + Question.MAX_LENGTH + " characters");
        }
        if (context.hasSpace()) {
            // A guest only looks: no questions in the space (#33).
            spaces.requireContributor(context.spaceId(), context.startedBy());
            // Before any paid call (#19).
            usage.requireQuestionAvailable(context.startedBy());
        }
        requireClaude();
        Timer.Sample timing = metrics.start();
        AgentProcess process = agentPlatform.createAgentProcessFrom(
                agent(), ProcessOptions.DEFAULT, question, noise, ReviewMode.HUMAN, scope, context);
        try {
            Run run = settle(process.run(), question, context);
            metrics.finished(timing, scope, run);
            return run;
        } catch (RuntimeException e) {
            metrics.failed(timing, scope);
            throw e;
        } finally {
            if (context.hasSpace()) {
                usage.record(context.startedBy(), context.spaceId(), UsageKind.QUESTION, usageOf(process, 0));
            }
        }
    }

    /** The Claude calls made since invocation number `from`, and the Jev call of the run if it reached Jev. */
    private RunUsage usageOf(AgentProcess process, int from) {
        var invocations = process.getLlmInvocations();
        var since = invocations.subList(Math.min(from, invocations.size()), invocations.size());
        int prompt = since.stream().mapToInt(i -> i.getUsage().getPromptTokens() == null ? 0 : i.getUsage().getPromptTokens()).sum();
        int completion = since.stream()
                .mapToInt(i -> i.getUsage().getCompletionTokens() == null ? 0 : i.getUsage().getCompletionTokens()).sum();
        double cost = since.stream().mapToDouble(LlmInvocation::cost).sum();
        Intent intent = process.last(Intent.class);
        boolean jev = from == 0 && decisionLayer instanceof JevDecisionLayer && process.last(Decision.class) != null
                && intent != null && intent.kind() != QueryKind.OTHER;
        return new RunUsage(since.size(), prompt, completion, cost, jev ? 1 : 0);
    }

    /** Escalated questions of one space waiting for a person, from the database (#36); for its members only (#41). */
    public List<PendingReview> pending(long spaceId, String login) {
        if (!spaces.isMember(spaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + spaceId);
        }
        return pending.waitingIn(spaceId);
    }

    /**
     * Hands a person's answer to a paused agent and lets it finish. The review must belong to
     * the given space, so nobody can answer another space's escalation by guessing its id.
     */
    public PipelineAnswer respond(String processId, long spaceId, HumanAnswer human) {
        spaces.requireContributor(spaceId, human.reviewer());
        PendingEscalations.Stored stored = pending.waiting(processId)
                .filter(s -> s.review().context().spaceId() == spaceId)
                .orElseThrow(() -> new NoSuchElementException("No review waiting for process " + processId
                        + " in this space"));
        PendingReview review = stored.review();
        AgentProcess paused = agentPlatform.getAgentProcess(processId);
        AgentProcess process;
        int before;
        if (paused != null && paused.getStatus() == AgentProcessStatusCode.WAITING) {
            process = paused;
            before = process.getLlmInvocations().size();
            @SuppressWarnings("unchecked")
            TypeRequest<HumanAnswer> request = process.last(TypeRequest.class);
            request.onResponse(new TypeResponse<>(human, request.getId(), UUID.randomUUID().toString(),
                    Instant.now(), false), process);
        } else {
            // The paused agent is gone (a restart, #36): a new run of the same agent starts from what was
            // stored and the person's answer, so only the phrasing step is left to do.
            requireClaude();
            process = agentPlatform.createAgentProcessFrom(agent(), ProcessOptions.DEFAULT, review.question(),
                    review.decision(), stored.facts(), stored.noise(), Resolution.byHuman(review.decision(), human),
                    ReviewMode.HUMAN, review.context());
            before = 0;
        }
        Run run;
        try {
            run = settle(process.run(), review.question(), review.context());
        } finally {
            // Recorded, never blocked: whoever answers a waiting question must not be stopped (#19).
            usage.record(human.reviewer(), spaceId, UsageKind.REVIEW, usageOf(process, before));
        }
        if (run.answer() == null) {
            throw new IllegalStateException("Process " + processId + " is still waiting after the answer");
        }
        transaction.executeWithoutResult(status -> {
            pending.resolve(processId, human.reviewer(), Instant.now());
            events.publishEvent(new EscalationResolved(processId, spaceId, review.context().startedBy(),
                    human.reviewer(), Instant.now()));
        });
        return run.answer();
    }

    private Run settle(AgentProcess process, Question question, RunContext context) {
        if (process.getStatus() == AgentProcessStatusCode.WAITING) {
            PendingReview review = new PendingReview(process.getId(), context, question, process.last(Decision.class));
            // Stored and announced together (#36): the notifications (#26) are written in the same transaction.
            transaction.executeWithoutResult(status -> {
                pending.save(process.getId(), context, question, review.decision(), process.last(GraphFacts.class),
                        process.last(NoiseProfile.class), Instant.now());
                if (context.hasSpace()) {
                    events.publishEvent(new EscalationWaiting(process.getId(), context.spaceId(), context.startedBy(),
                            question.text(), review.decision().reason(), Instant.now()));
                }
            });
            return new Run(null, review, process.last(Intent.class));
        }
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED) {
            if (claude.breaker.failing()) {
                throw new ServiceUnavailableException(ClaudeCircuit.UNAVAILABLE);
            }
            throw new IllegalStateException("Pipeline ended as " + process.getStatus());
        }
        PipelineAnswer answer = process.resultOfType(PipelineAnswer.class);
        events.publishEvent(new PipelineRunFinished(process.getId(), context, question, process.last(NoiseProfile.class),
                process.last(Decision.class), answer, Instant.now()));
        return new Run(answer, null, process.last(Intent.class));
    }

    private Agent agent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(GardenPipelineAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow();
    }
}
