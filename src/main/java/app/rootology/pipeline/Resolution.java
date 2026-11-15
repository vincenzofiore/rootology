package app.rootology.pipeline;

import app.rootology.decision.Decision;

import java.util.List;

/** The settled answer, either the decision layer's own or a person's after an escalation. */
public record Resolution(List<String> answer, double confidence, boolean escalated,
                         boolean answeredByHuman, String reviewer, String reason) {

    static Resolution of(Decision decision) {
        return new Resolution(decision.answer(), decision.confidence(), decision.escalate(), false, null,
                decision.reason());
    }

    static Resolution byHuman(Decision decision, HumanAnswer human) {
        // Only what the person wrote (#118): the decision layer's reason ("Jev: … p=0,10") is not their note, and
        // Claude read it as one, calling the reviewer Jev.
        String reason = human.note() == null ? "" : human.note().strip();
        return new Resolution(human.answer(), decision.confidence(), true, true, human.reviewer(), reason);
    }
}
