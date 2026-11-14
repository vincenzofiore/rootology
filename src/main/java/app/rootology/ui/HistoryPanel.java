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
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.GardenPipeline.PendingReview;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The questions asked in the page's context, beside the question field of Ask (#100): every member's, newest
 * first, one card each (when, the outcome, the question, the answer's values and who asked), and a search on the
 * question's words. The questions still waiting for a person come first, with the hourglass (#104), and filters
 * narrow the list by outcome, by who asked and by period. A card opens its answer, or the waiting question, on
 * Ask's left; nothing here asks or changes anything.
 */
class HistoryPanel extends Div {

    /** What became of a question, for its icon and the outcome filter. */
    enum Outcome { ANSWERED, WAITING, OFF_DOMAIN }

    /** The period filter's choices, in days. */
    static final int WEEK = 7;
    static final int MONTH = 30;

    /** One card: a finished question (run) or one still waiting for a person (waiting), never both. */
    record Entry(String question, Outcome outcome, String askedBy, Instant at, PipelineRunRecord run,
                 PendingReview waiting) {

        static Entry of(PipelineRunRecord run) {
            return new Entry(run.getQuestion(), HistoryPanel.outcome(run), run.getStartedBy(), run.getFinishedAt(), run, null);
        }

        static Entry of(PendingReview review) {
            return new Entry(review.question().text(), Outcome.WAITING, review.context().startedBy(),
                    review.askedAt(), null, review);
        }
    }

    /** The filters chosen: an outcome or any (null), only the person's own questions, a period in days or any (null). */
    record Filters(Outcome outcome, boolean mine, Integer days) {

        static final Filters NONE = new Filters(null, false, null);

        boolean any() {
            return outcome != null || mine || days != null;
        }
    }

    private final RunHistory history;
    private final GardenPipeline pipeline;
    private final Consumer<PipelineRunRecord> open;
    private final Consumer<PendingReview> openWaiting;
    private final TextField search = new TextField();
    private final Div filterRow = new Div();
    private final Div cards = new Div();
    private final Paragraph noMatch = new Paragraph();
    private final EmptyState empty;
    private List<Entry> entries = List.of();
    private Filters filters = Filters.NONE;
    private String login = "";

    HistoryPanel(RunHistory history, GardenPipeline pipeline, Consumer<PipelineRunRecord> open,
                 Consumer<PendingReview> openWaiting) {
        this.history = history;
        this.pipeline = pipeline;
        this.open = open;
        this.openWaiting = openWaiting;
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
        filterRow.addClassName("rootology-history-filters");
        filterRow.getElement().setAttribute("role", "group");
        filterRow.getElement().setAttribute("aria-label", getTranslation("history.filters"));
        cards.addClassName("rootology-history-cards");
        noMatch.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        // Ask is beside it: the empty panel needs no button of its own.
        empty = new EmptyState(getTranslation("history.empty")).drawing(EmptyState.Drawing.NOTEBOOK);
        add(search, filterRow, cards, noMatch, empty);
    }

    /** Reads the context's questions again: when the page opens, and after each question of the visit. */
    void load(long spaceId, long gardenId, String login) {
        this.login = login;
        if (spaceId == 0) {
            entries = List.of();
        } else {
            // Waiting first, then the finished ones, each newest first.
            List<Entry> all = new ArrayList<>();
            pipeline.pending(spaceId, gardenId, login).stream().map(Entry::of)
                    .sorted(Comparator.comparing(Entry::at, Comparator.nullsLast(Comparator.reverseOrder())))
                    .forEach(all::add);
            history.recent(spaceId, gardenId, login).stream().map(Entry::of).forEach(all::add);
            entries = List.copyOf(all);
        }
        show();
    }

    /** How many questions the context has, waiting ones included, for the count on the History button. */
    int count() {
        return entries.size();
    }

    /** The question of this context with the agent's process id, for a notification's link (#100). */
    Optional<PipelineRunRecord> run(String processId) {
        return entries.stream().map(Entry::run).filter(r -> r != null && processId.equals(r.getProcessId()))
                .findFirst();
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

    /** The filters, for the chips and the tests. */
    void filter(Filters chosen) {
        filters = chosen;
        show();
    }

    /** How many cards are shown, for the tests. */
    int shown() {
        return cards.getComponentCount();
    }

    private void show() {
        cards.removeAll();
        String words = search.getValue();
        Instant now = Instant.now();
        entries.stream().filter(e -> matches(e.question(), words) && passes(e, filters, login, now))
                .forEach(e -> cards.add(card(e)));
        renderFilters();
        search.setVisible(!entries.isEmpty());
        filterRow.setVisible(!entries.isEmpty());
        empty.setVisible(entries.isEmpty());
        boolean none = !entries.isEmpty() && cards.getComponentCount() == 0;
        noMatch.setText(none ? (words.isBlank() ? getTranslation("history.nofilter")
                : getTranslation("history.nomatch", words.trim())) : "");
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

    /** Whether a question passes the filters: its outcome, who asked (logins compared ignoring case), when. */
    static boolean passes(Entry entry, Filters filters, String login, Instant now) {
        if (filters.outcome() != null && entry.outcome() != filters.outcome()) {
            return false;
        }
        if (filters.mine() && (entry.askedBy() == null || !entry.askedBy().equalsIgnoreCase(login))) {
            return false;
        }
        return filters.days() == null
                || (entry.at() != null && !entry.at().isBefore(now.minus(Duration.ofDays(filters.days()))));
    }

    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /**
     * The filters as toggle chips (#104): one outcome at a time, mine, one period at a time; a chip chosen again
     * goes back to any. Each says whether it is on (aria-pressed).
     */
    private void renderFilters() {
        filterRow.removeAll();
        Stream.of(Outcome.values()).forEach(o -> filterRow.add(chip(outcomeKey(o), filters.outcome() == o,
                () -> filter(new Filters(filters.outcome() == o ? null : o, filters.mine(), filters.days())))));
        filterRow.add(chip("history.filter.mine", filters.mine(),
                () -> filter(new Filters(filters.outcome(), !filters.mine(), filters.days()))));
        for (int days : new int[] {WEEK, MONTH}) {
            boolean on = Integer.valueOf(days).equals(filters.days());
            filterRow.add(chip(days == WEEK ? "history.filter.week" : "history.filter.month", on,
                    () -> filter(new Filters(filters.outcome(), filters.mine(), on ? null : days))));
        }
    }

    private Button chip(String key, boolean on, Runnable toggle) {
        Button chip = new Button(getTranslation(key), e -> toggle.run());
        chip.addClassName("rootology-filter-chip");
        chip.getElement().setAttribute("aria-pressed", String.valueOf(on));
        if (on) {
            chip.addClassName("rootology-filter-chip-on");
            chip.setIcon(VaadinIcon.CHECK.create());
        }
        return chip;
    }

    private static String outcomeKey(Outcome outcome) {
        return switch (outcome) {
            case ANSWERED -> "history.filter.answered";
            case WAITING -> "history.filter.waiting";
            case OFF_DOMAIN -> "history.filter.offdomain";
        };
    }

    /**
     * One question as a card (#80, #88): the date and its outcome, the question on as many lines as it needs, the
     * answer's values and who asked. A click, or Enter on the open button, shows the answer or the waiting question.
     */
    private Component card(Entry entry) {
        Div card = new Div();
        card.addClassName("rootology-history-card");
        Div head = new Div(DateText.dateTimeSpan(entry.at(), this), outcome(entry.outcome(), this));
        head.addClassName("rootology-history-card-head");
        Span question = new Span(entry.question());
        question.addClassName("rootology-history-card-question");
        Span askedBy = new Span(getTranslation("history.askedby") + " " + entry.askedBy());
        askedBy.addClassNames(FontSize.XSMALL, TextColor.SECONDARY);
        // No context line: the panel is already one garden's, or the space's; the answer shows its context.
        card.add(head, question);
        if (entry.run() != null) {
            Div badges = values(entry.run());
            badges.addClassName("rootology-history-card-values");
            card.add(badges);
        }
        card.add(askedBy);
        // The button is the keyboard's way in; its click bubbles to the card, which opens it once.
        Button openButton = RowActions.open(getTranslation(entry.run() != null ? "history.open" : "history.openwaiting"),
                e -> { });
        openButton.addClassName("rootology-history-card-open");
        card.add(openButton);
        if (entry.run() != null) {
            card.addClickListener(e -> open.accept(entry.run()));
        } else {
            card.addClassName("rootology-history-card-waiting");
            card.addClickListener(e -> openWaiting.accept(entry.waiting()));
        }
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

    /** Answered, waiting for a person or off domain (#53), of a finished question. */
    static Outcome outcome(PipelineRunRecord run) {
        return run.isOffDomain() ? Outcome.OFF_DOMAIN
                : run.isDecisionEscalated() && !run.isAnsweredByHuman() ? Outcome.WAITING : Outcome.ANSWERED;
    }

    /** The outcome as an icon whose tooltip and aria label say the word. */
    static Component outcome(PipelineRunRecord run, Component in) {
        return outcome(outcome(run), in);
    }

    static Component outcome(Outcome outcome, Component in) {
        VaadinIcon icon = switch (outcome) {
            case OFF_DOMAIN -> VaadinIcon.INFO_CIRCLE_O;
            case WAITING -> VaadinIcon.HOURGLASS;
            case ANSWERED -> VaadinIcon.CHECK_CIRCLE_O;
        };
        String word = in.getTranslation(switch (outcome) {
            case OFF_DOMAIN -> "history.offdomain";
            case WAITING -> "history.escalated";
            case ANSWERED -> "history.answered";
        });
        Icon described = icon.create();
        described.addClassNames(switch (outcome) {
            case OFF_DOMAIN -> TextColor.SECONDARY;
            case WAITING -> "rootology-waiting-icon";
            case ANSWERED -> TextColor.SUCCESS;
        });
        described.getElement().setAttribute("role", "img");
        described.getElement().setAttribute("aria-label", word);
        described.setTooltipText(word);
        return described;
    }
}
