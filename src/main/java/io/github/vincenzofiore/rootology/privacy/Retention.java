package io.github.vincenzofiore.rootology.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * How long personal data is kept (`rootology.retention`, docs/privacy.md). The same values are applied
 * by the nightly job and shown on the privacy page, so the page cannot drift from what happens.
 */
@ConfigurationProperties("rootology.retention")
public record Retention(
        /** Finished question runs. */
        Duration history,
        /** Usage records. */
        Duration usage,
        /** Escalations once answered. */
        Duration escalations,
        /** Notifications once read or resolved. */
        Duration notifications,
        /** Invitations once accepted, revoked or expired. */
        Duration invitations,
        /** Telegram one-time codes, and links Telegram stopped delivering to (#38). */
        Duration telegram) {
}
