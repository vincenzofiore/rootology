package app.rootology.garden;

import java.util.Locale;
import java.util.Optional;

/**
 * light-need-v1: a crop needs more light than a plot can give when ECOCROP's light optimum asks for more
 * direct sun than the gardener declared for that plot. The mapping from ECOCROP's words to the least light
 * a plot must get is explicit: "very bright" wants full sun, "clear skies" and "cloudy skies" full sun or
 * part shade (no vegetable of the list grows well in full shade). Anything else is unknown, so it is never
 * guessed.
 */
public final class LightRule {

    public static final String ID = "light-need-v1";

    /** The source ids of an inferred light warning: the plot is declared by the gardener, the need is ECOCROP's. */
    public static final String GARDEN_SOURCE = "garden";
    public static final String ECOCROP_SOURCE = "fao-ecocrop";

    /** The least direct sun that satisfies an ECOCROP light optimum; empty when the value is not mapped. */
    public static Optional<DirectSun> needs(String ecocropLight) {
        return switch (ecocropLight == null ? "" : ecocropLight.trim().toLowerCase(Locale.ROOT)) {
            case "very bright" -> Optional.of(DirectSun.FULL_SUN);
            case "clear skies" -> Optional.of(DirectSun.PART_SHADE);
            case "cloudy skies" -> Optional.of(DirectSun.PART_SHADE);
            default -> Optional.empty();
        };
    }

    /** True when the plot's direct sun gives less light than the crop needs. */
    public static boolean tooDark(DirectSun plot, DirectSun needs) {
        return plot.ordinal() > needs.ordinal();
    }

    private LightRule() {
    }

    /** Hours of direct sun that make full sun, and part shade (#89); fewer is shade. */
    public static final int FULL_SUN_HOURS = 6;
    public static final int PART_SHADE_HOURS = 3;

    /** The level of a number of hours of direct sun a day (#89): 6 or more full sun, 3 to 5 part shade, else shade. */
    public static DirectSun fromHours(int hours) {
        return hours >= FULL_SUN_HOURS ? DirectSun.FULL_SUN
                : hours >= PART_SHADE_HOURS ? DirectSun.PART_SHADE : DirectSun.SHADE;
    }

    /** The summer half of the year for the sun hours (#89): April to September; October to March is winter. */
    public static boolean isSummer(java.time.Month month) {
        return month.getValue() >= 4 && month.getValue() <= 9;
    }
}
