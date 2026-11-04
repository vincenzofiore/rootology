@org.springframework.modulith.ApplicationModule(
        displayName = "Agentic Pipeline",
        allowedDependencies = { "decision", "facts", "garden", "weather", "resilience", "spaces", "usage" }
)
package io.github.vincenzofiore.rootology.pipeline;
