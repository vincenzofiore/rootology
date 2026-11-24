package app.rootology.alerts;

import app.rootology.officialwarnings.OfficialWarning;

import java.util.List;
import java.util.Map;

/**
 * The one line about MeteoAlarm's official warnings in the morning alert (#168): how many are active, the
 * highest level with its phenomenon, the source and where the detail is. It never repeats or rewrites a
 * warning's own text: the garden home's dialog (#176) shows it unchanged, with the issuer, the time of issue,
 * the link and the disclaimer.
 */
final class OfficialWarningLine {

    /** MeteoAlarm's {@code awareness_type} to the phenomenon's Italian name. */
    private static final Map<Integer, String> PHENOMENA = Map.ofEntries(
            Map.entry(1, "vento"),
            Map.entry(2, "neve o ghiaccio"),
            Map.entry(3, "temporali"),
            Map.entry(4, "nebbia"),
            Map.entry(5, "temperature alte"),
            Map.entry(6, "temperature basse"),
            Map.entry(7, "mareggiate"),
            Map.entry(8, "incendi boschivi"),
            Map.entry(9, "valanghe"),
            Map.entry(10, "pioggia"),
            Map.entry(12, "alluvioni"),
            Map.entry(13, "pioggia e alluvioni"));

    /** MeteoAlarm's {@code awareness_level} to the level's Italian name; green, 1, is no warning. */
    private static final Map<Integer, String> LEVELS = Map.of(2, "gialla", 3, "arancione", 4, "rossa");

    private OfficialWarningLine() {
    }

    /** {@code Allerte ufficiali in Abruzzo}, with the region's name as the feed writes it. */
    static String title(List<OfficialWarning> warnings) {
        String region = warnings.stream().map(OfficialWarning::areaDesc)
                .filter(area -> area != null && !area.isBlank()).findFirst().orElse(warnings.getFirst().zone());
        return "Allerte ufficiali in " + region;
    }

    /** The count, the highest level and its phenomenon, the source and where the detail is. */
    static String body(List<OfficialWarning> warnings) {
        OfficialWarning highest = highest(warnings);
        String count = warnings.size() == 1 ? "1 allerta attiva" : warnings.size() + " allerte attive";
        return count + ", la più alta " + LEVELS.getOrDefault(highest.level(), "segnalata")
                + " (" + PHENOMENA.getOrDefault(highest.type(), "altro") + "). "
                + "Fonte: MeteoAlarm, Servizio Meteorologico dell’Aeronautica Militare. "
                + "Il dettaglio è nella pagina dell’orto.";
    }

    /** The warning with the highest level; among equals the first, which {@code active} puts first by onset. */
    private static OfficialWarning highest(List<OfficialWarning> warnings) {
        OfficialWarning highest = warnings.getFirst();
        for (OfficialWarning warning : warnings) {
            if (warning.level() > highest.level()) {
                highest = warning;
            }
        }
        return highest;
    }
}
