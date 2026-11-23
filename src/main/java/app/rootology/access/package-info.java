/**
 * Access (#65, #165): invitation requests from people who are not users yet, and whole accounts an admin
 * blocks. A request is keyed by the identity the provider gave the person, since nothing of them is written
 * before they are admitted; a block is a state of their account, which keeps everything it had and only loses
 * the app. Nobody decides on their own access: an admin does, and the person is told how to ask for a review.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Access",
        allowedDependencies = { "spaces", "roles", "mail" }
)
package app.rootology.access;
