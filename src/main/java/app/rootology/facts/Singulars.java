package app.rootology.facts;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The singular forms a plural name may have, in Italian and English: questions say "i pomodori" or
 * "tomatoes" while the catalog names "Pomodoro" and "Tomato". Only guesses: a name is looked up with them
 * after its exact form found nothing, and a guess that names nothing in the catalog finds nothing.
 */
final class Singulars {

    /** One ending rule each, applied to every word of a name long enough to carry it ("cavoli neri"). */
    private static final List<UnaryOperator<String>> RULES = List.of(
            w -> replace(w, "che", "ca"),   // zucche -> zucca
            w -> replace(w, "ghe", "ga"),   // lattughe -> lattuga
            w -> replace(w, "i", "o"),      // pomodori -> pomodoro
            w -> replace(w, "i", "e"),      // peperoni -> peperone
            w -> replace(w, "i", "io"),     // finocchi -> finocchio, spinaci -> spinacio
            w -> replace(w, "e", "a"),      // fave -> fava
            w -> replace(w, "ies", "y"),    // strawberries -> strawberry
            w -> replace(w, "oes", "o"),    // tomatoes -> tomato
            w -> replace(w, "es", ""),      // radishes -> radish
            w -> replace(w, "s", ""));      // beans -> bean

    static Set<String> of(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        Set<String> forms = new LinkedHashSet<>();
        for (UnaryOperator<String> rule : RULES) {
            String[] words = lower.split(" ");
            for (int i = 0; i < words.length; i++) {
                words[i] = rule.apply(words[i]);
            }
            String form = String.join(" ", words);
            if (!form.equals(lower)) {
                forms.add(form);
            }
        }
        return forms;
    }

    private static String replace(String word, String ending, String with) {
        return word.length() > ending.length() + 2 && word.endsWith(ending)
                ? word.substring(0, word.length() - ending.length()) + with
                : word;
    }

    private Singulars() {
    }
}
