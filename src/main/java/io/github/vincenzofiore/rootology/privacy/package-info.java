/**
 * Personal data (#40, docs/privacy.md): who the controller is, how long personal data is kept,
 * and, built on the other modules, account deletion, data export and the nightly retention job.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Privacy",
        allowedDependencies = { "spaces", "garden", "history", "pipeline", "notifications", "usage", "enrichment", "roles" }
)
package io.github.vincenzofiore.rootology.privacy;
