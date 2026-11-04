package io.github.vincenzofiore.rootology.pipeline;

import java.time.Instant;

/** A question of a space waits for a person (#26): published when its agent pauses. */
public record EscalationWaiting(String processId, long spaceId, String askedBy, String question, String reason,
                                Instant at) {
}
