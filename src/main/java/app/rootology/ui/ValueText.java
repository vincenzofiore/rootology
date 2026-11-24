package app.rootology.ui;

import app.rootology.facts.Crop;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.OrganismCatalog;
import app.rootology.facts.OrganismCatalog.Organism;
import com.vaadin.flow.component.Component;

import java.text.Normalizer;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One answer value in the page's language (#110): a month becomes "agosto" or "August", a crop or an organism
 * gets the name the curated files give it in that language, and anything else (a reviewer's own word, an id the
 * knowledge base does not know) stays exactly as written. It never guesses.
 */
final class ValueText {

    private static final CropCatalog CROPS = new CropCatalog();
    private static final OrganismCatalog ORGANISMS = new OrganismCatalog();

    static String of(String value, Component in) {
        return of(value, NoteText.locale(in));
    }

    static String of(String value, Locale locale) {
        if (value == null || value.isBlank()) {
            return value;
        }
        Locale language = localeText(locale);
        Month month = month(value);
        if (month != null) {
            return month.getDisplayName(TextStyle.FULL_STANDALONE, language);
        }
        boolean english = language == Locale.ENGLISH;
        for (Crop crop : CROPS.all()) {
            if (matches(value, crop.id(), crop.italianName(), crop.englishName())) {
                return english ? crop.englishName() : crop.italianName();
            }
        }
        for (Organism organism : ORGANISMS.all()) {
            if (matches(value, organism.id(), organism.italianName(), organism.englishName())) {
                return english ? organism.englishName() : organism.italianName();
            }
        }
        return value;
    }

    /** Whether a value is worth a badge: a control method is a sentence the answer's text already says (#140). */
    static boolean badge(String value) {
        return !ORGANISMS.isControlMethod(value);
    }

    /**
     * Whether a sentence already says a word, as a whole word and ignoring case and accents: "agosto" is in
     * "da agosto a ottobre", while "rapa" is not in "rapanello".
     */
    static boolean says(String sentence, String word) {
        if (sentence == null || word == null || word.isBlank()) {
            return false;
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(plain(word.trim()))
                + "(?![\\p{L}\\p{N}])").matcher(plain(sentence)).find();
    }

    static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** The page's two languages, Italian for anything else, as the rest of the UI does (#31). */
    private static Locale localeText(Locale locale) {
        return locale != null && locale.getLanguage().equals("en") ? Locale.ENGLISH : Locale.ITALIAN;
    }

    /** A month as the pipeline writes it (an enum name such as AUGUST); any other word is no month. */
    private static Month month(String value) {
        try {
            return Month.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matches(String value, String... names) {
        String word = plain(value.trim());
        for (String name : names) {
            if (name != null && !name.isBlank() && plain(name).equals(word)) {
                return true;
            }
        }
        return false;
    }

    private ValueText() {
    }
}
