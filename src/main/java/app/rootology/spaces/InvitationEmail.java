package app.rootology.spaces;

import app.rootology.mail.OutgoingMail;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The invitation email (#25), in Italian, as plain text and HTML. */
final class InvitationEmail {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)
            .withZone(ZoneId.of("Europe/Rome"));

    static OutgoingMail of(String to, String spaceName, Role role, String invitedBy, String link, Instant expires) {
        String as = switch (role) {
            case OWNER -> "proprietario";
            case MEMBER -> "membro";
            case GUEST -> "ospite";
        };
        String until = DAY.format(expires);
        String subject = "Invito a \"" + spaceName + "\" su Rootology";
        String text = """
                Ciao,

                %s ti invita a unirti a "%s" su Rootology come %s.

                Apri questo link e accedi con GitHub, usando un account su cui questo indirizzo email è verificato:
                %s

                Il link vale una sola volta, fino al %s.

                Se non aspettavi questo invito, puoi ignorare questa email.
                """.formatted(invitedBy, spaceName, as, link, until);
        String html = """
                <p>Ciao,</p>
                <p><strong>%s</strong> ti invita a unirti a <strong>%s</strong> su Rootology come %s.</p>
                <p><a href="%s">Accetta l'invito</a></p>
                <p>Accedi con GitHub, usando un account su cui questo indirizzo email è verificato.
                Il link vale una sola volta, fino al %s.</p>
                <p style="color:#666">Se non aspettavi questo invito, puoi ignorare questa email.</p>
                """.formatted(HtmlUtils.htmlEscape(invitedBy), HtmlUtils.htmlEscape(spaceName), as,
                HtmlUtils.htmlEscape(link), until);
        return new OutgoingMail(to, subject, text, html);
    }

    private InvitationEmail() {
    }
}
