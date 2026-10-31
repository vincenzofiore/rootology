package io.github.vincenzofiore.rootology.pipeline;

/** What happens to an escalated decision. */
public enum ReviewMode {
    /** The escalation is only recorded in the answer. Used by the recordings, which never block. */
    AUTOMATIC,
    /** The agent pauses and waits for a person to answer (see {@link GardenPipeline#respond}). */
    HUMAN
}
