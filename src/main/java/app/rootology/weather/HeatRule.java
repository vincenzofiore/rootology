package app.rootology.weather;

/**
 * heat-v1: a day's forecast maximum air temperature at or above the absolute maximum a crop stands
 * (ECOCROP's "absolute max. temp.", `temp_abs_max` in `crop-needs.csv`, from FAO ECOCROP, CC BY 4.0).
 * Crops without that value are not judged: they are named as not checked, never guessed, as the frost rule
 * does. Days are counted in the garden's time zone. Thresholds approved by the owner on 2026-11-18.
 */
public final class HeatRule {

    public static final String ID = "heat-v1";
    public static final String SOURCE = "fao-ecocrop";

    /** Whether a day this hot is beyond what the crop stands. */
    public static boolean tooHot(double maximum, double absoluteMaximum) {
        return maximum >= absoluteMaximum;
    }

    private HeatRule() {
    }
}
