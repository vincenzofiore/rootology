@org.springframework.modulith.ApplicationModule(
        displayName = "Agentic Pipeline",
        allowedDependencies = { "decision", "facts", "garden", "weather", "resilience" }
)
package io.github.vincenzofiore.rootology.pipeline;
