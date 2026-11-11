package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
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
import com.vaadin.flow.theme.lumo.LumoUtility.FlexWrap;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.FontWeight;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;
import com.vaadin.flow.theme.lumo.LumoUtility.MaxWidth;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import io.github.vincenzofiore.rootology.decision.Note;

import java.util.List;

/**
 * An answer as a card (#31): Claude's sentence, the values as badges, the confidence as a bar, what the person
 * should know as coloured callouts with an icon, and the sources. Also the card of a question that waits.
 */
class AnswerCard extends Div {

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

    AnswerCard answered(String text, List<String> values, double confidence) {
        add(header(VaadinIcon.CHECK_CIRCLE_O, TextColor.SUCCESS, getTranslation("card.answer")));
        add(new Paragraph(text));
        Div badges = new Div();
        badges.addClassNames(Display.FLEX, FlexWrap.WRAP, Gap.SMALL);
        if (values.isEmpty()) {
            badges.add(badge(getTranslation("ask.novalues"), Background.CONTRAST_10, TextColor.SECONDARY));
        }
        values.forEach(v -> badges.add(badge(v, Background.PRIMARY_10, TextColor.PRIMARY)));
        add(badges);
        ProgressBar bar = new ProgressBar(0, 1, Math.max(0, Math.min(1, confidence)));
        bar.setWidth("12rem");
        Span label = new Span(getTranslation("card.confidence", confidence));
        label.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        Div row = new Div(label, bar);
        row.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.MEDIUM);
        add(row);
        return this;
    }

    AnswerCard waiting(String reason) {
        add(header(VaadinIcon.HOURGLASS, TextColor.WARNING, getTranslation("card.waiting")));
        add(new Paragraph(getTranslation("card.waiting.why", reason)));
        return this;
    }

    /** A question clearly outside gardens (#53): a fixed sentence, no values and no confidence. */
    AnswerCard refused(String text) {
        add(header(VaadinIcon.INFO_CIRCLE_O, TextColor.SECONDARY, getTranslation("card.offdomain")));
        add(new Paragraph(text));
        return this;
    }

    AnswerCard notes(List<Note> notes) {
        addNotes(notes, this, this);
        return this;
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
        add(title);
        for (SourceLine line : lines) {
            Icon icon = line.icon().create();
            icon.setSize("0.9em");
            Component text = line.url() == null ? new Span(line.text()) : link(line.text(), line.url());
            Div row = new Div(icon, text);
            row.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.SMALL, FontSize.SMALL);
            add(row);
        }
        return this;
    }

    AnswerCard extra(Component component) {
        add(component);
        return this;
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

    private static Span badge(String text, String background, String color) {
        Span badge = new Span(text);
        badge.addClassNames(background, color, BorderRadius.LARGE, Padding.Horizontal.SMALL, Padding.Vertical.XSMALL,
                FontSize.SMALL, FontWeight.MEDIUM);
        return badge;
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
