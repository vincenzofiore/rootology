package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.inference.facts.FactSource;

/**
 * Third contestant. Blackboard flow: Question + Intent -> GraphFacts -> Decision -> PipelineAnswer.
 * Intent is supplied by the caller for now; Claude will derive it from the question text and
 * phrase the final answer in a later step.
 */
@Agent(description = "Answers closed-domain companion-planting questions from raw graph facts")
public class GardenPipelineAgent {

    private final FactSource factSource;
    private final DecisionLayer decisionLayer;

    public GardenPipelineAgent(FactSource factSource, DecisionLayer decisionLayer) {
        this.factSource = factSource;
        this.decisionLayer = decisionLayer;
    }

    @Action(description = "Load the raw declared facts from the knowledge graph")
    public GraphFacts gatherFacts(Intent intent) {
        return new GraphFacts(factSource.allFacts());
    }

    @Action(description = "Decide the answer and its confidence from the raw facts")
    public Decision decide(Intent intent, GraphFacts facts) {
        return decisionLayer.decide(intent, facts);
    }

    @AchievesGoal(description = "The question is answered, or escalated to a human")
    @Action(description = "Phrase the decision as the pipeline's answer")
    public PipelineAnswer answer(Question question, Decision decision) {
        String text = decision.escalate()
                ? "Escalated to a human: " + decision.reason() + "."
                : String.join(", ", decision.answer()) + " (" + decision.reason() + ").";
        return new PipelineAnswer(question.id(), decision.answer(), decision.confidence(),
                decision.escalate(), text);
    }
}
