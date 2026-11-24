package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.WindowRow;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Rule `pest-shift-v1` (#162): a pest or disease active on a crop follows the crop. The months of
 * `pest-hosts.csv`, written for central Italy, move at a site as that crop's own calendar moves under
 * {@link FrostShiftRule} (`frost-shift-v1`).
 *
 * <p>For one host row (organism, crop, fromMonth, toMonth) and a site:
 * <ol>
 *   <li>the crop's curated CENTRO windows and the windows `frost-shift-v1` derives from them at the site
 *       are taken (each derived window knows the curated one it comes from);</li>
 *   <li>dStart is the derived start minus the curated start of the crop's curated window that starts
 *       earliest in the year, dEnd the derived end minus the curated end of the one that ends latest;
 *       windows whose end is before their start wrap over the new year and are left out. The derived
 *       version of exactly those curated windows is used, and each shift is clamped to [-2, 2];</li>
 *   <li>the row stays as it is when it wraps over the new year, spans 11 months or more, the crop has no
 *       derived window at the site for the window used above, or both shifts are 0 (Vasto and Palermo);</li>
 *   <li>otherwise from' = from + dStart and to' = to + dEnd, each clamped to 1..12, and to' is never
 *       before from', so a row is never empty;</li>
 *   <li>a moved row is inferred by this rule, an unmoved one stays curated.</li>
 * </ol>
 *
 * <p>The site's climate only says how much the crop's own calendar moves: a rule on months, not on
 * temperature (#147).
 */
public final class PestShiftRule {

    public static final String ID = "pest-shift-v1";

    /** A host row as a site has it, with the rule's id when it moved and null when it is curated. */
    public record Shifted(int fromMonth, int toMonth, String derivedBy) {

        public boolean moved() {
            return derivedBy != null;
        }
    }

    /**
     * The months of one host row at a site: the curated row when nothing moves. An empty derived list
     * (no climate profile) leaves every row as it is.
     */
    public static Shifted shift(String cropId, int fromMonth, int toMonth, List<WindowRow> curated,
                                List<FrostShiftRule.InferredWindow> derived) {
        if (fromMonth > toMonth || toMonth - fromMonth + 1 >= 11) {
            return new Shifted(fromMonth, toMonth, null);
        }
        // The crop's own calendar, wrapping windows left out: they are not a start or an end in the year.
        List<WindowRow> own = curated.stream()
                .filter(w -> w.cropId().equals(cropId) && w.zone().equals("CENTRO") && w.toMonth() >= w.fromMonth())
                .toList();
        Optional<WindowRow> earliest = own.stream()
                .min(Comparator.comparingInt(WindowRow::fromMonth));
        Optional<WindowRow> latest = own.stream()
                .max(Comparator.comparingInt(WindowRow::toMonth));
        if (earliest.isEmpty() || latest.isEmpty()) {
            return new Shifted(fromMonth, toMonth, null);
        }
        Integer start = delta(derived, earliest.get(), WindowRow::fromMonth);
        Integer end = delta(derived, latest.get(), WindowRow::toMonth);
        if (start == null || end == null) {
            return new Shifted(fromMonth, toMonth, null);
        }
        int dStart = clamp(start, -2, 2);
        int dEnd = clamp(end, -2, 2);
        if (dStart == 0 && dEnd == 0) {
            return new Shifted(fromMonth, toMonth, null);
        }
        int from = clamp(fromMonth + dStart, 1, 12);
        int to = clamp(toMonth + dEnd, 1, 12);
        // Never an empty row: 9-10 moved by +1 and -1 is October, not an empty range.
        return new Shifted(from, Math.max(from, to), ID);
    }

    /** Whether a host row's months, wrapping over the new year, include the month (1-12). */
    public static boolean covers(int fromMonth, int toMonth, int month) {
        return fromMonth <= toMonth
                ? month >= fromMonth && month <= toMonth
                : month >= fromMonth || month <= toMonth;
    }

    /** How many months the derived version of this curated window moved its start (or its end); null when not derived. */
    private static Integer delta(List<FrostShiftRule.InferredWindow> derived, WindowRow curated,
                                 ToIntFunction<WindowRow> month) {
        return derived.stream().filter(i -> i.from().equals(curated)).findFirst()
                .map(i -> month.applyAsInt(i.window()) - month.applyAsInt(curated)).orElse(null);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private PestShiftRule() {
    }
}
