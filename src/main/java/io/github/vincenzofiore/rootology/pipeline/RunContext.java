package io.github.vincenzofiore.rootology.pipeline;

/**
 * Who asked, and for which shared space. Every run started from the UI carries it, so its
 * escalation and its history belong to that space.
 */
public record RunContext(long spaceId, String startedBy) {
}
