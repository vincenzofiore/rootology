package io.github.vincenzofiore.rootology.decision;

/** The question shapes covered by the experiment cases. */
public enum QueryKind {
    /** easy-1, hard-3: declared companions of a plant. */
    COMPANIONS,
    /** easy-2: pests active in a season, minus those the plant repels. */
    RELEVANT_PESTS,
    /** hard-1: companions of other plants that repel a pest the subject repels. */
    COMPANIONS_OF_SHARED_REPELLERS,
    /** hard-2: declared active seasons of a pest. */
    ACTIVE_SEASONS,
    /** Knowledge base: declared antagonists of a crop (what not to plant next to it). */
    ANTAGONISTS,
    /** Knowledge base: months to sow or plant a crop, in a zone. */
    SOWING_TIME,
    /** Knowledge base: months to harvest a crop, in a zone. */
    HARVEST_TIME,
    /** Knowledge base: crops that can be sown or planted in a month (now by default), in a zone. */
    SOW_NOW,
    /** My garden: crops growing now in the asking person's space. */
    GROWING_NOW,
    /** My garden: pests and diseases that threaten the crops growing now, in a month (now by default). */
    PEST_RISK,
    /** My garden: crops that can be sown in a plot (the subject) in a month, minus those against rotation. */
    SOW_IN_PLOT,
    /** Anything that does not fit the shapes above. Always escalated. */
    OTHER
}
