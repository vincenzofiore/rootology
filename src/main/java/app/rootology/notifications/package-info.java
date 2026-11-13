/**
 * Notifications (#26): listens to what needs people (first, a question waiting in a space), stores one
 * notification per recipient in Postgres, and delivers it in the app (live, through Vaadin Push in the UI)
 * and by email, grouped and outside each person's quiet hours. The stored rows are the email outbox, and the
 * same rows also go to Telegram (#38): promptly, one message per person, with the place and a link.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Notifications",
        allowedDependencies = { "pipeline", "spaces", "roles", "access", "mail", "resilience" }
)
package app.rootology.notifications;
