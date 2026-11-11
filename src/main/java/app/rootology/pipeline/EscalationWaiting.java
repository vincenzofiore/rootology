package app.rootology.pipeline;

import java.time.Instant;

/** A question of a space, in one of its gardens or at the space level (garden 0), waits for a person (#26, #56). */
public record EscalationWaiting(String processId, long spaceId, long gardenId, String askedBy, String question,
                                String reason, Instant at) {
}
