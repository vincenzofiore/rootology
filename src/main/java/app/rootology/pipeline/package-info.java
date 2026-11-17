@org.springframework.modulith.ApplicationModule(
        displayName = "Agentic Pipeline",
        allowedDependencies = { "decision", "facts", "garden", "geocoding", "weather", "resilience", "spaces",
        "usage" }
)
package app.rootology.pipeline;
