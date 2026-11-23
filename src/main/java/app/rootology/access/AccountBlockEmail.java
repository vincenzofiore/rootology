package app.rootology.access;

import app.rootology.mail.OutgoingMail;
import org.springframework.web.util.HtmlUtils;

/**
 * The two emails of a block (#165), in Italian like the invitation one: the block says what happened, the
 * reason when the admin wrote one, and how to ask for a review (the controller's contact, or replying when the
 * deployment names none); the unblock says that the account works again.
 */
final class AccountBlockEmail {

    private AccountBlockEmail() {
    }

    static OutgoingMail blocked(String to, String reason, String contact) {
        String why = reason == null ? "" : "Motivo: " + reason + "\n\n";
        String review = contact == null ? "Per chiedere una revisione rispondi a questa email."
                : "Per chiedere una revisione scrivi a " + contact + ".";
        String text = """
                Ciao,

                il tuo account Rootology è stato bloccato da un amministratore: non puoi più accedere all'app.
                I tuoi orti, le tue domande e il tuo storico non sono stati cancellati.

                %s%s

                Se pensi che sia un errore, questa email è il modo per dirlo.
                """.formatted(why, review);
        String whyHtml = reason == null ? "" : "<p>Motivo: <strong>%s</strong></p>"
                .formatted(HtmlUtils.htmlEscape(reason));
        String html = """
                <p>Ciao,</p>
                <p>Il tuo account Rootology è stato bloccato da un amministratore: non puoi più accedere all&#39;app.
                I tuoi orti, le tue domande e il tuo storico non sono stati cancellati.</p>
                %s
                <p>%s</p>
                <p style="color:#666">Se pensi che sia un errore, questa email è il modo per dirlo.</p>
                """.formatted(whyHtml, contact == null
                ? "Per chiedere una revisione rispondi a questa email."
                : "Per chiedere una revisione scrivi a <strong>" + HtmlUtils.htmlEscape(contact) + "</strong>.");
        return new OutgoingMail(to, "Il tuo account Rootology è bloccato", text, html);
    }

    static OutgoingMail unblocked(String to) {
        String text = """
                Ciao,

                il tuo account Rootology è di nuovo attivo: puoi accedere e usare l'app come prima.

                Ci scusiamo per l'attesa.
                """;
        String html = """
                <p>Ciao,</p>
                <p>Il tuo account Rootology è di nuovo attivo: puoi accedere e usare l&#39;app come prima.</p>
                <p style="color:#666">Ci scusiamo per l&#39;attesa.</p>
                """;
        return new OutgoingMail(to, "Il tuo account Rootology è di nuovo attivo", text, html);
    }
}
