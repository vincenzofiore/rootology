package io.github.vincenzofiore.rootology.notifications;

import java.util.List;

/**
 * What a Telegram message says (#38), in Italian like the emails. It carries a title, the space and garden
 * names and the link to the app, and nothing else: never the question, the answer, Claude's phrasing or the
 * body of a weather alert, which stay behind the sign-in. Plain text, no formatting: nothing in it is ever
 * read by Telegram as markup.
 */
final class TelegramMessages {

    /** Well under Telegram's 4096 characters, so cutting leaves no message refused. */
    static final int MAX = 4000;

    /** One thing to tell: a sentence without its full stop, and where to read it. */
    record Item(String text, String link) {
    }

    static String connected() {
        return "Rootology è collegato a questo account. Riceverai qui gli avvisi che scegli; puoi "
                + "scollegarlo dalle preferenze di notifica nell’app.";
    }

    static String notLinked() {
        return "Per ricevere le notifiche di Rootology, collega Telegram dalle preferenze di notifica "
                + "nell’app: troverai lì il link da aprire.";
    }

    static String codeExpired() {
        return "Questo codice non è più valido: aprine uno nuovo dalle preferenze di notifica nell’app.";
    }

    static Item waiting(String place, String link) {
        return new Item("una domanda aspetta una risposta in " + place, link);
    }

    static Item answered(String place, String link) {
        return new Item("la tua domanda in " + place + " ha una risposta", link);
    }

    static Item weather(String title, String place, String link) {
        return new Item(title + " (" + place + ")", link);
    }

    /** One message for everything that happened since the last tick, or nothing when there is nothing. */
    static String digest(List<Item> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            Item only = items.getFirst();
            return cut("Rootology — " + only.text() + ".\n" + only.link());
        }
        StringBuilder text = new StringBuilder("Rootology — " + items.size() + " avvisi:");
        int included = 0;
        for (Item item : items) {
            String block = "\n\n• " + item.text() + ".\n" + item.link();
            if (text.length() + block.length() > MAX) {
                break;
            }
            text.append(block);
            included++;
        }
        if (included < items.size()) {
            text.append("\n\n…e altre ").append(items.size() - included).append(" nella app.");
        }
        return cut(text.toString());
    }

    private static String cut(String text) {
        return text.length() <= MAX ? text : text.substring(0, MAX - 1) + "…";
    }

    private TelegramMessages() {
    }
}
