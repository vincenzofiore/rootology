package app.rootology.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The suggested questions of the Ask page (#82): a few chips instead of a paragraph of instructions. Each chip
 * is a question of a kind the pipeline answers (sow now, a crop's pests this month, its harvest, its companions,
 * when to sow it, what to sow in a named plot), so a click never ends in an escalation only because of its
 * wording. The season chooses the crops of the general questions; inside a garden the crops growing there and
 * one of its plots add their own. A guest gets none, since they cannot ask (#33), and the page does not build them.
 */
final class SuggestedQuestions {

    /** How many chips a page shows at most: enough to help, few enough to read at a glance. */
    static final int MAX = 5;

    /** How many crops the crop questions talk about: the ones growing now, in the order they were planted. */
    static final int MAX_CROPS = 2;

    /** One chip: the `i18n` key of its text and the values that text takes (a crop's or plot's name). */
    record Suggestion(String key, List<Object> arguments) {

        static Suggestion of(String key) {
            return new Suggestion(key, List.of());
        }

        /** The text of the chip in the page's language. */
        String text(NoteText.Texts texts) {
            return texts.get(key, arguments.toArray());
        }
    }

    /**
     * The chips of a page visit, in the order they are shown: what to sow now; in a garden, the pests of the
     * first crop growing, the harvest of the second and what to sow in one of its plots; then the season's
     * companion and sowing-time questions, about a crop of the season.
     *
     * @param plot the name of one plot of the garden, or null when it has none (or at the space level)
     */
    static List<Suggestion> of(int month, boolean inGarden, List<String> cropsGrowing, String plot) {
        List<Suggestion> chips = new ArrayList<>();
        chips.add(Suggestion.of("ask.suggest.sow"));
        if (inGarden) {
            List<String> crops = cropsGrowing.stream().filter(c -> c != null && !c.isBlank())
                    .distinct().limit(MAX_CROPS).toList();
            if (!crops.isEmpty()) {
                chips.add(new Suggestion("ask.suggest.crop.pests", List.of(crops.get(0))));
            }
            if (crops.size() > 1) {
                chips.add(new Suggestion("ask.suggest.crop.harvest", List.of(crops.get(1))));
            }
            if (plot != null && !plot.isBlank()) {
                chips.add(new Suggestion("ask.suggest.plot", List.of(plot)));
            }
        }
        String season = season(month);
        chips.add(Suggestion.of("ask.suggest.companions." + season));
        chips.add(Suggestion.of("ask.suggest.sowing." + season));
        return chips.size() > MAX ? chips.subList(0, MAX) : chips;
    }

    /** The season of a month, as the suffix of the seasonal keys. */
    static String season(int month) {
        return switch (month) {
            case 3, 4, 5 -> "spring";
            case 6, 7, 8 -> "summer";
            case 9, 10, 11 -> "autumn";
            default -> "winter";
        };
    }

    private SuggestedQuestions() {
    }
}
