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
 * person should act on, the confidence in words with a bar, and everything else (the full context, the other
 * notes, the sources) under a closed "Why this answer?". Also the card of a question that waits.
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
     * The context of an answer (#83): a short line above it (garden, area, month), the full line under
     * "Why this answer?".
     */
    AnswerCard context(ContextSnapshot context) {
        Span brief = new Span(ContextText.brief(context, this));
        brief.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        add(brief);
        Span full = new Span(ContextText.line(context, this));
        full.addClassName("rootology-why-line");
        why().add(full);
        return whyLast();
    }

    AnswerCard answered(String text, List<String> values, double confidence) {
        Paragraph sentence = new Paragraph(text);
        sentence.addClassName("rootology-answer-text");
        add(sentence);
        Div chips = new Div();
        chips.addClassName("rootology-answer-values");
        if (values.isEmpty()) {
            chips.add(chip(getTranslation("ask.novalues"), "rootology-answer-value-none"));
        }
        values.forEach(v -> chips.add(chip(v, null)));
        add(chips);
        warnings = new Div();
        warnings.addClassNames(Display.FLEX, "flex-col", Gap.SMALL);
        warnings.setVisible(false);
        add(warnings);
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
        return whyLast();
    }

    AnswerCard waiting(String reason) {
        add(header(VaadinIcon.HOURGLASS, TextColor.WARNING, getTranslation("card.waiting")));
        add(new Paragraph(getTranslation("card.waiting.why", reason)));
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
                into.add(callout(NoteText.icon(note), NoteText.Tone.MISSING, NoteText.text(note, this)));
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
        Span title = new Span(getTranslation("ask.sources"));
        title.addClassNames(FontSize.SMALL, FontWeight.SEMIBOLD, TextColor.SECONDARY, Margin.Top.SMALL);
        why().add(title);
        for (SourceLine line : lines) {
            Component text = line.url() == null ? new Span(line.text()) : link(line.text(), line.url());
            why().add(line(line.icon(), text));
        }
        return whyLast();
    }

    /** "Why this answer?", created closed at the end of the card when the first line goes in it. */
    private Div why() {
        if (why == null) {
            whyContent = new Div();
            whyContent.addClassNames(Display.FLEX, "flex-col", Gap.SMALL);
            why = new Details(getTranslation("card.why"), whyContent);
            why.addClassName("rootology-answer-why");
        }
        return whyContent;
    }

    /** "Why this answer?" stays the last part of the card, whatever was added after it. */
    private AnswerCard whyLast() {
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
