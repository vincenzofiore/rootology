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
import app.rootology.pipeline.PipelineAnswer;
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
 * Ask's left; nothing here asks or changes anything. At the space level it holds every garden's questions and
 * those without one, says which garden each belongs to, and adds a garden filter (#109).
 */
class HistoryPanel extends Div {

    /** What became of a question, for its icon and the outcome filter. */
    enum Outcome { ANSWERED, WAITING, OFF_DOMAIN }

    /** The period filter's choices, in days. */
    static final int WEEK = 7;

    /** The garden of a question with none, in the filter and on the cards (#109): no garden's name is empty. */
    static final String NO_GARDEN = "";

    /**
     * One card: a finished question (run) or one still waiting for a person (waiting), never both, and the
     * name of its garden (NO_GARDEN at the space level when it has none, #109).
     */
    record Entry(String question, Outcome outcome, String askedBy, Instant at, PipelineRunRecord run,
                 PendingReview waiting, String garden) {

        static Entry of(PipelineRunRecord run, String garden) {
            return new Entry(run.getQuestion(), HistoryPanel.outcome(run), run.getStartedBy(), run.getFinishedAt(),
                    run, null, garden);
        }

        static Entry of(PendingReview review) {
            return new Entry(review.question().text(), Outcome.WAITING, review.context().startedBy(),
                    review.askedAt(), null, review, HistoryPanel.gardenOf(review));
        }
    }

    /**
     * The filters chosen: an outcome or any (null), only the person's own questions, a period in days or any
     * (null), and one garden of the space at the space level, or any (null, #109).
     */
    record Filters(Outcome outcome, boolean mine, Integer days, String garden) {

        static final Filters NONE = new Filters(null, false, null, null);

        /** The filters without a garden (#104): inside a garden there is nothing to choose. */
        Filters(Outcome outcome, boolean mine, Integer days) {
            this(outcome, mine, days, null);
        }

        boolean any() {
            return outcome != null || mine || days != null || garden != null;
        }
    }

    private final RunHistory history;
    private final GardenPipeline pipeline;
    private final Consumer<PipelineRunRecord> open;
    private final Consumer<PendingReview> openWaiting;
    private final TextField search = new TextField();
    private final Div filterRow = new Div();
    /** The gardens of the space, at the space level only (#109): one chip each, or none. */
    private final Div gardenRow = new Div();
    private final Div cards = new Div();
    private final Paragraph noMatch = new Paragraph();
    private final EmptyState empty;
    private List<Entry> entries = List.of();
    private Filters filters = Filters.NONE;
    /** Whether someone else asked a question of the list (#119), set on every render. */
    private boolean othersAsk;
    /** The keys of the questions open on Ask's left ("past-<id>", "waiting-<process id>"), marked here (#119). */
    private final java.util.Set<String> openKeys = new java.util.HashSet<>();
    private String login = "";
    /** The display names of the askers (#106), read once per load; the entries keep the logins. */
    private final java.util.function.Function<java.util.Collection<String>, java.util.Map<String, String>> namesOf;
    private java.util.Map<String, String> names = java.util.Map.of();
    /** Whether the panel lists every garden of the space (#109): true only at the space level. */
    private boolean spaceLevel;

    HistoryPanel(RunHistory history, GardenPipeline pipeline, Consumer<PipelineRunRecord> open,
                 Consumer<PendingReview> openWaiting,
                 java.util.function.Function<java.util.Collection<String>, java.util.Map<String, String>> namesOf) {
        this.namesOf = namesOf;
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
        gardenRow.addClassName("rootology-history-filters");
        gardenRow.addClassName("rootology-history-gardens");
        gardenRow.getElement().setAttribute("role", "group");
        gardenRow.getElement().setAttribute("aria-label", getTranslation("history.gardens"));
        cards.addClassName("rootology-history-cards");
        noMatch.addClassNames(FontSize.SMALL, TextColor.SECONDARY);
        // Ask is beside it: the empty panel needs no button of its own.
        empty = new EmptyState(getTranslation("history.empty")).drawing(EmptyState.Drawing.NOTEBOOK);
        add(search, filterRow, gardenRow, cards, noMatch, empty);
    }

    /** Reads the context's questions again: when the page opens, and after each question of the visit. */
    void load(long spaceId, long gardenId, String login) {
        this.login = login;
        this.spaceLevel = gardenId == 0;
        if (spaceId == 0) {
            entries = List.of();
        } else if (spaceLevel) {
            // Every question of the space, every garden's and those without one (#109).
            List<Entry> all = new ArrayList<>();
            pipeline.pendingInSpace(spaceId, login).stream().map(Entry::of)
                    .sorted(Comparator.comparing(Entry::at, Comparator.nullsLast(Comparator.reverseOrder())))
                    .forEach(all::add);
            history.recentInSpace(spaceId, login).stream()
                    .map(run -> Entry.of(run, gardenName(run))).forEach(all::add);
            entries = List.copyOf(all);
        } else {
            // Waiting first, then the finished ones, each newest first.
            List<Entry> all = new ArrayList<>();
            pipeline.pending(spaceId, gardenId, login).stream().map(Entry::of)
                    .sorted(Comparator.comparing(Entry::at, Comparator.nullsLast(Comparator.reverseOrder())))
                    .forEach(all::add);
            history.recent(spaceId, gardenId, login).stream().map(run -> Entry.of(run, NO_GARDEN))
                    .forEach(all::add);
            entries = List.copyOf(all);
        }
        names = namesOf.apply(entries.stream().map(Entry::askedBy).filter(java.util.Objects::nonNull).toList());
        show();
    }

    /** The garden a stored question was answered in, kept in its context snapshot so a deleted garden keeps its name. */
    private String gardenName(PipelineRunRecord run) {
        return history.contextOf(run).map(ContextSnapshot::gardenName).filter(name -> name != null)
                .orElse(NO_GARDEN);
    }

    /** The garden a waiting question was asked in, from its context snapshot (#109). */
    static String gardenOf(PendingReview review) {
        return review.snapshot() == null || review.snapshot().gardenName() == null
                ? NO_GARDEN : review.snapshot().gardenName();
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

    /** A past question's answer as the pipeline gave it (#110), for the same card as a fresh one. */
    PipelineAnswer answerOf(PipelineRunRecord run) {
        return history.answerOf(run);
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

    /** A question now open on Ask's left (#119): its card says so (aria-current). */
    void opened(String key) {
        if (openKeys.add(key)) {
            show();
        }
    }

    /** A question closed on Ask's left (#119). */
    void closed(String key) {
        if (openKeys.remove(key)) {
            show();
        }
    }

    /** Whether a question's card is marked as open, for the tests. */
    boolean isOpen(String key) {
        return openKeys.contains(key);
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
        othersAsk = othersAsk(entries, login);
        entries.stream().filter(e -> matches(e.question(), words) && passes(e, filters, login, now))
                .forEach(e -> cards.add(card(e)));
        renderFilters();
        renderGardenFilters();
        search.setVisible(!entries.isEmpty());
        filterRow.setVisible(filterRow.getComponentCount() > 0);
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

    /**
     * Whether a question passes the filters: its outcome, who asked (logins compared ignoring case), when, and
     * its garden at the space level (#109).
     */
    static boolean passes(Entry entry, Filters filters, String login, Instant now) {
        if (filters.outcome() != null && entry.outcome() != filters.outcome()) {
            return false;
        }
        if (filters.garden() != null && !filters.garden().equals(entry.garden())) {
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
     * Whether someone other than the person asked a question of the list (#119): in a space of one, "asked by"
     * and "mine" say nothing.
     */
    static boolean othersAsk(List<Entry> entries, String login) {
        return entries.stream().anyMatch(e -> e.askedBy() != null && !e.askedBy().equalsIgnoreCase(login));
    }

    /**
     * The filters as toggle chips (#104), only those that help (#119): the waiting questions when some wait,
     * and mine when others ask too; the search and the newest-first order do the rest. A chip chosen again goes
     * back to any, and each says whether it is on (aria-pressed).
     */
    private void renderFilters() {
        filterRow.removeAll();
        if (filters.outcome() == Outcome.WAITING || entries.stream().anyMatch(e -> e.outcome() == Outcome.WAITING)) {
            boolean on = filters.outcome() == Outcome.WAITING;
            filterRow.add(chip(outcomeKey(Outcome.WAITING), on,
                    () -> filter(new Filters(on ? null : Outcome.WAITING, filters.mine(), filters.days(),
                            filters.garden()))));
        }
        if (othersAsk || filters.mine()) {
            filterRow.add(chip("history.filter.mine", filters.mine(),
                    () -> filter(new Filters(filters.outcome(), !filters.mine(), filters.days(), filters.garden()))));
        }
    }

    /**
     * The garden filter of the space level (#109): one chip per garden the list holds, plus "no garden" when
     * some question has none; one at a time, and a chip chosen again goes back to any (#104).
     */
    private void renderGardenFilters() {
        gardenRow.removeAll();
        if (!spaceLevel) {
            gardenRow.setVisible(false);
            return;
        }
        List<String> gardens = entries.stream().map(Entry::garden).distinct()
                .sorted(Comparator.comparing(name -> name.equals(NO_GARDEN) ? "\uFFFF" : name))
                .toList();
        if (gardens.size() < 2) {
            // One garden only (or none at all): every card already says it, and there is nothing to choose.
            gardenRow.setVisible(false);
            return;
        }
        for (String garden : gardens) {
            boolean on = garden.equals(filters.garden());
            Button chip = new Button(garden.equals(NO_GARDEN) ? getTranslation("history.nogarden") : garden,
                    e -> filter(new Filters(filters.outcome(), filters.mine(), filters.days(), on ? null : garden)));
            chip.addClassName("rootology-filter-chip");
            chip.getElement().setAttribute("aria-pressed", String.valueOf(on));
            if (on) {
                chip.addClassName("rootology-filter-chip-on");
                chip.setIcon(VaadinIcon.CHECK.create());
            }
            gardenRow.add(chip);
        }
        gardenRow.setVisible(true);
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
        // "Answered" is the normal state (#122): only waiting and off-domain questions get a mark, so a check
        // beside a calendar's months no longer reads as "yes". At the space level the garden is text before the
        // date, not a chip like the answer's values.
        Div head = new Div();
        head.addClassName("rootology-history-card-head");
        if (spaceLevel) {
            Span garden = new Span(entry.garden().equals(NO_GARDEN)
                    ? getTranslation("history.nogarden") : entry.garden());
            garden.addClassName("rootology-history-card-garden");
            head.add(garden);
        }
        head.add(DateText.dateTimeSpan(entry.at(), this));
        if (entry.outcome() != Outcome.ANSWERED) {
            head.add(outcome(entry.outcome(), this));
        }
        Span question = new Span(entry.question());
        question.addClassName("rootology-history-card-question");
        Span askedBy = new Span(getTranslation("history.askedby") + " "
                + names.getOrDefault(entry.askedBy(), entry.askedBy()));
        askedBy.addClassNames(FontSize.XSMALL, TextColor.SECONDARY);
        // Inside a garden there is no context line (the panel is already that garden's); at the space level
        // every card says which garden its question belongs to, or that it has none (#109).
        card.add(head);
        card.add(question);
        if (entry.run() != null) {
            Div badges = values(entry.run());
            badges.addClassName("rootology-history-card-values");
            card.add(badges);
        }
        if (othersAsk) {
            card.add(askedBy);
        }
        // The button is the keyboard's way in; its click bubbles to the card, which opens it once. Its label
        // names the question, so a screen reader's list of buttons says which answer each one opens (#113).
        Button openButton = RowActions.open(openLabel(entry.run() == null, entry.question(), this::getTranslation),
                e -> { });
        openButton.addClassName("rootology-history-card-open");
        card.add(openButton);
        String key = entry.run() != null ? "past-" + entry.run().getId() : "waiting-" + entry.waiting().processId();
        if (openKeys.contains(key)) {
            card.addClassName("rootology-history-card-current");
            card.getElement().setAttribute("aria-current", "true");
        }
        if (entry.run() != null) {
            card.addClickListener(e -> open.accept(entry.run()));
        } else {
            card.addClassName("rootology-history-card-waiting");
            card.addClickListener(e -> openWaiting.accept(entry.waiting()));
        }
        return card;
    }

    /**
     * The open button's label (#113): the word, then the question it opens, so a screen reader's list of the
     * card's buttons says which answer each one leads to.
     */
    static String openLabel(boolean waiting, String question, NoteText.Texts texts) {
        return texts.get(waiting ? "history.openwaiting" : "history.open", question);
    }

    /**
     * The answer's values as small badges (#110: in the page's language, all of them, since a card is the
     * scannable summary of a question), and nothing when the question was refused (#53).
     */
    private Div values(PipelineRunRecord run) {
        Div badges = new Div();
        if (run.isOffDomain()) {
            return badges;
        }
        // A calendar answer says what it means for the month it was asked in (#122), as its opened card does;
        // its months are on opening.
        var answer = history.answerOf(run);
        var verdict = CalendarVerdict.of(answer.answer(), answer.activities(), answer.context(), run.getFinishedAt(),
                this);
        if (verdict.isPresent()) {
            Icon icon = (verdict.get().now() ? VaadinIcon.CHECK_CIRCLE_O : VaadinIcon.CALENDAR_CLOCK).create();
            icon.addClassName("rootology-verdict-icon");
            Div line = new Div(icon, new Span(verdict.get().brief()));
            line.addClassNames("rootology-verdict", "rootology-history-card-verdict",
                    verdict.get().now() ? "rootology-verdict-now" : "rootology-verdict-later");
            badges.add(line);
            return badges;
        }
        run.getAnswer().forEach(value -> {
            Badge badge = new Badge(ValueText.of(value, this));
            // Values are read, not acted on: neutral, so green stays where the person acts (#113).
            badge.addThemeVariants(BadgeVariant.SMALL, BadgeVariant.CONTRAST);
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
