package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.WindowRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule `zone-shift-v1`: derives NORD and SUD windows from the curated CENTRO ones, by one month.
 *
 * <ul>
 *   <li>Spring and summer work (windows starting January to July, and harvests starting March to
 *       September) comes later in the north and earlier in the south: NORD +1, SUD -1.</li>
 *   <li>Autumn sowing and planting (windows starting August to December) comes earlier in the north,
 *       before the cold, and later in the south: NORD -1, SUD +1.</li>
 *   <li>Winter harvests (starting October to February) depend on frost, not on the calendar: not derived.</li>
 *   <li>A curated window for the same crop, zone and activity always wins: nothing is derived then.</li>
 * </ul>
 * Derived windows are never verified; they keep the sources of the window they come from.
 */
public final class ZoneShiftRule {

    public static final String ID = "zone-shift-v1";

    /** A window derived by this rule, with the curated window it comes from. */
    public record InferredWindow(WindowRow window, WindowRow from) {
    }

    public static List<InferredWindow> infer(List<WindowRow> curated) {
        Set<String> declared = curated.stream().map(w -> key(w.cropId(), w.zone(), w.activity()))
                .collect(Collectors.toSet());
        List<InferredWindow> inferred = new ArrayList<>();
        for (WindowRow w : curated.stream().filter(w -> w.zone().equals("CENTRO")).toList()) {
            int northShift = shift(w);
            if (northShift == 0) {
                continue;
            }
            add(inferred, declared, w, "NORD", northShift);
            add(inferred, declared, w, "SUD", -northShift);
        }
        return inferred;
    }

    /** How many months the NORD window moves (SUD moves the other way); 0 means not derived. */
    static int shift(WindowRow w) {
        int start = w.fromMonth();
        if (w.activity().equals("HARVEST")) {
            return start >= 3 && start <= 9 ? 1 : 0;
        }
        return start <= 7 ? 1 : -1;
    }

    private static void add(List<InferredWindow> inferred, Set<String> declared, WindowRow w, String zone, int shift) {
        if (declared.contains(key(w.cropId(), zone, w.activity()))) {
            return;
        }
        inferred.add(new InferredWindow(new WindowRow(w.cropId(), zone, w.activity(), month(w.fromMonth() + shift),
                month(w.toMonth() + shift), w.note(), w.source(), false), w));
    }

    /** 1-12, wrapping over the year. */
    static int month(int m) {
        return ((m - 1) % 12 + 12) % 12 + 1;
    }

    private static String key(String crop, String zone, String activity) {
        return crop + "|" + zone + "|" + activity;
    }

    private ZoneShiftRule() {
    }
}
