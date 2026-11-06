/**
 * Operational alerts (#27): emails to the admins when an external service keeps failing (its circuit is open,
 * #29) or the server's disk is nearly full, once per event. Addresses come from the admins' notification
 * preferences (#26); quiet hours do not apply, since these need someone soon.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Operations",
        allowedDependencies = { "resilience", "roles", "notifications", "mail" }
)
package io.github.vincenzofiore.rootology.operations;
