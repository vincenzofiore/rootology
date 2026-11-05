/**
 * Personal data (#40, docs/privacy.md): who the controller is, how long personal data is kept,
 * and, built on the other modules, account deletion, data export and the nightly retention job.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Privacy",
        allowedDependencies = {}
)
package io.github.vincenzofiore.rootology.privacy;
