package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.inference.facts.FactSource;

/**
 * Third contestant. Blackboard flow: Question (+ NoiseProfile) -> Intent -> GraphFacts -> Decision -> PipelineAnswer.
 * Claude only reads the question and phrases the answer; it never sees the facts and never
 * chooses the answer values, which come from the decision layer.
 */
@Agent(description = "Answers closed-domain companion-planting questions from raw graph facts")
public class GardenPipelineAgent {

    // No dataset content here on purpose: Claude must parse the question, not answer it.
    static final String UNDERSTAND_PROMPT = """
            Read a gardening question and classify it into a structured intent.

            kind is one of:
            - COMPANIONS: which plants are companions of a plant
            - RELEVANT_PESTS: which pests are relevant for a plant in a given season
            - COMPANIONS_OF_SHARED_REPELLERS: companions of the plants that repel the same pest as a plant
            - ACTIVE_SEASONS: in which seasons a pest is active
            - OTHER: anything else

            subject: the plant or pest the question is about, as a single capitalized English
            name exactly as named in the question (e.g. "Tomato"). Do not correct or replace it.
            season: SPRING, SUMMER, AUTUMN or WINTER for RELEVANT_PESTS, otherwise null.
            Do not answer the question.

            Question: %s
            """;

    static final String PHRASE_PROMPT = """
            Write a one or two sentence answer to a gardening question.
            Use only the values given below. Do not add, remove or rename any plant, pest or
            season, and do not add gardening advice.
            If "escalated" is true, say that the question is being handed to a human and why.

            Question: %s
            Values: %s
            Escalated: %s
            Reason: %s
            """;

    private final FactSource factSource;
    private final DecisionLayer decisionLayer;

    public GardenPipelineAgent(FactSource factSource, DecisionLayer decisionLayer) {
        this.factSource = factSource;
        this.decisionLayer = decisionLayer;
    }

    @Action(description = "Turn the question text into a structured intent")
    public Intent understand(Question question, OperationContext context) {
        return context.ai().withDefaultLlm()
                .createObject(UNDERSTAND_PROMPT.formatted(question.text()), Intent.class);
    }

    @Action(description = "Load the raw declared facts from the knowledge graph, degraded by the noise profile")
    public GraphFacts gatherFacts(Intent intent, NoiseProfile noise) {
        return new GraphFacts(NoisyFacts.apply(factSource.allFacts(), intent, noise));
    }

    @Action(description = "Decide the answer and its confidence from the raw facts")
    public Decision decide(Intent intent, GraphFacts facts) {
        return decisionLayer.decide(intent, facts);
    }

    @AchievesGoal(description = "The question is answered, or escalated to a human")
    @Action(description = "Phrase the decision as the pipeline's answer")
    public PipelineAnswer answer(Question question, Decision decision, OperationContext context) {
        String text = context.ai().withDefaultLlm().generateText(PHRASE_PROMPT.formatted(
                question.text(), decision.answer(), decision.escalate(), decision.reason()));
        return new PipelineAnswer(question.id(), decision.answer(), decision.confidence(),
                decision.escalate(), text);
    }
}
