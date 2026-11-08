package io.github.vincenzofiore.rootology.pipeline;

/**
 * Who asked, for which shared space, and in which of its gardens (#56). Every run started from the UI
 * carries it, so its escalation and its history belong to that space and garden. The garden id is 0 for
 * a question asked at the space level (no garden), and for runs that belong to no space.
 */
public record RunContext(long spaceId, long gardenId, String startedBy) {

    /** For runs that belong to no space (the suite's recordings): garden questions cannot be answered. */
    public static final RunContext NONE = new RunContext(0, 0, null);

    public boolean hasSpace() {
        return spaceId > 0 && startedBy != null;
    }

    /** True inside a garden; false for the space level, where the rows without a garden live. */
    public boolean hasGarden() {
        return gardenId > 0;
    }
}
