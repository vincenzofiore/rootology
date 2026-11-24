package app.rootology.notifications;

import app.rootology.access.AccountFlagged;

/**
 * What an account flag says outside the admin page (#165), in Italian like the emails and Telegram, where the
 * page words it in its reader's language. Plain text: the login and the counts, never a question.
 */
final class AccountFlagText {

    /** The line the email and Telegram carry, as the flag's own body: "test: 7 domande su 8 fuori tema, ...". */
    static String line(AccountFlagged flag) {
        return line(flag.login(), flag.judgedQuestions(), flag.offDomainQuestions(), flag.daysWithoutGarden());
    }

    static String line(String login, int judged, int offDomain, int days) {
        return login + ": " + (offDomain == 1 ? "1 domanda" : offDomain + " domande") + " su " + judged
                + " fuori tema, nessun orto da " + days + (days == 1 ? " giorno" : " giorni");
    }

    /** The subject of an email that carries only flags. */
    static String subject(int flags) {
        return flags == 1 ? "un account segnalato" : flags + " account segnalati";
    }

    private AccountFlagText() {
    }
}
