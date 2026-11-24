package app.rootology.officialwarnings;

import java.util.Optional;

/**
 * The part of the region an official warning is about, as the Italian CAP description of MeteoAlarm names it
 * (#171): the text after one of the two fixed opening sentences, before the disclaimer, for example
 * {@code APPENNINO E COSTE} or {@code >800 LOCALMENTE 600 METRI ALPI}. The documents give no values, only this
 * hint, and it tells whether a warning concerns the garden (a coastal garden and a warning for the Apennines).
 *
 * <p>Pure and fixed: only the two openings MeteoAlarm uses give an area, any other text (another wording, another
 * language, nothing) gives none, so the area is never guessed.
 */
public final class OfficialWarningArea {

    private static final String MODERATE =
            "Sono previsti fenomeni meteorologici di moderata intensità";
    private static final String INTENSE = "Sono previsti fenomeni meteorologici intensi";
    private static final String DISCLAIMER = "(DISCLAIMER";

    private OfficialWarningArea() {
    }

    /**
     * The area of a warning's Italian description, as the issuer wrote it, or empty when the description does not
     * open with one of the two known sentences or names no area after it.
     */
    public static Optional<String> of(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String text = description;
        int disclaimer = text.indexOf(DISCLAIMER);
        if (disclaimer >= 0) {
            text = text.substring(0, disclaimer);
        }
        text = text.replaceAll("\\s+", " ").trim();
        String rest;
        if (text.startsWith(MODERATE)) {
            rest = text.substring(MODERATE.length());
        } else if (text.startsWith(INTENSE)) {
            rest = text.substring(INTENSE.length());
        } else {
            return Optional.empty();
        }
        rest = rest.trim();
        return rest.isEmpty() ? Optional.empty() : Optional.of(rest);
    }
}