package io.github.vincenzofiore.rootology.mail;

/** One email to one person, in plain text and HTML. */
public record OutgoingMail(String to, String subject, String text, String html) {
}
