package app.rootology.ui;

import app.rootology.facts.FrostSuitabilityRule;
import app.rootology.garden.CropProposalService.CropChecks;
import app.rootology.garden.RotationRule;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.AbstractIcon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The crop's category and the checks of the planting dialog (#185): one icon per check that applies, each with
 * its sentence as tooltip and as what a screen reader says, in the positive colour for something good, the
 * warning colour for a warning and the neutral one for what is not known. They come from
 * {@link CropChecks}, the same facts the proposals use, and they never block the save.
 */
final class PlantingChecks {

    private PlantingChecks() {
    }

    /** The category and the checks; the sentences are named in the page's language by texts and cropName. */
    static Div of(CropChecks checks, NoteText.Texts texts, Function<String, String> cropName) {
        Div row = new Div(category(checks, texts));
        row.addClassName("rootology-planting-checks");
        Stream.of(companions(checks, texts, cropName), antagonists(checks, texts, cropName),
                        rotation(checks, texts, cropName), light(checks, texts, cropName),
                        window(checks, texts, cropName), frost(checks, texts, cropName))
                .flatMap(Optional::stream).forEach(row::add);
        return row;
    }

    /** The family and the use: the two facts of the knowledge base a person sees before saving (#185). */
    private static Span category(CropChecks checks, NoteText.Texts texts) {
        Span category = new Span(texts.get("garden.category", checks.family(),
                texts.get(checks.service() ? "crop.use.SERVICE" : "crop.use.FOOD")));
        category.addClassName("rootology-planting-category");
        return category;
    }

    /** A good companion already growing in the area, named. */
    private static Optional<Component> companions(CropChecks checks, NoteText.Texts texts,
                                                  Function<String, String> cropName) {
        return present(checks.companionsInArea(), cropIds -> check(VaadinIcon.CHECK_CIRCLE_O.create(),
                texts.get("garden.check.companion", names(cropIds, cropName)), "good", texts.get("garden.check.short.companion")));
    }

    /** An antagonist already growing in the area, named. */
    private static Optional<Component> antagonists(CropChecks checks, NoteText.Texts texts,
                                                   Function<String, String> cropName) {
        return present(checks.antagonistsInArea(), cropIds -> check(VaadinIcon.WARNING.create(),
                texts.get("garden.check.antagonist", names(cropIds, cropName)), "warning", texts.get("garden.check.short.antagonist")));
    }

    /** The same family as the crop before it in the area (`family-rotation-v1`). */
    private static Optional<Component> rotation(CropChecks checks, NoteText.Texts texts,
                                                Function<String, String> cropName) {
        RotationRule.RotationWarning rotation = checks.rotation();
        return rotation == null ? Optional.empty()
                : Optional.of(check(VaadinIcon.REFRESH.create(), texts.get("garden.check.rotation",
                        cropName.apply(rotation.previous().cropId()), rotation.family()), "warning",
                        texts.get("garden.check.short.rotation")));
    }

    /** Too little light is a warning; an unknown one is only a question (`light-need-v1`). */
    private static Optional<Component> light(CropChecks checks, NoteText.Texts texts,
                                             Function<String, String> cropName) {
        return switch (checks.light()) {
            case ENOUGH -> Optional.empty();
            case TOO_DARK -> Optional.of(check(VaadinIcon.SUN_O.create(),
                    texts.get("garden.check.light", cropName.apply(checks.cropId())), "warning",
                    texts.get("garden.check.short.light")));
            case UNKNOWN -> Optional.of(check(VaadinIcon.QUESTION_CIRCLE_O.create(),
                    texts.get("garden.check.light.unknown", cropName.apply(checks.cropId())), "unknown",
                    texts.get("garden.check.short.light.unknown")));
        };
    }

    /** A month outside the crop's sowing or planting window in the area. */
    private static Optional<Component> window(CropChecks checks, NoteText.Texts texts,
                                              Function<String, String> cropName) {
        return checks.inWindow() ? Optional.empty()
                : Optional.of(check(VaadinIcon.CALENDAR_O.create(),
                        texts.get("garden.check.window", cropName.apply(checks.cropId())), "warning",
                        texts.get("garden.check.short.window")));
    }

    /** A perennial that does not stand the winter here: in open ground it suffers, in a pot it is sheltered. */
    private static Optional<Component> frost(CropChecks checks, NoteText.Texts texts,
                                             Function<String, String> cropName) {
        FrostSuitabilityRule.Result frost = checks.frost();
        if (frost == null || frost.survives()) {
            return Optional.empty();
        }
        String key = frost.verdict() == FrostSuitabilityRule.Verdict.SHELTER_IN_POT
                ? "garden.check.frost.pot" : "garden.check.frost.ground";
        return Optional.of(check(new SvgIcon(GardenHomeView.FROST), texts.get(key,
                cropName.apply(checks.cropId()), frost.hardiness(), frost.winterMinimum()), "warning",
                texts.get(key.replace("garden.check.", "garden.check.short."))));
    }

    private static Optional<Component> present(List<String> cropIds,
                                               Function<List<String>, Component> check) {
        return cropIds.isEmpty() ? Optional.empty() : Optional.of(check.apply(cropIds));
    }

    /** The crops of a check by name, in the page's language. */
    private static String names(List<String> cropIds, Function<String, String> cropName) {
        return cropIds.stream().map(cropName).collect(Collectors.joining(", "));
    }

    /**
     * One check: its icon in the tone of what it says with a short word beside it, visible on a phone where a
     * tooltip never shows, and the sentence as tooltip and as what a screen reader says.
     */
    private static Span check(AbstractIcon<?> icon, String sentence, String tone, String label) {
        icon.getElement().setAttribute("aria-hidden", "true");
        Span span = new Span(icon, new Span(label));
        span.addClassNames("rootology-planting-check", "rootology-planting-check-" + tone);
        span.getElement().setAttribute("title", sentence);
        span.getElement().setAttribute("aria-label", sentence);
        span.getElement().setAttribute("role", "img");
        return span;
    }
}