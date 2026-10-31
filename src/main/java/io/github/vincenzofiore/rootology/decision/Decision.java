package io.github.vincenzofiore.rootology.decision;

import java.util.List;

/**
 * Output of the decision layer.
 *
 * @param answer     the decided values (plant, pest or season names)
 * @param confidence 0.0 to 1.0
 * @param escalate   true when a human should look at it (low confidence or out of scope)
 * @param reason     short explanation, kept for the experiment record
 */
public record Decision(List<String> answer, double confidence, boolean escalate, String reason) {

    public static Decision certain(List<String> answer, String reason) {
        return new Decision(answer, 1.0, false, reason);
    }

    public static Decision escalated(String reason) {
        return new Decision(List.of(), 0.0, true, reason);
    }
}
