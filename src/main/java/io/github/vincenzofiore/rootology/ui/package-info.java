@org.springframework.modulith.ApplicationModule(
        displayName = "Experiment UI",
        allowedDependencies = { "experiments", "pipeline", "decision", "history", "spaces", "garden", "facts", "enrichment", "geocoding", "roles", "usage", "notifications", "alerts", "weather", "privacy" }
)
package io.github.vincenzofiore.rootology.ui;
