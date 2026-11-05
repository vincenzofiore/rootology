/**
 * Outgoing email (#25): plain text and HTML, sent in the background with a few attempts, through SMTP
 * (Mailpit in development, Gmail with an app password for now, a transactional service with a domain later).
 * A failed send is logged and never fails the caller.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Mail")
package io.github.vincenzofiore.rootology.mail;
