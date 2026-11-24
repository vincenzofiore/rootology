package app.rootology.access;

/**
 * A new flag (#165). It carries the counts that made it, so the admins are told in the app, by email and by
 * Telegram without a single question text leaving the pipeline: the login, how many questions were judged, how
 * many of them were off-domain, and for how long the account has had no garden.
 */
public record AccountFlagged(String login, int judgedQuestions, int offDomainQuestions, int daysWithoutGarden) {
}
