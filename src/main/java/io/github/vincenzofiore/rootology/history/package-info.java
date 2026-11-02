/** Stores finished pipeline runs in Postgres, with the arbiter's answer next to each one. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Run History",
        allowedDependencies = { "pipeline", "experiments", "decision" }
)
package io.github.vincenzofiore.rootology.history;
