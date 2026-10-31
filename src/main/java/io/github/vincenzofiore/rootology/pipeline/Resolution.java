package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Decision;

import java.util.List;

/** The settled answer, either the decision layer's own or a person's after an escalation. */
public record Resolution(List<String> answer, double confidence, boolean escalated,
                         boolean answeredByHuman, String reason) {

    static Resolution of(Decision decision) {
        return new Resolution(decision.answer(), decision.confidence(), decision.escalate(), false, decision.reason());
    }

    static Resolution byHuman(Decision decision, HumanAnswer human) {
        String reason = human.note() == null || human.note().isBlank() ? decision.reason() : human.note();
        return new Resolution(human.answer(), decision.confidence(), true, true, reason);
    }
}
