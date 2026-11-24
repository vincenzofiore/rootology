package app.rootology.officialwarnings;

import java.util.Map;
import java.util.Optional;

/**
 * Maps a garden's place to the zone of the official MeteoAlarm warnings (#136): the first-level
 * administrative code GeoNames gives (for Italy the region) to MeteoAlarm's {@code EMMA_ID}.
 *
 * <p>The two code lists come from GeoNames' {@code admin1CodesASCII.txt} (Italian regions "01" Abruzzo
 * … "20" Veneto) and MeteoAlarm's Geocodes JSON (2026-11-13). The table changes only if MeteoAlarm
 * changes its zones; it is fixed and covers Italy only.
 */
public final class WarningZones {

    private static final Map<String, String> ITALY = Map.ofEntries(
            Map.entry("01", "IT013"),
            Map.entry("02", "IT017"),
            Map.entry("03", "IT001"),
            Map.entry("04", "IT016"),
            Map.entry("05", "IT008"),
            Map.entry("06", "IT020"),
            Map.entry("07", "IT012"),
            Map.entry("08", "IT007"),
            Map.entry("09", "IT003"),
            Map.entry("10", "IT011"),
            Map.entry("11", "IT014"),
            Map.entry("12", "IT005"),
            Map.entry("13", "IT015"),
            Map.entry("14", "IT019"),
            Map.entry("15", "IT018"),
            Map.entry("16", "IT009"),
            Map.entry("17", "IT002"),
            Map.entry("18", "IT010"),
            Map.entry("19", "IT004"),
            Map.entry("20", "IT006"));

    private WarningZones() {
    }

    /**
     * The MeteoAlarm zone of a place: countryCode is ISO 3166-1 alpha-2, or null for gardens saved before
     * the country was recorded (all in Italy). Empty for any other country or an unknown code.
     */
    public static Optional<String> emmaId(String countryCode, String regionCode) {
        if (regionCode == null || !(countryCode == null || "IT".equals(countryCode))) {
            return Optional.empty();
        }
        return Optional.ofNullable(ITALY.get(regionCode));
    }
}
