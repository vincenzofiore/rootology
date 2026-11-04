@org.springframework.modulith.ApplicationModule(
        displayName = "Agentic Pipeline",
        allowedDependencies = { "decision", "facts", "garden", "weather", "resilience", "spaces" }
)
package io.github.vincenzofiore.rootology.pipeline;
