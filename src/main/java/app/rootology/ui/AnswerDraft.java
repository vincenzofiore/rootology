package app.rootology.ui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The answer being prepared on a waiting question (#86): the values proposed by the decision layer as chips
 * the person keeps or removes, plus the ones they type. The order is the order they were added, duplicates are
 * dropped, and an explicit "none" answer wins over every value, so it is never a half-finished list.
 */
final class AnswerDraft {

    private final Set<String> values = new LinkedHashSet<>();
    private boolean none;

    AnswerDraft(List<String> proposed) {
        if (proposed != null) {
            proposed.forEach(this::add);
        }
    }

    /** Adds one value, or the comma-separated values a person typed; blank ones are ignored. */
    void add(String text) {
        if (text == null) {
            return;
        }
        for (String part : text.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
    }

    void remove(String value) {
        values.remove(value);
    }

    /** An explicit "none": the answer has no values, whatever the proposed ones were. */
    void none(boolean none) {
        this.none = none;
    }

    boolean none() {
        return none;
    }

    List<String> values() {
        return List.copyOf(values);
    }

    /** What {@code GardenPipeline.respond} receives: no values at all when the person said "none". */
    List<String> answer() {
        return none ? List.of() : values();
    }
}
