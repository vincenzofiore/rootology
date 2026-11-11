package app.rootology.pipeline;

/** Which graph the pipeline reads its facts from. */
public enum FactScope {
    /** The quality suite's closed dataset: experiment cases, comparisons and recordings. */
    SUITE,
    /** The curated knowledge base: real questions from people. */
    KNOWLEDGE_BASE
}
