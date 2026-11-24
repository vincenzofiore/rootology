package app.rootology.access;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The nightly signal that flags an account creating only useless traffic (#165). Both must hold: the account
 * signed in at least {@code daysWithoutGarden} days ago and is a member of no space with a garden, and at least
 * {@code minJudged} of its last {@code judgedQuestions} questions were judged by the domain filter, with a share
 * of off-domain ones of at least {@code offDomainShare}. The settings are validated at startup.
 */
@ConfigurationProperties("rootology.suspicious")
public record SuspiciousAccounts(int daysWithoutGarden, int judgedQuestions, int minJudged, double offDomainShare) {

    public SuspiciousAccounts {
        if (daysWithoutGarden < 1) {
            throw new IllegalArgumentException("rootology.suspicious.days-without-garden is at least 1");
        }
        if (judgedQuestions < 1 || minJudged < 1 || minJudged > judgedQuestions) {
            throw new IllegalArgumentException("rootology.suspicious needs 1 <= min-judged <= judged-questions");
        }
        if (offDomainShare <= 0 || offDomainShare > 1) {
            throw new IllegalArgumentException("rootology.suspicious.off-domain-share is above 0 and at most 1");
        }
    }
}
