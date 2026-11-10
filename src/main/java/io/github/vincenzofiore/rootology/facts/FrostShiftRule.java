package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.WindowRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rule `frost-shift-v1` (#62): moves the curated central-Italy windows of Vasto to a site, only where the
 * site's own frost dates make them impossible. A window that cannot be derived is not a window for that
 * site: the question escalates, as for any missing fact.
 *
 * <p>Each crop belongs to a group (`frost_group` in `crops-v1.csv`), and the shift d is in months:
 * <ul>
 *   <li>TENDER: d = clamp(firstSafe - start of the crop's first open-field window, 0, 2), for every window;
 *       the seedbed is used when the crop has no field window.</li>
 *   <li>HARDY: the same with firstSafe - 1.</li>
 *   <li>AUTUMN: d = clamp(lastSafe - 11, -2, 0) for every window (October -1, September or earlier -2).</li>
 *   <li>OVERWINTER: harvests d = clamp(firstSafe - 4, 0, 2); windows starting August or later
 *       d = clamp(lastSafe - 11, -1, 0); other windows d = clamp(firstSafe - 1 - start, 0, 2).</li>
 *   <li>LONG: sowing windows become max(start, firstSafe - 1) .. min(end, lastSafe - 1); harvests start
 *       plus d = max(0, firstSafe - 1 - start of the crop's sowing window) and end at most at lastSafe.</li>
 * </ul>
 * TENDER, HARDY and OVERWINTER harvests end at most at lastSafe. A winter harvest (starting October to
 * February) is not derived when its d is not 0, a window whose end falls before its start is never
 * derived, and year-round windows (11 months or more) stay as they are. Nothing ever moves earlier in
 * spring than Vasto: the clamps above guarantee it.
 */
public final class FrostShiftRule {

    public static final String ID = "frost-shift-v1";

    public static final Set<String> GROUPS = Set.of("TENDER", "HARDY", "AUTUMN", "OVERWINTER", "LONG");

    /** A window derived by this rule, with the curated window it comes from. */
    public record InferredWindow(WindowRow window, WindowRow from) {
    }

    /**
     * The windows of every CENTRO crop at a site. The map is crop id -> frost group; every crop of the
     * curated windows must have one (the validator checks it).
     */
    public static List<InferredWindow> infer(List<WindowRow> curated, Map<String, String> groups, SiteProfile site) {
        List<WindowRow> rows = curated.stream().filter(w -> w.zone().equals("CENTRO")).toList();
        List<InferredWindow> inferred = new ArrayList<>();
        for (WindowRow row : rows) {
            String group = groups.get(row.cropId());
            if (group == null) {
                throw new IllegalStateException("Crop " + row.cropId() + " has no frost group");
            }
            int[] derived = derive(row, rows, group, site);
            if (derived != null) {
                String zone = site.zone() == null ? "GARDEN" : site.zone();
                inferred.add(new InferredWindow(new WindowRow(row.cropId(), zone, row.activity(),
                        month(derived[0]), month(derived[1]), row.note(), row.source(), false), row));
            }
        }
        return inferred;
    }

    /** The derived window as unwrapped months (from, to) or null when it is not derived. */
    private static int[] derive(WindowRow row, List<WindowRow> rows, String group, SiteProfile site) {
        int from = row.fromMonth();
        int to = row.toMonth();
        if (to < from) {
            to += 12;
        }
        boolean harvest = row.activity().equals("HARVEST");
        if (to - from >= 11) {
            return new int[]{from, to};
        }
        List<WindowRow> chain = rows.stream()
                .filter(w -> w.cropId().equals(row.cropId()) && !w.activity().equals("HARVEST")).toList();
        int firstSafe = site.firstSafeMonth();
        int lastSafe = site.lastSafeMonth();
        int d;
        if (group.equals("TENDER") || group.equals("HARDY")) {
            List<WindowRow> field = chain.stream().filter(w -> !w.activity().equals("SOW_SEEDBED")).toList();
            if (field.isEmpty()) {
                field = chain;
            }
            int start = field.stream().mapToInt(WindowRow::fromMonth).min().orElse(from);
            int need = group.equals("TENDER") ? firstSafe : firstSafe - 1;
            d = clamp(need - start, 0, 2);
        } else if (group.equals("AUTUMN")) {
            d = clamp(lastSafe - 11, -2, 0);
        } else if (group.equals("OVERWINTER")) {
            if (harvest) {
                d = clamp(firstSafe - 4, 0, 2);
            } else if (from >= 8) {
                d = clamp(lastSafe - 11, -1, 0);
            } else {
                d = clamp(firstSafe - 1 - from, 0, 2);
            }
        } else {
            // LONG: the sowing starts after firstSafe - 1 and ends a month before the frost month; the
            // harvest starts with the sowing and ends by the frost month.
            if (harvest) {
                WindowRow sowing = chain.getFirst();
                int shift = Math.max(0, firstSafe - 1 - sowing.fromMonth());
                int start = from + shift;
                int end = Math.min(to, lastSafe);
                return end >= start ? new int[]{start, end} : null;
            }
            int start = Math.max(from, firstSafe - 1);
            int end = Math.min(to, lastSafe - 1);
            return end >= start ? new int[]{start, end} : null;
        }
        if (d != 0 && harvest && (from >= 10 || from <= 2)) {
            return null;
        }
        int start = from + d;
        int end = to + d;
        if (harvest && !group.equals("AUTUMN")) {
            end = Math.min(end, to <= 12 ? lastSafe : lastSafe + 12);
        }
        return end >= start ? new int[]{start, end} : null;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    /** 1-12, wrapping over the year. */
    static int month(int value) {
        return ((value - 1) % 12 + 12) % 12 + 1;
    }

    private FrostShiftRule() {
    }
}
