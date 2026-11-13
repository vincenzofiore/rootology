package app.rootology.facts;

import java.time.LocalDate;
import java.util.List;

/**
 * Rule `frost-season-v1` (#62): the two safe months of a site from its AgERA5 frost dates, after the
 * altitude correction. No hidden magic: the correction is 0.15 °C per day, 6.5 °C per 1000 m, capped at
 * 500 m; a garden on the coast (a cell with less than 90 % land, below its altitude) and a garden whose
 * altitude is not known keep the cell's dates as they are. Frost-free dates are never shifted.
 *
 * <p>The first safe month is the month of the last frost, or the next month when the frost is after the
 * 15th, and January when there is no last frost. The last safe month is the month of the first frost, or
 * the previous month when the frost is on or before the 15th, and December when there is no first frost.
 */
public final class FrostSeasonRule {

    public static final String ID = "frost-season-v1";

    public static final String AGERA5 = "agera5";
    public static final String DEM = "copernicus-dem-90m";
    public static final String GTOPO30 = "geonames-gtopo30";

    /** 6.5 °C per 1000 m, and 0.15 °C per day of the frost season. */
    static final double LAPSE_RATE = 6.5 / 1000.0;
    static final double DEGREES_PER_DAY = 0.15;
    static final int MAX_ALTITUDE_DIFFERENCE = 500;
    /** Below this land fraction a cell whose elevation is above the garden is a coast, not a hill. */
    static final double COAST_LAND_FRACTION = 0.9;

    /** The profile of a garden: the altitude corrects the cell's dates when it is known. */
    public static SiteProfile profile(ClimateCells.Cell cell, Integer altitudeM, String zone) {
        Integer altitude = altitudeM == null || altitudeM == -9999 ? null : altitudeM;
        SiteProfile.Correction correction;
        double days = 0;
        if (altitude == null) {
            correction = SiteProfile.Correction.MISSING;
        } else if (altitude < cell.elevationM() - 100 && cell.landFraction() < COAST_LAND_FRACTION) {
            correction = SiteProfile.Correction.COAST;
        } else {
            int difference = altitude - cell.elevationM();
            boolean capped = Math.abs(difference) > MAX_ALTITUDE_DIFFERENCE;
            difference = Math.max(-MAX_ALTITUDE_DIFFERENCE, Math.min(MAX_ALTITUDE_DIFFERENCE, difference));
            days = difference * LAPSE_RATE / DEGREES_PER_DAY;
            correction = capped ? SiteProfile.Correction.CAPPED
                    : days == 0 ? SiteProfile.Correction.NONE : SiteProfile.Correction.APPLIED;
        }
        List<String> sources = new java.util.ArrayList<>(List.of(AGERA5, DEM));
        if (altitude != null && correction != SiteProfile.Correction.COAST) {
            sources.add(GTOPO30);
        }
        return season(cell, zone, altitude, correction, days, sources);
    }

    /** The profile of a reference site named in the question: the cell's values, with no altitude correction. */
    public static SiteProfile reference(ClimateCells.Cell cell, String zone) {
        return season(cell, zone, null, SiteProfile.Correction.NONE, 0, List.of(AGERA5, DEM));
    }

    private static SiteProfile season(ClimateCells.Cell cell, String zone, Integer altitude,
                                      SiteProfile.Correction correction, double days, List<String> sources) {
        Integer last = cell.lastFrostP80() == null ? null
                : (int) Math.round(Math.max(1, cell.lastFrostP80() + days));
        Integer first = cell.firstFrostP20() == null ? null
                : (int) Math.round(Math.min(365, cell.firstFrostP20() - days));
        return new SiteProfile(zone, cell.latitude(), cell.longitude(), cell.elevationM(), altitude,
                cell.landFraction(), last, first, firstSafeMonth(last), lastSafeMonth(first), correction, sources);
    }

    /** The month of the last frost, or the next month when it is after the 15th; January without a last frost. */
    static int firstSafeMonth(Integer lastFrostDay) {
        if (lastFrostDay == null) {
            return 1;
        }
        LocalDate date = LocalDate.of(2025, 1, 1).plusDays(lastFrostDay - 1L);
        int month = date.getMonthValue() + (date.getDayOfMonth() > 15 ? 1 : 0);
        return month > 12 ? 1 : month;
    }

    /** The month of the first frost, or the previous month when it is on or before the 15th; December without. */
    static int lastSafeMonth(Integer firstFrostDay) {
        if (firstFrostDay == null) {
            return 12;
        }
        LocalDate date = LocalDate.of(2025, 1, 1).plusDays(firstFrostDay - 1L);
        int month = date.getMonthValue() - (date.getDayOfMonth() <= 15 ? 1 : 0);
        return month < 1 ? 12 : month;
    }

    private FrostSeasonRule() {
    }
}
