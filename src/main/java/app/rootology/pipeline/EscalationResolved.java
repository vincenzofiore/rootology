package app.rootology.pipeline;

import java.time.Instant;

/**
 * A waiting question was answered by a person (#26); {@code askedBy} is who was waiting for it (#51), and
 * the garden is where it was asked (#56), 0 at the space level.
 */
public record EscalationResolved(String processId, long spaceId, long gardenId, String askedBy, String reviewer,
                                 Instant at) {
}
