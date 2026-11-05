package io.github.vincenzofiore.rootology.pipeline;

import java.time.Instant;

/** A waiting question was answered by a person (#26). */
public record EscalationResolved(String processId, long spaceId, String reviewer, Instant at) {
}
