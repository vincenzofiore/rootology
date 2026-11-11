/**
 * Invitation requests (#65): someone signed in with GitHub or Google but not admitted asks for access, an
 * admin approves or rejects it, and the admins are notified through the notifications outbox. Nothing of a
 * person is written before they are admitted: a request is keyed by the identity the provider gave them.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Access requests",
        allowedDependencies = { "spaces" }
)
package io.github.vincenzofiore.rootology.access;
