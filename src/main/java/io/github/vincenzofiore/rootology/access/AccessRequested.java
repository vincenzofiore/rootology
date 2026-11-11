package io.github.vincenzofiore.rootology.access;

/**
 * A new invitation request (#65). It carries nothing: the admins are told that something waits, in the app
 * and by email and Telegram, and the request itself stays behind the admin role.
 */
public record AccessRequested() {
}
