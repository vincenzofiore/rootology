package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.badge.BadgeVariant;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import app.rootology.history.PipelineRunRecord;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.ContextSnapshot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The questions asked in the page's context, beside the question field of Ask (#100): every member's, newest
 * first, one card each (when, the outcome, the question, the answer's values and who asked), and a
 * search on the question's words. A card opens its answer on Ask's left; nothing here asks or changes anything.
 */
class HistoryPanel extends Div {

    private final RunHistory history;
    private final Consumer<PipelineRunRecord> open;
    private final TextField search = new TextField();
    private final Div cards = new Div();
    private final Paragraph noMatch = new Paragraph();
    private final EmptyState empty;
    private List<PipelineRunRecord> runs = List.of();

    HistoryPanel(RunHistory history, Consumer<PipelineRunRecord> open) {
        this.history = history;
        this.open = open;
        addClassName("rootology-history-panel");
        getElement().setAttribute("role", "region");
        getElement().setAttribute("aria-label", getTranslation("history.title"));

        search.setPlaceholder(getTranslation("history.search"));
        search.setAriaLabel(getTranslation("history.search"));
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> show());
        search.addClassName("rootology-history-search");
        cards.addClassName("rootology-history-cards");
        noMatch.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        // Ask is beside it: the empty panel needs no button of its own.
        empty = new EmptyState(getTranslation("history.empty")).drawing(EmptyState.Drawing.NOTEBOOK);
        add(search, cards, noMatch, empty);
    }

    /** Reads the context's questions again: when the page opens, and after each question of the visit. */
    void load(long spaceId, long gardenId, String login) {
        runs = spaceId == 0 ? List.of() : history.recent(spaceId, gardenId, login);
        show();
    }

    /** How many questions the context has, for the count on the History button. */
    int count() {
        return runs.size();
    }

    /** The question of this context with the agent's process id, for a notification's link (#100). */
    Optional<PipelineRunRecord> run(String processId) {
        return runs.stream().filter(r -> processId.equals(r.getProcessId())).findFirst();
    }

    /** The context a past question was answered in (#55), for its answer card. */
    Optional<ContextSnapshot> contextOf(PipelineRunRecord run) {
        return history.contextOf(run);
    }

    /** Where the keyboard lands when the column opens (#100): the search field. */
    void focusFirst() {
        search.focus();
    }

    /** The search, for the tests. */
    void search(String words) {
        search.setValue(words);
    }

    /** How many cards are shown, for the tests. */
    int shown() {
        return cards.getComponentCount();
    }

    private void show() {
        cards.removeAll();
        String words = search.getValue();
        runs.stream().filter(r -> matches(r.getQuestion(), words)).forEach(r -> cards.add(card(r)));
        search.setVisible(!runs.isEmpty());
        empty.setVisible(runs.isEmpty());
        boolean none = !runs.isEmpty() && cards.getComponentCount() == 0;
        noMatch.setText(none ? getTranslation("history.nomatch", words.trim()) : "");
        noMatch.setVisible(none);
    }

    /**
     * Whether a question holds every word searched, ignoring case and accents: "perche pomodoro" finds
     * "Perché il pomodoro…". Blank words match everything.
     */
    static boolean matches(String question, String words) {
        if (words == null || words.isBlank()) {
            return true;
        }
        String text = plain(question == null ? "" : question);
        for (String word : plain(words).split("\\s+")) {
            if (!word.isEmpty() && !text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /**
     * One question as a card (#80, #88): the date and its outcome, the question on as many lines as it needs, the
     * answer's values and who asked. A click, or Enter on the open button, shows the answer.
     */
    private Component card(PipelineRunRecord run) {
        Div card = new Div();
        card.addClassName("rootology-history-card");
        Div head = new Div(DateText.dateTimeSpan(run.getFinishedAt(), this), outcome(run, this));
        head.addClassName("rootology-history-card-head");
        Span question = new Span(run.getQuestion());
        question.addClassName("rootology-history-card-question");
        Div badges = values(run);
        badges.addClassName("rootology-history-card-values");
        Span askedBy = new Span(getTranslation("history.askedby") + " " + run.getStartedBy());
        askedBy.addClassNames(FontSize.XSMALL, TextColor.SECONDARY);
        // No context line: the panel is already one garden's, or the space's; the answer shows its context.
        card.add(head, question, badges, askedBy);
        // The button is the keyboard's way in; its click bubbles to the card, which opens the answer once.
        Button openButton = RowActions.open(getTranslation("history.open"), e -> { });
        openButton.addClassName("rootology-history-card-open");
        card.add(openButton);
        card.addClickListener(e -> open.accept(run));
        return card;
    }

    /** The answer's values as small badges, and nothing when the question was refused (#53). */
    private static Div values(PipelineRunRecord run) {
        Div badges = new Div();
        if (run.isOffDomain()) {
            return badges;
        }
        run.getAnswer().forEach(value -> {
            Badge badge = new Badge(value);
            badge.addThemeVariants(BadgeVariant.SMALL);
            badges.add(badge);
        });
        return badges;
    }

    /** Answered, waiting for a person or off domain (#53): an icon whose tooltip and aria label say the word. */
    static Component outcome(PipelineRunRecord run, Component in) {
        boolean waiting = run.isDecisionEscalated() && !run.isAnsweredByHuman();
        VaadinIcon icon = run.isOffDomain() ? VaadinIcon.INFO_CIRCLE_O
                : waiting ? VaadinIcon.HOURGLASS : VaadinIcon.CHECK_CIRCLE_O;
        String word = in.getTranslation(run.isOffDomain() ? "history.offdomain"
                : waiting ? "history.escalated" : "history.answered");
        Icon described = icon.create();
        described.addClassNames(run.isOffDomain() ? TextColor.SECONDARY : waiting ? TextColor.WARNING : TextColor.SUCCESS);
        described.getElement().setAttribute("role", "img");
        described.getElement().setAttribute("aria-label", word);
        described.setTooltipText(word);
        return described;
    }
}
