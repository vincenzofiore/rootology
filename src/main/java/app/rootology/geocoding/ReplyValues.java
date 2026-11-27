package app.rootology.geocoding;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * What a GeoNames reply's values mean (#184, #158): a place's own name in the search language, and the zone of
 * an IANA id. Kept out of {@link GeoNames} so the client stays one service, not a god class.
 */
final class ReplyValues {

    /**
     * The place's name in the search language: its preferred alternate name there, else the first one that is
     * not a short form, else its main name. Never the raw "name", which is what happened to match the text
     * typed (#184).
     */
    static String displayName(GeoNames.Entry place, String lang) {
        String alternate = alternateName(place.alternateNames(), lang);
        if (alternate != null) {
            return alternate;
        }
        // A FULL reply always has the main name; a reply without it is malformed and the raw name is the last resort.
        return place.toponymName() == null || place.toponymName().isBlank() ? place.name() : place.toponymName();
    }

    /** The preferred alternate name in the language, else the first one that is not a short; null when none. */
    private static String alternateName(List<GeoNames.AlternateName> names, String lang) {
        String first = null;
        for (GeoNames.AlternateName alternate : names == null ? List.<GeoNames.AlternateName>of() : names) {
            if (alternate.name() == null || !lang.equalsIgnoreCase(alternate.lang())) {
                continue;
            }
            if (Boolean.TRUE.equals(alternate.isPreferredName())) {
                return alternate.name();
            }
            if (first == null && !Boolean.TRUE.equals(alternate.isShortName())) {
                first = alternate.name();
            }
        }
        return first;
    }

    /** The zone of an IANA id, or nothing for an id this JVM does not know: never guessed. */
    static Optional<ZoneId> zone(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(id));
        } catch (java.time.DateTimeException unknown) {
            return Optional.empty();
        }
    }

    private ReplyValues() {
    }
}
