package app.rootology.notifications;

/**
 * A change of membership in Italian (#105), for the email digest and Telegram, as the other kinds are written
 * there. The app words the same events in the reader's language from the stored event.
 */
final class MembershipText {

    /** What changed, without who: a subject line, and the whole of a Telegram message. */
    static String subject(String event, String space) {
        if (event != null && event.startsWith("ROLE:")) {
            return "il tuo ruolo in " + space + " è cambiato";
        }
        return switch (event == null ? "" : event) {
            case "BLOCKED" -> "il tuo accesso a " + space + " è bloccato";
            case "UNBLOCKED" -> "il tuo accesso a " + space + " è di nuovo aperto";
            case "LEFT" -> "un membro ha lasciato " + space;
            default -> "un cambiamento in " + space;
        };
    }

    /** Who did what, for the email's body. */
    static String sentence(String event, String actor, String space) {
        if (event != null && event.startsWith("ROLE:")) {
            return actor + " ti ha reso " + role(event.substring(5)) + " in " + space + ".";
        }
        return switch (event == null ? "" : event) {
            case "BLOCKED" -> actor + " ti ha bloccato in " + space + ": non lo vedi più.";
            case "UNBLOCKED" -> actor + " ti ha sbloccato in " + space + ": lo vedi di nuovo.";
            case "LEFT" -> actor + " ha lasciato " + space + ".";
            default -> "Qualcosa è cambiato in " + space + ".";
        };
    }

    private static String role(String role) {
        return switch (role) {
            case "OWNER" -> "proprietario";
            case "GUEST" -> "ospite";
            default -> "membro";
        };
    }

    private MembershipText() {
    }
}
