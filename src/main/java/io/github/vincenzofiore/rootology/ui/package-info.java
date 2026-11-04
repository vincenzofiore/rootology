@org.springframework.modulith.ApplicationModule(
        displayName = "Experiment UI",
        allowedDependencies = { "experiments", "pipeline", "decision", "history", "spaces", "garden", "facts", "enrichment" }
)
package io.github.vincenzofiore.rootology.ui;
