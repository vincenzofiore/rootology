package app.rootology.access;

import java.time.Instant;

/**
 * An account flag as an admin reads it (#165): the counts that raised it and the decision, if there is one.
 * It carries the login, never a question: the texts of the notification do the same.
 */
public record AccountFlagView(long id, String login, Instant flaggedAt, int judgedQuestions, int offDomainQuestions,
                              int daysWithoutGarden, AccountFlagStatus status, String decidedBy, Instant decidedAt) {
}
