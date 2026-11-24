package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.Background;
import com.vaadin.flow.theme.lumo.LumoUtility.BorderRadius;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.FontWeight;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;
import com.vaadin.flow.theme.lumo.LumoUtility.MaxWidth;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import app.rootology.decision.Note;
import app.rootology.pipeline.ContextSnapshot;

import java.util.List;

/**
 * An answer as a card (#31, lighter in #83): Claude's sentence and the values first, then only the warnings the
 * person should act on, the confidence in words with a bar and what it means, and everything else (the part of
 * the context the line above does not say, the other notes, the sources) under a closed "Why this answer?" —
 * "Why it waits?" on a waiting question (#111). Also the card of a question that waits.
 */
class AnswerCard extends Div {

    /** The confidence in words (#83); answers start at 0.5, below that the question waits for a person. */
    static String confidenceKey(double confidence) {
        if (confidence >= 0.80) {
            return "card.confidence.high";
        }
        return confidence >= 0.65 ? "card.confidence.good" : "card.confidence.fair";
    }

    /** Where the warnings of an answer go: under its values, above the confidence; null before an answer. */
    private Div warnings;
    /** "Why this answer?", added at the end of the card the first time something goes in it. */
    private Details why;
    private Div whyContent;
    /** What the fold is called: an answer asks why it answers, a waiting question why it waits (#111). */
    private String whyLabel = "card.why";

    /** One source line: an icon, a text, and a link when there is one (url may be null). */
    record SourceLine(VaadinIcon icon, String text, String url) {
    }

    AnswerCard() {
        addClassNames(Display.FLEX, "flex-col", Gap.SMALL, Padding.MEDIUM, BorderRadius.LARGE, Background.CONTRAST_5,
                MaxWidth.SCREEN_MEDIUM, Margin.Top.MEDIUM);
    }

    /** The context the answer was given in (#55), above the answer itself. */
    AnswerCard context(String line) {
        Span context = new Span(line);
        context.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        add(context);
        return this;
    }

    /**
     * The context of an answer (#83): a short line above it (garden, area, month), and under "Why this
     * answer?" only what that line does not say — the climate and its source, a zone from an older row, a month
     * the question named (#111).
     */
    AnswerCard context(ContextSnapshot context) {
        return context(context, null);
    }

    /**
     * The same, for a card shown in a page whose garden is already in the breadcrumb and in its context line
     * (#121): the garden is left out of both the line above and the fold, and the line goes when it has
     * nothing else to say.
     *
     * @param pageGarden the name of the garden the page is about; null at the space level
     */
    AnswerCard context(ContextSnapshot context, String pageGarden) {
        String briefText = ContextText.brief(context, pageGarden, this);
        if (!briefText.isBlank()) {
            Span brief = new Span(briefText);
            brief.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
            add(brief);
        }
        String detail = ContextText.detail(context, pageGarden, this);
        if (!detail.isBlank()) {
            // Said for what it is (#114): alone, "ultime gelate metà febbraio (dall’orto)" read as a stray line.
            Span label = new Span(getTranslation("card.why.context"));
            label.addClassName(FontWeight.SEMIBOLD);
            why().add(line(VaadinIcon.CALENDAR_O, new Span(label, new Span(" " + detail))));
        }
        return whyLast();
    }

    AnswerCard answered(String text, List<String> values, double confidence) {
        return answered(text, values, confidence, java.util.Map.of());
    }

    /**
     * An answer whose values carry how they go into the ground (#112): the values grouped by activity, "In
     * semenzaio: febbraio, marzo · Trapianto: aprile, maggio", always shown, since the groups are what keeps a
     * seedbed month from reading as a month to plant out.
     */
    AnswerCard answered(String text, List<String> values, double confidence,
                        java.util.Map<String, List<String>> activities) {
        sentenceAndValues(text, values, activities, true);
        return confidence(confidence);
    }

    /**
     * An answer a person of the space gave (#116): who answered instead of a confidence bar, which would only
     * measure the decision layer that did not answer.
     *
     * @param reviewer the login of who answered, null when it was not recorded
     */
    AnswerCard answeredBy(String text, List<String> values, String reviewer,
                          java.util.Map<String, List<String>> activities) {
        // A person's "no" is said by their sentence (#117): a "no values" badge under it only repeats it.
        sentenceAndValues(text, values, activities, false);
        warnings();
        Span who = new Span(reviewer == null ? getTranslation("card.byperson.someone")
                : getTranslation("card.byperson", reviewer));
        who.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        Icon icon = VaadinIcon.USER.create();
        icon.addClassName("rootology-answer-person-icon");
        Div row = new Div(icon, who);
        row.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.SMALL, "rootology-answer-person");
        add(row);
        return this;
    }

    /**
     * What a calendar answer means for the month it was asked in (#116), above the sentence: worked out from
     * the values, so a sentence that says "yes" for a month months away cannot be read as "now".
     */
    AnswerCard verdict(CalendarVerdict.Verdict verdict) {
        Icon icon = (verdict.now() ? VaadinIcon.CHECK_CIRCLE_O : VaadinIcon.CALENDAR_CLOCK).create();
        icon.addClassName("rootology-verdict-icon");
        Span words = new Span(verdict.words());
        Div row = new Div(icon, words);
        row.addClassName("rootology-verdict");
        row.addClassName(verdict.now() ? "rootology-verdict-now" : "rootology-verdict-later");
        add(row);
        return this;
    }

    private void sentenceAndValues(String text, List<String> values, java.util.Map<String, List<String>> activities,
                                   boolean sayNoValues) {
        Paragraph sentence = new Paragraph(text);
        sentence.addClassName("rootology-answer-text");
        add(sentence);
        if (!activities.isEmpty()) {
            add(activityGroups(values, activities));
            return;
        }
        // The values in the page's language (#110), without those the sentence already says: a badge repeating
        // a word just read adds nothing. An answer with no value keeps its "no values" badge.
        List<String> words = values.stream().filter(ValueText::badge).map(v -> ValueText.of(v, this)).filter(v -> !ValueText.says(text, v))
                .toList();
        if ((values.isEmpty() && sayNoValues) || !words.isEmpty()) {
            Div chips = new Div();
            chips.addClassName("rootology-answer-values");
            if (values.isEmpty()) {
                chips.add(chip(getTranslation("ask.novalues"), "rootology-answer-value-none"));
            }
            words.forEach(v -> chips.add(chip(v, null)));
            add(chips);
        }
    }

    /** One row per activity, in the activities' order, with its values; a value with none goes in a plain row. */
    private Div activityGroups(List<String> values, java.util.Map<String, List<String>> activities) {
        Div groups = new Div();
        groups.addClassName("rootology-answer-activities");
        java.util.Map<String, List<String>> byActivity = new java.util.TreeMap<>(java.util.Comparator.comparingInt(
                (String a) -> app.rootology.facts.SowingActivity.valueOf(a).ordinal()));
        List<String> plain = new java.util.ArrayList<>();
        for (String value : values) {
            List<String> ways = activities.getOrDefault(value, List.of());
            if (ways.isEmpty()) {
                plain.add(value);
            }
            ways.forEach(a -> byActivity.computeIfAbsent(a, k -> new java.util.ArrayList<>()).add(value));
        }
        byActivity.forEach((activity, group) -> groups.add(activityRow(getTranslation("activity." + activity), group)));
        if (!plain.isEmpty()) {
            groups.add(activityRow(null, plain));
        }
        return groups;
    }

    private Div activityRow(String label, List<String> group) {
        Div row = new Div();
        row.addClassName("rootology-answer-values");
        if (label != null) {
            Span name = new Span(label);
            name.addClassName("rootology-answer-activity");
            row.add(name);
        }
        group.forEach(v -> row.add(chip(ValueText.of(v, this), null)));
        return row;
    }

    private void warnings() {
        warnings = new Div();
        warnings.addClassNames(Display.FLEX, "flex-col", Gap.SMALL);
        warnings.setVisible(false);
        add(warnings);
    }

    private AnswerCard confidence(double confidence) {
        warnings();
        double value = Math.max(0, Math.min(1, confidence));
        ProgressBar bar = new ProgressBar(0, 1, value);
        bar.addClassName("rootology-confidence-bar");
        if (value >= 0.80) {
            bar.addThemeName("success");
        }
        Span label = new Span(getTranslation(confidenceKey(value)));
        label.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        Div row = new Div(label, bar);
        row.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.MEDIUM);
        row.getElement().setAttribute("title", getTranslation("card.confidence", confidence));
        add(row);
        // What the number means (#111): "Affidabilità media" alone says nothing to a gardener.
        why().add(line(VaadinIcon.QUESTION_CIRCLE_O, getTranslation("card.confidence.meaning")));
        return whyLast();
    }

    /**
     * A waiting question as the person who asked it reads it (#110): a promise rather than a verdict. The
     * decision layer's reason goes under "Why it waits?" (#111), in words, its number as a tooltip (#114).
     *
     * @param reason the reason as the pipeline recorded it, worded here by {@link ReasonText}
     */
    AnswerCard promise(String reason) {
        whyCalled("card.why.waiting");
        add(header(VaadinIcon.HOURGLASS, "rootology-waiting-icon", getTranslation("card.waiting")));
        add(new Paragraph(getTranslation("card.waiting.promise")));
        if (reason != null && !reason.isBlank()) {
            Span words = new Span(getTranslation("card.waiting.reason", ReasonText.text(reason, this)));
            why().add(line(VaadinIcon.INFO_CIRCLE_O, ReasonText.withNumber(words, reason, this)));
        }
        return whyLast();
    }

    /** A waiting question as whoever answers it reads it: the reason in words, its number as a tooltip (#114). */
    AnswerCard waiting(String reason) {
        whyCalled("card.why.waiting");
        add(header(VaadinIcon.HOURGLASS, "rootology-waiting-icon", getTranslation("card.waiting")));
        add(ReasonText.withNumber(new Paragraph(getTranslation("card.waiting.why", ReasonText.text(reason, this))),
                reason, this));
        return whyLast();
    }

    /** A question clearly outside gardens (#53): a fixed sentence, no values and no confidence. */
    AnswerCard refused(String text) {
        add(header(VaadinIcon.INFO_CIRCLE_O, TextColor.SECONDARY, getTranslation("card.offdomain")));
        add(new Paragraph(text));
        return whyLast();
    }

    /**
     * The notes of an answer (#83): what the person should act on as warnings under the values, the rest
     * under "Why this answer?".
     */
    AnswerCard notes(List<Note> notes) {
        for (Note note : notes) {
            if (NoteText.actionable(note)) {
                HasComponents into = warnings == null ? this : warnings;
                Div warning = callout(NoteText.icon(note), NoteText.Tone.MISSING, NoteText.text(note, this));
                // Frost and late blight keep their own colours (#87): ice blue and amber.
                String state = switch (note.kind()) {
                    case FROST_EXCLUDED, FROST_SUITABILITY -> "rootology-callout-frost";
                    case SMITH_PERIOD -> "rootology-callout-pest";
                    default -> null;
                };
                if (state != null) {
                    warning.removeClassName(Background.WARNING_10);
                    warning.getChildren().findFirst().ifPresent(i -> i.getElement().getClassList().remove(TextColor.WARNING));
                    warning.addClassName(state);
                }
                into.add(warning);
                if (warnings != null) {
                    warnings.setVisible(true);
                }
            } else {
                why().add(line(NoteText.icon(note), NoteText.text(note, this)));
            }
        }
        return whyLast();
    }

    /** A question's notes as coloured callouts, wherever they are shown (#60): a card or a waiting question. */
    static void addNotes(List<Note> notes, Component in, HasComponents into) {
        notes.forEach(note -> into.add(callout(NoteText.icon(note), NoteText.tone(note), NoteText.text(note, in))));
    }

    AnswerCard sources(List<SourceLine> lines) {
        if (lines.isEmpty()) {
            return this;
        }
        why().add(sourcesTitle());
        for (SourceLine line : lines) {
            Component text = line.url() == null ? new Span(line.text()) : link(line.text(), line.url());
            why().add(line(line.icon(), text));
        }
        return whyLast();
    }

    /**
     * A past answer stored before notes and sources were kept (#110): rather than leaving the fold silent about
     * its sources, it says it was saved before Rootology recorded them (#111, #114). A fresh answer with no
     * source stays silent.
     */
    AnswerCard noSources() {
        why().add(sourcesTitle());
        why().add(line(VaadinIcon.BOOK, getTranslation("ask.sources.before")));
        return whyLast();
    }

    private Span sourcesTitle() {
        Span title = new Span(getTranslation("ask.sources"));
        title.addClassNames(FontSize.SMALL, FontWeight.SEMIBOLD, TextColor.SECONDARY, Margin.Top.SMALL);
        return title;
    }

    /** "Why this answer?", created closed at the end of the card when the first line goes in it. */
    private Div why() {
        if (why == null) {
            whyContent = new Div();
            whyContent.addClassNames(Display.FLEX, "flex-col", Gap.SMALL);
            why = new Details(getTranslation(whyLabel), whyContent);
            why.addClassName("rootology-answer-why");
        }
        return whyContent;
    }

    /** The fold's own words: an answer asks why it answers, a waiting question why it waits (#111). */
    private void whyCalled(String key) {
        whyLabel = key;
        if (why != null) {
            why.setSummaryText(getTranslation(key));
        }
    }

    /** "Why this answer?" stays the last part of the card, whatever was added after it. */
    AnswerCard whyLast() {
        if (why != null) {
            add(why);
        }
        return this;
    }

    private static Div line(VaadinIcon icon, String text) {
        return line(icon, new Span(text));
    }

    private static Div line(VaadinIcon icon, Component text) {
        Icon i = icon.create();
        i.addClassName("rootology-why-icon");
        Div row = new Div(i, text);
        row.addClassName("rootology-why-line");
        return row;
    }

    AnswerCard extra(Component component) {
        add(component);
        return whyLast();
    }

    private static Anchor link(String text, String url) {
        Anchor anchor = new Anchor(url, text);
        anchor.setTarget("_blank");
        return anchor;
    }

    private static Div header(VaadinIcon icon, String color, String text) {
        Icon i = icon.create();
        i.addClassNames(color);
        Span title = new Span(text);
        title.addClassNames(FontWeight.SEMIBOLD);
        Div header = new Div(i, title);
        header.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.SMALL);
        return header;
    }

    private static Span chip(String text, String variant) {
        Span chip = new Span(text);
        chip.addClassName("rootology-answer-value");
        if (variant != null) {
            chip.addClassName(variant);
        }
        return chip;
    }

    private static Div callout(VaadinIcon icon, NoteText.Tone tone, String text) {
        Icon i = icon.create();
        i.setSize("1.1em");
        Span body = new Span(text);
        Div callout = new Div(i, body);
        callout.addClassNames(Display.FLEX, AlignItems.START, Gap.SMALL, Padding.SMALL, BorderRadius.MEDIUM, FontSize.SMALL);
        switch (tone) {
            case RISK -> {
                callout.addClassNames(Background.ERROR_10);
                i.addClassNames(TextColor.ERROR);
            }
            case MISSING -> {
                callout.addClassNames(Background.WARNING_10);
                i.addClassNames(TextColor.WARNING);
            }
            case INFO -> {
                callout.addClassNames(Background.CONTRAST_5);
                i.addClassNames(TextColor.SECONDARY);
            }
        }
        return callout;
    }
}
