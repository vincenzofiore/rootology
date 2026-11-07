package io.github.vincenzofiore.rootology.pipeline;

import java.time.Instant;

/** A waiting question was answered by a person (#26); {@code askedBy} is who was waiting for it (#51). */
public record EscalationResolved(String processId, long spaceId, String askedBy, String reviewer, Instant at) {
}
