package io.github.vincenzofiore.rootology.pipeline;

/**
 * Who asked, and for which shared space. Every run started from the UI carries it, so its
 * escalation and its history belong to that space.
 */
public record RunContext(long spaceId, String startedBy) {

    /** For runs that belong to no space (the suite's recordings): garden questions cannot be answered. */
    public static final RunContext NONE = new RunContext(0, null);

    public boolean hasSpace() {
        return spaceId > 0 && startedBy != null;
    }
}
