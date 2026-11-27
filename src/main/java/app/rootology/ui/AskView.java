package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParameters;
import app.rootology.decision.Intent;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenTime;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.history.PipelineRunRecord;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.history.RunHistory;
import app.rootology.facts.SourceCatalog;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.usage.UsageService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.HumanAnswer;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.QuestionContext.Choice;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * A free question, answered from the curated knowledge base, with the sources behind the answer (#31)
 * and the context it was answered in (#55). An escalated question waits in Escalations, like the others.
 * Inside a garden the question belongs to it; at the space level there is no garden, and the answer says
 * which defaults it used (#56).
 *
 * <p>The question runs off the UI thread and the page updates when the answer is ready (#82): the field and
 * its button stay where they are, the button is disabled meanwhile, and a card says what is being asked.
 * Answers of the visit stack below the field, the newest first, each with "ask again" and "copy". Everything
 * that belongs to the request or to the session is read here, on the UI thread, before the run starts.
 *
 * <p>The history is beside the question (#100): a button in the page header opens the context's past questions
 * as a column on the right, and the browser remembers whether it was open; on a phone the question and the
 * history take turns. At the space level the column holds every garden's questions, with a garden filter, and
 * the button says "History" next to the count where there is room (#109). A past question opens its answer
 * on the left, in the same stack. {@code ?history} opens the column (the old History links) and
 * {@code ?run=<process id>} opens one answer (a notification's link).
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/ask", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/ask", layout = MainLayout.class)
@PermitAll
public class AskView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** A question handed over by the home page (#31), answered as soon as the page opens. */
    static final String QUESTION = "q";
    /** Opens the history column: where the History page's old links lead (#100). */
    static final String HISTORY = "history";
    /** Opens the waiting questions (#132): every one on the left, the history filtered to them. */
    static final String WAITING = "waiting";
    /** Opens one past answer by the agent's process id: where an answered question's notification leads (#100). */
    static final String RUN = "run";
    /** Where the browser keeps whether the history column is open: a per-viewer convenience, never needed. */
    private static final String STORED = "rootology.ask.history";
    /** The phone's width (as in rootology.css), where the question and the history take turns. */
    private static final String WIDE = "(min-width: 641px)";

    private final GardenPipeline pipeline;
    private final SourceCatalog sources;
    private final CurrentUser currentUser;
    private final KnowledgeEnrichment enrichment;
    private final UsageService usage;
    private final GardenService gardens;
    private final CropCatalog crops;
    private final KnowledgeGraph knowledge;
    private final GeoNames geoNames;
    private final ClimateCells cells;
    private final HistoryPanel history;
    /** Opens and closes the history column: the count of the context's questions beside the clock (#100). */
    private final Button historyButton = new Button(VaadinIcon.CLOCK.create());
    /** The count on the history button, and the word beside it on a wide screen (#109). */
    private final Span historyCount = new Span();
    private final Span historyWord = new Span(getTranslation("history.title"));
    /** The question and the answers on the left, the history on the right; one at a time on a phone. */
    private final Div split = new Div();
    /** Where a question runs while the page stays usable (#82): the app's own, never the common pool. */
    private final Executor executor;
    private final Clock clock;
    private final Span questionsToday = new Span();
    /** The answers of this visit, the newest on top (#82): the thinking card during a run, then the answer. */
    private final Div answers = new Div();
    /** The suggested questions of the visit: they replace the long paragraph of instructions (#82). */
    private final Div suggested = new Div();
    /**
     * The suggestions and their label (#114): above the field on an empty page, under the newest item once there
     * is one, so the answer is the first thing read; there at most three, under "Altre domande".
     */
    private final Div suggestions = new Div();
    private final Div main = new Div();
    /** What the answer will assume, before the question (#110): the garden, its last frost, the month. */
    private final Span context = new Span();
    /** The place or garden a question needs before it can be answered (#107), under the field. */
    private final ChoiceCard choice;
    /** A guest only looks (#33): each question costs Claude and Jev calls and may wait for a member. */
    private final Paragraph guest = new Paragraph(getTranslation("ask.guest"));
    private final HorizontalLayout form = new HorizontalLayout();
    private final TextField question = new TextField();
    private final Button askButton = new Button();
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;
    /** The page's garden's name, so a card does not say it again (#121); null at the space level. */
    private String pageGarden;
    /** Whether this page's space lets the person ask; read once, on the UI thread, and passed on (#82). */
    private boolean contributor;
    /** One question at a time: a second click, a reused link or a chip starts nothing meanwhile. */
    private volatile boolean asking;

    @Autowired
    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment, UsageService usage, GardenService gardens,
                   CropCatalog crops, KnowledgeGraph knowledge, GeoNames geoNames, ClimateCells cells,
                   RunHistory runs, @Qualifier("applicationTaskExecutor") Executor executor, Clock clock) {
        this.gardens = gardens;
        this.crops = crops;
        this.knowledge = knowledge;
        this.geoNames = geoNames;
        this.cells = cells;
        this.enrichment = enrichment;
        this.usage = usage;
        this.pipeline = pipeline;
        this.sources = sources;
        this.currentUser = currentUser;
        this.executor = executor;
        this.clock = clock;
        this.history = new HistoryPanel(runs, pipeline, this::showPast, this::showWaiting, currentUser::namesOf,
                gardens, clock);
        this.choice = new ChoiceCard(gardens, geoNames, cells, currentUser, () -> asking, () -> contributor,
                this::continueQuestion);

        addClassName("rootology-ask");
        question.setLabel(getTranslation("ask.question"));
        question.setPlaceholder(getTranslation("ask.placeholder"));
        question.addClassName("rootology-ask-field");
        question.setMaxLength(Question.MAX_LENGTH);
        askButton.setText(getTranslation("ask.button"));
        askButton.addThemeName("primary");
        askButton.addClickListener(e -> ask(question.getValue()));
        // Enter in the field asks too, like the button: one question at a time all the same.
        question.addKeyDownListener(Key.ENTER, e -> ask(question.getValue()));
        questionsToday.addClassName("rootology-count-badge");
        form.addClassName("rootology-ask-form");
        form.add(question, askButton, questionsToday);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        suggested.addClassName("rootology-ask-chips");
        // A heading (#119): what follows is a list of its own, not part of the answer above it.
        H2 suggestionsLabel = new H2(getTranslation("ask.suggest.more"));
        suggestionsLabel.addClassName("rootology-ask-suggestions-label");
        suggestions.addClassName("rootology-ask-suggestions");
        suggestions.add(suggestionsLabel, suggested);
        context.addClassName("rootology-ask-context");
        answers.addClassName("rootology-ask-answers");
        guest.setVisible(false);
        historyButton.addClassName("rootology-history-toggle");
        historyButton.setTooltipText(getTranslation("history.title"));
        // The word says what the clock is on a wide screen; on a phone the icon keeps the room (#109). Its
        // tooltip and aria label are the word everywhere, so nothing is lost for a screen reader.
        historyWord.addClassName("rootology-wide-only");
        Div historyLabel = new Div(historyWord, historyCount);
        historyLabel.addClassName("rootology-history-label");
        historyButton.setSuffixComponent(historyLabel);
        historyButton.addClickListener(e -> {
            setHistoryOpen(!historyOpen(), true);
            // The column comes after the question in the page: the keyboard is taken straight to it.
            if (historyOpen()) {
                history.focusFirst();
            }
        });
        // On a phone the history covers the question: this brings it back, like the gardens page's Map / List.
        Button backToAsk = new Button(getTranslation("ask.page"), VaadinIcon.COMMENT_ELLIPSIS_O.create(),
                e -> setHistoryOpen(false, true));
        backToAsk.addClassName("rootology-view-toggle");
        history.add(backToAsk);
        // Esc closes the history from inside it (#120): the same as the "back to the question" button on a phone,
        // and the keyboard goes back to the toggle that opened it. The keydown stays in the browser, so a page
        // full of history cards sends no key to the server; only Esc arrives as one event.
        getElement().addEventListener("rootology-escape", e -> {
            setHistoryOpen(false, true);
            historyButton.focus();
        });
        history.getElement().executeJs("""
                const history = this;
                if (history.__escape) return;
                history.__escape = true;
                history.addEventListener('keydown', (event) => {
                    if (event.key !== 'Escape') return;
                    event.preventDefault();
                    history.dispatchEvent(new CustomEvent('rootology-escape', {bubbles: true}));
                });
                """);
        main.add(suggestions, guest, context, form, choice, answers);
        main.addClassName("rootology-ask-main");
        split.addClassName("rootology-ask-split");
        split.add(main, history);
        setHistoryOpen(false, false);
        add(new PageHeader(getTranslation("ask.title"), historyButton), split);
        showQuestionsToday();
    }

    /** Tests build the page outside Spring (#56): the question then runs on the calling thread. */
    AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
            KnowledgeEnrichment enrichment, UsageService usage, GardenService gardens,
            CropCatalog crops, KnowledgeGraph knowledge, GeoNames geoNames, ClimateCells cells,
            RunHistory runs, Clock clock) {
        this(pipeline, sources, currentUser, enrichment, usage, gardens, crops, knowledge, geoNames, cells, runs,
                Runnable::run, clock);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        pageGarden = where.get().gardenName();
        // The person's role is the one they have in the page's space, known only now (#56).
        contributor = currentUser.canContribute(spaceId);
        form.setVisible(contributor);
        guest.setVisible(!contributor);
        // A choice belongs to the question that asked it: a page that opens again starts without one (#107).
        choice.hide();
        showContext();
        showSuggestions();
        loadHistory();
        QueryParameters query = event.getLocation().getQueryParameters();
        if (query.getParameters().containsKey(WAITING)) {
            showAllWaiting();
        } else if (query.getParameters().containsKey(HISTORY)) {
            setHistoryOpen(true, false);
        } else {
            restoreHistoryOpen();
        }
        query.getSingleParameter(RUN).flatMap(history::run).ifPresent(this::showPast);
        query.getSingleParameter(QUESTION).filter(q -> !q.isBlank()).ifPresent(this::ask);
        if (contributor && focusFieldOnOpen(query)) {
            focusQuestion();
        }
    }

    /**
     * Whether the question field takes the keyboard when the page opens (#113): with nothing else to show, so
     * not with an answer opened by {@code ?run=}, the history by {@code ?history} or a question by {@code ?q=}.
     * The browser decides the rest: the field is focused on a wide screen only, since on a phone it would
     * open the keyboard over the page.
     */
    static boolean focusFieldOnOpen(QueryParameters query) {
        return !query.getParameters().containsKey(HISTORY)
                && !query.getParameters().containsKey(WAITING)
                && !query.getParameters().containsKey(RUN)
                && !query.getParameters().containsKey(QUESTION);
    }

    /**
     * Puts the keyboard in the field, where the person's first Tab would land anyway (#113). On a first load
     * the field is not yet a text field when this runs, so it waits for the element and the next frame.
     */
    private void focusQuestion() {
        question.getElement().executeJs("const field = this; customElements.whenDefined(field.localName)"
                + ".then(() => requestAnimationFrame(() => { if (window.matchMedia($0).matches) { field.focus(); } }))",
                WIDE);
    }

    /** The context's past questions, and their count on the button (#100). */
    private void loadHistory() {
        history.load(spaceId, gardenId, currentUser.login());
        int count = history.count();
        historyCount.setText(count == 0 ? "" : String.valueOf(count));
        historyButton.setAriaLabel(getTranslation("ask.history", count));
    }

    /** The history column, for the tests. */
    /** The context line above the question, for the tests (#110). */
    String contextLine() {
        return context.getText();
    }

    HistoryPanel historyPanel() {
        return history;
    }

    /** Whether the history column is open; on a phone, whether the history is the one shown. */
    boolean historyOpen() {
        return "open".equals(split.getElement().getAttribute("data-history"));
    }

    /** Opens or closes the history; remembered in the browser only when chosen on a wide screen (#100). */
    private void setHistoryOpen(boolean open, boolean remember) {
        split.getElement().setAttribute("data-history", open ? "open" : "closed");
        historyButton.getElement().setAttribute("aria-pressed", String.valueOf(open));
        if (remember) {
            getElement().executeJs("try { if (window.matchMedia($0).matches) localStorage.setItem($1, $2); } catch (e) {}",
                    WIDE, STORED, open ? "open" : "closed");
        }
    }

    /** The column as the browser left it, on a wide screen only: a phone always opens on the question. */
    private void restoreHistoryOpen() {
        getElement().executeJs("try { return window.matchMedia($0).matches && localStorage.getItem($1) === 'open'; }"
                        + " catch (e) { return false; }", WIDE, STORED)
                .then(Boolean.class, open -> setHistoryOpen(Boolean.TRUE.equals(open), false));
    }

    /**
     * A past question's answer on the left, on top of the stack (#100): with "ask again" and "copy", like the
     * answers of the visit, and once only. On a phone the question comes back into view.
     */
    private void showPast(PipelineRunRecord run) {
        String key = "past-" + run.getId();
        answers.getChildren().filter(c -> key.equals(c.getId().orElse(null))).toList().forEach(answers::remove);
        AnswerCard card;
        String copyText = null;
        if (run.isOffDomain()) {
            card = new AnswerCard();
            // The page's garden is already in the breadcrumb and in its context line (#121, #123).
            history.contextOf(run).ifPresent(snapshot -> card.context(snapshot, pageGarden));
            card.refused(run.getAnswerText() == null ? getTranslation("ask.offdomain") : run.getAnswerText());
        } else if (run.isDecisionEscalated() && !run.isAnsweredByHuman()) {
            card = new AnswerCard();
            history.contextOf(run).ifPresent(snapshot -> card.context(snapshot, pageGarden));
            card.promise(run.getDecisionReason());
        } else {
            // The same card as a fresh answer (#110): its notes and sources too, the context in the same place.
            // A stored answer with no sources says none was recorded (#111).
            PipelineAnswer answer = history.answerOf(run);
            copyText = answer.text();
            card = AnswerCards.ofPast(answer, run.getFinishedAt(), zone(run.getGardenId()),
                    run.getSources() != null, spaceId, currentUser, pageGarden, sources, enrichment, this);
        }
        Div item = item(run.getQuestion(), run.getFinishedAt(), contributor, copyText, card);
        item.setId(key);
        stack(item);
        history.opened(key);
        showOnPhone(item);
    }

    /**
     * A question still waiting for a person, from the history (#104): the same card as Escalations, on top of the
     * stack and once only, so a contributor answers it here and the asker follows it without leaving Ask.
     */
    private void showWaiting(PendingReview review) {
        String key = "waiting-" + review.processId();
        answers.getChildren().filter(c -> key.equals(c.getId().orElse(null))).toList().forEach(answers::remove);
        String login = currentUser.login();
        WaitingQuestionCard card = new WaitingQuestionCard(review, contributor, contributor ? suggestions() : List.of(),
                (values, note) -> {
                    // The question's own garden (#109): at the space level the history holds every garden's.
                    var answer = pipeline.respond(review.processId(), spaceId, review.context().gardenId(),
                            new HumanAnswer(values, note, login));
                    loadHistory();
                    return answer;
                },
                answer -> AnswerCards.of(answer, clock.instant(), zone(gardenId), spaceId, currentUser, pageGarden, sources,
                        enrichment, this),
                // At the space level the question may be another garden's: the card says which (#109).
                gardenId == 0, true, pageGarden);
        card.askedBy(currentUser.nameOf(review.context().startedBy()));
        if (contributor) {
            card.extra(propose(review.question().text()));
        }
        Div item = item(review.question().text(), review.askedAt(), contributor, null, card);
        item.setId(key);
        stack(item);
        history.opened(key);
        showOnPhone(item);
    }

    /**
     * The waiting questions of the page's context on the left, the newest on top, and the history filtered to
     * them (#132): what the old waiting questions page showed, now on Questions, where every card is answered.
     */
    private void showAllWaiting() {
        setHistoryOpen(true, false);
        history.filter(new HistoryPanel.Filters(HistoryPanel.Outcome.WAITING, false, null, null));
        String login = currentUser.login();
        List<PendingReview> waiting = gardenId > 0 ? pipeline.pending(spaceId, gardenId, login)
                : pipeline.pendingInSpace(spaceId, login);
        // Each card goes on top of the stack, so the oldest first leaves the newest on top.
        waiting.stream().sorted(java.util.Comparator.comparing(PendingReview::askedAt)).forEach(this::showWaiting);
    }

    /** On a phone the question comes back into view, with the item just opened (#100). */
    private void showOnPhone(Div item) {
        getElement().executeJs("return window.matchMedia($0).matches", WIDE)
                .then(Boolean.class, wide -> {
                    if (!Boolean.TRUE.equals(wide)) {
                        setHistoryOpen(false, false);
                    }
                    item.scrollIntoView();
                });
    }

    /** Which question an answer is for, and when it was asked, above a past answer (#100). */
    private Div asked(String text, String when) {
        Span date = new Span(when);
        date.addClassName("rootology-ask-asked-when");
        Span words = new Span(text);
        words.addClassName("rootology-ask-asked-question");
        Div asked = new Div(date, words);
        asked.addClassName("rootology-ask-asked");
        return asked;
    }

    /**
     * The context the answer will use, said before the question (#110), in the words of the answer card's line:
     * the garden, the last frost of its site when it has one, and the month. At the space level the month, and
     * that a place is asked for when a question needs one.
     */
    private void showContext() {
        Locale locale = NoteText.locale(this);
        String month = today().getMonth().getDisplayName(TextStyle.FULL, locale);
        if (gardenId == 0) {
            context.setText(getTranslation("ask.context.nogarden", month));
            return;
        }
        List<String> parts = new ArrayList<>();
        gardens.garden(spaceId, currentUser.login(), gardenId).ifPresent(garden -> {
            parts.add(garden.name());
            if (garden.latitude() != null && garden.longitude() != null) {
                cells.nearest(garden.latitude().doubleValue(), garden.longitude().doubleValue())
                        .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null))
                        .ifPresent(profile -> parts.add(profile.lastFrostDay() == null
                                ? getTranslation("context.climate.none")
                                : getTranslation("ask.context.lastfrost",
                                ClimateText.frost(profile.lastFrostDay(), this::getTranslation, locale))));
            }
        });
        parts.add(month);
        context.setText(String.join(" · ", parts));
    }

    /** Chips instead of a paragraph (#82): the month, what grows here now, and never for a guest (#33). */
    private void showSuggestions() {
        suggested.removeAll();
        int month = today().getMonthValue();
        List<SuggestedQuestions.Suggestion> chips = contributor
                ? SuggestedQuestions.of(month, gardenId > 0, growingCropNames(), onePlotName())
                : List.of();
        for (SuggestedQuestions.Suggestion chip : chips) {
            String text = chip.text(this::getTranslation);
            Button button = new Button(text, e -> ask(text));
            button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            button.addClassName("rootology-ask-chip");
            suggested.add(button);
        }
        ChipGroup.roving(suggested, getTranslation("ask.suggest.more"));
        suggestions.setVisible(!chips.isEmpty());
    }

    /** An item on top of the visit's stack, and the suggestions under it (#114). */
    private void stack(Component item) {
        answers.addComponentAsFirst(item);
        placeSuggestions();
    }

    /**
     * The suggestions above the field while the page is empty, and after the stack as soon as there is an item
     * (#114, #119): the answer is what the person came for, on a phone five chips pushed it out of view, and
     * between the newest item and the older ones they split the stack in two.
     */
    private void placeSuggestions() {
        boolean anyItem = answers.getChildren().anyMatch(c -> c != suggestions);
        suggestions.removeFromParent();
        if (anyItem) {
            answers.add(suggestions);
        } else {
            main.addComponentAsFirst(suggestions);
        }
        suggestions.getElement().getClassList().set("rootology-ask-suggestions-later", anyItem);
    }

    /** Whether the suggestions sit after the stack's items, for the tests (#114, #119). */
    boolean suggestionsUnderAnswer() {
        List<Component> items = answers.getChildren().toList();
        return suggestions.getParent().filter(answers::equals).isPresent() && items.size() > 1
                && items.indexOf(suggestions) == items.size() - 1;
    }

    /** Today at the page's garden (#158), or the app's own day at the space level. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone(gardenId));
    }

    /** The zone of one of the space's gardens by id (#158), or the app's own when it is unknown. */
    private ZoneId zone(Long id) {
        GardenSummary garden = id == null || id == 0 ? null
                : gardens.garden(spaceId, currentUser.login(), id).orElse(null);
        return GardenTime.zone(garden, clock);
    }

    /** What grows in this garden now, named in the page's language, each crop once (#82). */
    private List<String> growingCropNames() {
        if (gardenId <= 0) {
            return List.of();
        }
        boolean english = NoteText.locale(this).getLanguage().equals("en");
        List<String> names = new ArrayList<>();
        try {
            gardens.growingOn(spaceId, currentUser.login(), gardenId, today())
                    .forEach(p -> {
                        String name = crops.byId(p.cropId())
                                // A sentence names the crop with its article (#111): "la fragola", "l’aglio".
                                .map(c -> english ? c.englishName() : c.italianInSentence())
                                .orElse(p.cropName());
                        if (!names.contains(name) && names.size() < SuggestedQuestions.MAX_CROPS) {
                            names.add(name);
                        }
                    });
        } catch (RuntimeException ex) {
            // A garden that cannot be read gives the general chips only: a suggestion is never worth a failure.
            return List.of();
        }
        return names;
    }

    /** One plot of this garden, for the "what can I sow in plot X" chip; null when there is none (#82). */
    private String onePlotName() {
        if (gardenId <= 0) {
            return null;
        }
        try {
            return gardens.plots(spaceId, currentUser.login(), gardenId).stream()
                    .map(PlotSummary::name).findFirst().orElse(null);
        } catch (RuntimeException ex) {
            // A suggestion is never worth a failure: no plot chip.
            return null;
        }
    }

    private void ask(String text) {
        ask(text, false);
    }

    /**
     * Runs the question in the background (#82) and returns at once; askAnyway skips the domain filter once,
     * for the "Ask anyway" button of a refusal (#53).
     */
    private void ask(String text, boolean askAnyway) {
        if (text == null || text.isBlank() || asking) {
            return;
        }
        String asked = text.trim();
        // Everything the run needs from the session is read here: the background thread never touches it.
        RunContext context = context();
        String login = currentUser.login();
        boolean mayAsk = contributor;
        // Before the page is attached (the hand-off of the home page, #31) getUI() is still empty: the push
        // update needs the UI of the navigation itself.
        UI ui = getUI().or(() -> Optional.ofNullable(UI.getCurrent())).orElse(null);
        asking = true;
        askButton.setEnabled(false);
        // A new question replaces whatever the previous one waited for (#107).
        choice.hide();
        // The question moves into its card, like a chat: the field is free for the next one, and every card
        // has "ask again" to bring the text back.
        question.clear();
        ThinkingCard thinking = new ThinkingCard(asked, getTranslation("card.thinking.seconds"));
        stack(thinking);
        QuestionRunner.start(executor, ui, this,
                () -> pipeline.askKnowledgeBase(new Question("ask", asked), context, askAnyway),
                () -> asking = false, outcome(thinking, asked, context, login, mayAsk));
    }

    /** What a question of this page does once it ends, back on the UI thread. */
    private QuestionRunner.Outcome outcome(ThinkingCard thinking, String asked, RunContext context, String login,
                                           boolean mayAsk) {
        return new QuestionRunner.Outcome() {
            @Override
            public void finished(GardenPipeline.Run run) {
                AskView.this.finished(thinking, asked, run, context, login, mayAsk);
            }

            @Override
            public void failed(String text) {
                AskView.this.failed(thinking, asked, text, mayAsk);
            }

            @Override
            public void failedWith(RuntimeException ex) {
                AskView.this.failedWith(thinking, ex);
            }
        };
    }

    /** Back on the UI thread: the thinking card goes and the answer takes its place, above the older ones. */
    private void finished(ThinkingCard thinking, String asked, GardenPipeline.Run run, RunContext context,
                          String login, boolean mayAsk) {
        clear(thinking);
        if (run.offDomain()) {
            // A fixed text in the UI's language: no Claude call detected the question's language (#53).
            AnswerCard card = new AnswerCard().refused(getTranslation("ask.offdomain"));
            if (mayAsk) {
                card.extra(new Button(getTranslation("ask.offdomain.anyway"), e -> ask(asked, true)));
            }
            stack(item(asked, Instant.now(), mayAsk, null, card));
            return;
        }
        if (run.choice() != null) {
            // The question needs a place or a garden (#107): one line and a chip per option, and a tap runs it again.
            choice.show(run, asked, spaceId);
            return;
        }
        if (run.answer() == null) {
            // The same card as Escalations (#86): the person answers without leaving Ask.
            WaitingQuestionCard card = new WaitingQuestionCard(run.pending(), mayAsk, suggestions(),
                    (values, note) -> pipeline.respond(run.pending().processId(), context.spaceId(), context.gardenId(),
                            new HumanAnswer(values, note, login)),
                    answer -> AnswerCards.of(answer, clock.instant(), zone(context.gardenId()), context.spaceId(),
                            currentUser, pageGarden, sources, enrichment, this),
                    false, true, pageGarden);
            card.askedBy(currentUser.nameOf(run.pending().context().startedBy()));
            card.extra(propose(asked));
            stack(item(asked, run.pending().askedAt(), mayAsk, null, card));
            return;
        }
        AnswerCard card = AnswerCards.of(run.answer(), clock.instant(), zone(context.gardenId()), context.spaceId(),
                currentUser, pageGarden, sources, enrichment, this);
        // A place's answer can become a garden there, with every field already filled in (#107).
        choice.gardenHere(run, spaceId).ifPresent(card::extra);
        stack(item(asked, Instant.now(), mayAsk, run.answer().text(), card));
    }

    /**
     * Runs the waiting question again, with the place the person chose already resolved or in the garden they
     * chose (#107): no second understanding call and no daily limit, so it is a continuation, not a new
     * question. The choice has already gone, and a second tap meanwhile starts nothing.
     */
    private void continueQuestion(String asked, Intent intent, Choice.Place place, long gardenId) {
        if (asking || intent == null) {
            return;
        }
        RunContext context = context();
        String login = currentUser.login();
        boolean mayAsk = contributor;
        UI ui = getUI().or(() -> Optional.ofNullable(UI.getCurrent())).orElse(null);
        asking = true;
        askButton.setEnabled(false);
        ThinkingCard thinking = new ThinkingCard(asked, getTranslation("card.thinking.seconds"));
        stack(thinking);
        QuestionRunner.Outcome here = outcome(thinking, asked, context, login, mayAsk);
        QuestionRunner.start(executor, ui, this,
                () -> gardenId > 0
                        ? pipeline.continueWith(new Question("ask", asked), intent, context, gardenId)
                        : pipeline.continueWith(new Question("ask", asked), intent, context, place),
                () -> asking = false, gardenId <= 0 ? here : new QuestionRunner.Outcome() {
                    @Override
                    public void finished(GardenPipeline.Run run) {
                        continuedIn(run, asked, gardenId, thinking);
                    }

                    @Override
                    public void failed(String text) {
                        here.failed(text);
                    }

                    @Override
                    public void failedWith(RuntimeException ex) {
                        here.failedWith(ex);
                    }
                });
    }

    /**
     * A question continued in another garden (#107): its answer opens there, by the agent's process id, or the
     * garden's history when it waits for a person. The answer never shows on the page of another garden.
     */
    private void continuedIn(GardenPipeline.Run run, String asked, long gardenId, ThinkingCard thinking) {
        clear(thinking);
        if (run.choice() != null) {
            // Still a choice (the garden went away meanwhile, say): the chips come back here.
            choice.show(run, asked, spaceId);
            return;
        }
        Map<String, String> route = Map.of(Routed.SPACE, String.valueOf(spaceId),
                Routed.GARDEN, String.valueOf(gardenId));
        Map<String, String> query = run.answer() != null && run.processId() != null
                ? Map.of(RUN, run.processId()) : Map.of(HISTORY, "");
        getUI().ifPresent(ui -> ui.navigate(AskView.class, new RouteParameters(route),
                QueryParameters.simple(query)));
    }


    /** The question could not be answered (#82): what to say instead of the answer, in the same stack. */
    private void failed(ThinkingCard thinking, String asked, String text, boolean mayAsk) {
        clear(thinking);
        stack(item(asked, Instant.now(), mayAsk, null, new Paragraph(text)));
    }

    /**
     * The question failed for a reason the person did not cause, or was refused. A refusal means the access changed
     * while the page was open (#166): blocked, or no longer a member. Reading the page again goes through
     * AccessRouting, which sends the person where they belong; the stack and the history are not read again.
     */
    private void failedWith(ThinkingCard thinking, RuntimeException ex) {
        if (ex instanceof SpaceAccessException) {
            getUI().ifPresent(u -> u.getPage().reload());
            return;
        }
        clear(thinking);
        UiErrors.show(this, ex);
    }

    /** The page is usable again: the thinking card goes, the button comes back and the count is re-read. */
    private void clear(ThinkingCard thinking) {
        answers.remove(thinking);
        placeSuggestions();
        askButton.setEnabled(true);
        showQuestionsToday();
        loadHistory();
    }

    /**
     * One item of the stack (#82), the same in every state (#113): when it was asked and the question, the card,
     * and under it "ask again" and "copy".
     */
    private Div item(String asked, Instant at, boolean mayAsk, String copyText, Component content) {
        Div item = new Div(asked(asked, DateText.dateTime(at == null ? Instant.now() : at, this)), content);
        item.addClassName("rootology-ask-item");
        HorizontalLayout actions = new HorizontalLayout();
        actions.addClassName("rootology-ask-actions");
        if (mayAsk) {
            actions.add(RowActions.again(getTranslation("ask.again"), e -> {
                question.setValue(asked);
                question.focus();
            }));
            if (copyText != null) {
                actions.add(RowActions.copy(getTranslation("ask.copy"), e -> copy(asked, copyText)));
            }
        }
        // Closing an item takes it off the page, nothing else (#119): the question stays in the history.
        actions.add(RowActions.close(getTranslation("ask.close"), e -> close(item)));
        item.add(actions);
        return item;
    }

    /** An item off the stack (#119); the history no longer marks it as the one open. */
    private void close(Div item) {
        item.getId().ifPresent(history::closed);
        answers.remove(item);
        placeSuggestions();
        question.focus();
    }

    /** The question and the answer's sentence, on the clipboard; the browser may refuse, so nothing depends on it. */
    private void copy(String asked, String answer) {
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", asked + "\n\n" + answer));
        Toasts.done(getTranslation("ask.copied"));
    }

    /** The context this page asks in (#56): its route's space and garden, never the session's. */
    RunContext context() {
        return currentUser.runContext(spaceId, gardenId);
    }

    /** Whether this page offers the question field, for the tests (#33, #56). */
    boolean canAsk() {
        return form.isVisible();
    }

    /** How many suggested questions the page shows, for the tests (#82). */
    int suggestionCount() {
        return suggested.getComponentCount();
    }

    /** The tabindex of each suggested question, for the tests: one Tab stop for the group (#120). */
    List<String> suggestionTabIndexes() {
        return suggested.getChildren().map(c -> c.getElement().getAttribute("tabindex")).toList();
    }

    /** What the question field holds, for the tests: empty once a question starts. */
    String typed() {
        return question.getValue();
    }

    /** Whether a question is running, for the tests: one at a time (#82). */
    boolean asking() {
        return asking;
    }

    /** The place or garden choice under the field, for the tests (#107). */
    ChoiceCard choice() {
        return choice;
    }

    /** The answers of the visit, for the tests (#107). */
    Div answersShown() {
        return answers;
    }

    /** The way to propose the missing fact: the knowledge page of this garden, or of the space (#24, #56). */
    private com.vaadin.flow.component.html.Anchor propose(String text) {
        // A plain link to Knowledge (spaces/{space}[/gardens/{garden}]/knowledge), which the router follows in place.
        String path = "spaces/" + spaceId + (gardenId > 0 ? "/gardens/" + gardenId : "") + "/knowledge";
        return new com.vaadin.flow.component.html.Anchor(path + "?question="
                + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"), getTranslation("ask.propose"));
    }

    /** Few questions left: shown as a small badge; while there is room the count stays out of the way (#79). */
    private static final int SHOW_WHEN_LEFT = 5;

    /** Today's questions against the personal limit (#19), only when the person should start planning. */
    private void showQuestionsToday() {
        UsageService.Today today = usage.today(currentUser.login());
        questionsToday.setText(getTranslation("ask.today", today.used(), today.limit()));
        questionsToday.setVisible(fewLeft(today.used(), today.limit()));
    }

    /** The count is a badge only when it helps a decision (#79): five questions left, or fewer. */
    static boolean fewLeft(int used, int limit) {
        return limit - used <= SHOW_WHEN_LEFT;
    }

    /** The values a person can add: the catalogue's crops and the knowledge base's organisms, in the page's language. */
    private List<String> suggestions() {
        boolean english = NoteText.locale(this).getLanguage().equals("en");
        return WaitingQuestionCard.suggestions(
                crops.all().stream().map(c -> english ? c.englishName() : c.italianName()),
                knowledge.organisms().stream().map(o -> english ? o.englishName() : o.italianName()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("ask.page") + " · Rootology";
    }
}
