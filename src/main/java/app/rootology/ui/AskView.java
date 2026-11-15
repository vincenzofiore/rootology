package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
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
import com.vaadin.flow.router.RouterLink;
import app.rootology.decision.Intent;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import app.rootology.history.PipelineRunRecord;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.history.RunHistory;
import app.rootology.facts.SourceCatalog;
import app.rootology.resilience.ServiceUnavailableException;
import app.rootology.usage.UsageLimitException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    private final Span questionsToday = new Span();
    /** The answers of this visit, the newest on top (#82): the thinking card during a run, then the answer. */
    private final Div answers = new Div();
    /** The suggested questions of the visit: they replace the long paragraph of instructions (#82). */
    private final Div suggested = new Div();
    /** The choice a question needs before it can be answered (#107): one line and a chip per option. */
    private final Div choice = new Div();
    private final Span choiceLine = new Span();
    /** The question the choice belongs to, so the person sees what the chips answer. */
    private final Span choiceQuestion = new Span();
    private final HorizontalLayout choiceChips = new HorizontalLayout();
    /** "Another place…" (#107): the same GeoNames search the garden dialog uses, opened on demand. */
    private ComboBox<Place> anotherPlace;
    /** The question waiting for a choice, with the intent Claude already understood: what a tap runs again. */
    private String waitingQuestion;
    private Intent waitingIntent;
    /** A guest only looks (#33): each question costs Claude and Jev calls and may wait for a member. */
    private final Paragraph guest = new Paragraph(getTranslation("ask.guest"));
    private final HorizontalLayout form = new HorizontalLayout();
    private final TextField question = new TextField();
    private final Button askButton = new Button();
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;
    /** Whether this page's space lets the person ask; read once, on the UI thread, and passed on (#82). */
    private boolean contributor;
    /** One question at a time: a second click, a reused link or a chip starts nothing meanwhile. */
    private volatile boolean asking;

    @Autowired
    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment, UsageService usage, GardenService gardens,
                   CropCatalog crops, KnowledgeGraph knowledge, GeoNames geoNames, ClimateCells cells,
                   RunHistory runs, @Qualifier("applicationTaskExecutor") Executor executor) {
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
        this.history = new HistoryPanel(runs, pipeline, this::showPast, this::showWaiting);

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
        answers.addClassName("rootology-ask-answers");
        choice.addClassName("rootology-ask-choice");
        choiceLine.addClassName("rootology-ask-choice-line");
        choiceChips.addClassName("rootology-ask-chips");
        choiceChips.addClassName("rootology-ask-choice-chips");
        choice.setVisible(false);
        choice.add(choiceLine, choiceChips);
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
        Div main = new Div(suggested, guest, form, choice, answers);
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
            RunHistory runs) {
        this(pipeline, sources, currentUser, enrichment, usage, gardens, crops, knowledge, geoNames, cells, runs,
                Runnable::run);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        // The person's role is the one they have in the page's space, known only now (#56).
        contributor = currentUser.canContribute(spaceId);
        form.setVisible(contributor);
        guest.setVisible(!contributor);
        // A choice belongs to the question that asked it: a page that opens again starts without one (#107).
        hideChoice();
        showSuggestions();
        loadHistory();
        QueryParameters query = event.getLocation().getQueryParameters();
        if (query.getParameters().containsKey(HISTORY)) {
            setHistoryOpen(true, false);
        } else {
            restoreHistoryOpen();
        }
        query.getSingleParameter(RUN).flatMap(history::run).ifPresent(this::showPast);
        query.getSingleParameter(QUESTION).filter(q -> !q.isBlank()).ifPresent(this::ask);
    }

    /** The context's past questions, and their count on the button (#100). */
    private void loadHistory() {
        history.load(spaceId, gardenId, currentUser.login());
        int count = history.count();
        historyCount.setText(count == 0 ? "" : String.valueOf(count));
        historyButton.setAriaLabel(getTranslation("ask.history", count));
    }

    /** The history column, for the tests. */
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
        historyButton.getElement().getThemeList().set("primary", open);
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
            history.contextOf(run).ifPresent(card::context);
            card.refused(run.getAnswerText() == null ? getTranslation("ask.offdomain") : run.getAnswerText());
        } else if (run.isDecisionEscalated() && !run.isAnsweredByHuman()) {
            card = new AnswerCard();
            history.contextOf(run).ifPresent(card::context);
            card.promise(ReasonText.text(run.getDecisionReason(), this));
        } else {
            // The same card as a fresh answer (#110): its notes and sources too, the context in the same place.
            PipelineAnswer answer = history.answerOf(run);
            copyText = answer.text();
            card = AnswerCards.of(answer, spaceId, currentUser.login(), sources, enrichment, this);
        }
        Div item = item(run.getQuestion(), contributor, copyText, card);
        item.setId(key);
        item.addComponentAsFirst(asked(run.getQuestion(), DateText.dateTime(run.getFinishedAt(), this)));
        answers.addComponentAsFirst(item);
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
                answer -> AnswerCards.of(answer, spaceId, login, sources, enrichment, this),
                // At the space level the question may be another garden's: the card says which (#109).
                gardenId == 0, true);
        if (contributor) {
            card.extra(propose(review.question().text()));
        }
        Div item = item(review.question().text(), contributor, null, card);
        item.setId(key);
        answers.addComponentAsFirst(item);
        showOnPhone(item);
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

    /** Chips instead of a paragraph (#82): the month, what grows here now, and never for a guest (#33). */
    private void showSuggestions() {
        suggested.removeAll();
        int month = LocalDate.now(GardenHomeView.ITALY).getMonthValue();
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
        suggested.setVisible(!chips.isEmpty());
    }

    /** What grows in this garden now, named in the page's language, each crop once (#82). */
    private List<String> growingCropNames() {
        if (gardenId <= 0) {
            return List.of();
        }
        boolean english = NoteText.locale(this).getLanguage().equals("en");
        List<String> names = new ArrayList<>();
        try {
            gardens.growingOn(spaceId, currentUser.login(), gardenId, LocalDate.now(GardenHomeView.ITALY))
                    .forEach(p -> {
                        String name = crops.byId(p.cropId())
                                .map(c -> english ? c.englishName() : c.italianName())
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
        hideChoice();
        // The question moves into its card, like a chat: the field is free for the next one, and every card
        // has "ask again" to bring the text back.
        question.clear();
        ThinkingCard thinking = new ThinkingCard(asked, getTranslation("card.thinking.seconds"));
        answers.addComponentAsFirst(thinking);
        executor.execute(() -> {
            try {
                GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", asked), context, askAnyway);
                asking = false;
                update(ui, () -> finished(thinking, asked, run, context, login, mayAsk));
            } catch (UsageLimitException ex) {
                asking = false;
                int limit = ex.limit();
                update(ui, () -> failed(thinking, asked, getTranslation("ask.limit", limit), mayAsk));
            } catch (ServiceUnavailableException ex) {
                asking = false;
                update(ui, () -> failed(thinking, asked, getTranslation("error.claude"), mayAsk));
            } catch (RuntimeException ex) {
                asking = false;
                update(ui, () -> {
                    clear(thinking);
                    UiErrors.show(this, ex);
                });
            }
        });
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
            answers.addComponentAsFirst(item(asked, mayAsk, null, card));
            return;
        }
        if (run.choice() != null) {
            // The question needs a place or a garden (#107): one line and a chip per option, and a tap runs it again.
            showChoice(run, asked);
            return;
        }
        if (run.answer() == null) {
            // The same card as Escalations (#86): the person answers without leaving Ask.
            WaitingQuestionCard card = new WaitingQuestionCard(run.pending(), mayAsk, suggestions(),
                    (values, note) -> pipeline.respond(run.pending().processId(), context.spaceId(), context.gardenId(),
                            new HumanAnswer(values, note, login)),
                    answer -> AnswerCards.of(answer, context.spaceId(), login, sources, enrichment, this),
                    false, true);
            card.extra(propose(asked));
            answers.addComponentAsFirst(item(asked, mayAsk, null, card));
            return;
        }
        AnswerCard card = AnswerCards.of(run.answer(), context.spaceId(), login, sources, enrichment, this);
        if (mayAsk) {
            // A place's answer can become a garden there, with every field already filled in (#107).
            gardenHere(run).ifPresent(card::extra);
        }
        answers.addComponentAsFirst(item(asked, mayAsk, run.answer().text(), card));
    }

    /**
     * The choice a question needs before it can be answered (#107): one short line and a chip per option under
     * the field. A tap runs the same question again with that place or garden, and the answer is handled like
     * any other.
     */
    private void showChoice(GardenPipeline.Run run, String asked) {
        Choice needed = run.choice();
        waitingQuestion = asked;
        waitingIntent = run.intent();
        choiceLine.setText(choiceText(needed, run.intent()));
        choiceQuestion.setText(getTranslation("ask.choice.question", asked));
        choiceQuestion.addClassName("rootology-ask-choice-question");
        choiceChips.removeAll();
        choice.removeAll();
        choice.add(choiceQuestion, choiceLine, choiceChips);
        for (Choice.Place place : needed.places()) {
            choiceChips.add(chip(place.label(), () -> choosePlace(place)));
        }
        for (Choice.Garden garden : needed.gardens()) {
            choiceChips.add(chip(garden.name(), () -> chooseGarden(garden.id())));
        }
        if (needed.kind() == Choice.Kind.PLACE) {
            if (anotherPlaceAvailable()) {
                choiceChips.add(chip(getTranslation("ask.choice.otherplace"), this::openAnotherPlace));
                // The search opens only from its chip: most people tap one of the places offered.
                choice.add(anotherPlace());
                anotherPlace.setVisible(false);
            }
        } else if (needed.gardens().isEmpty()) {
            // The space has no garden yet: creating one is the only way to answer a garden question.
            choiceChips.add(chip(getTranslation("ask.choice.creategarden"), this::openNewGarden));
        }
        choice.setVisible(true);
        setChipsEnabled(true);
    }

    /** The line above the chips, naming what the question was looking for, in the page's language. */
    private String choiceText(Choice needed, Intent intent) {
        String asked = intent == null || intent.place() == null ? "" : intent.place();
        return switch (needed.kind()) {
            case PLACE -> switch (needed.why()) {
                case MISSING -> getTranslation("ask.choice.place");
                case NOT_FOUND -> getTranslation("ask.choice.place.notfound", asked);
                case AMBIGUOUS -> getTranslation("ask.choice.place.ambiguous", asked);
            };
            case GARDEN -> getTranslation("ask.choice.garden");
        };
    }

    /** One option, as a chip: a button with the look of the suggested questions, reachable from the keyboard. */
    private Button chip(String text, Runnable action) {
        Button button = new Button(text, e -> action.run());
        button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        button.addClassName("rootology-ask-chip");
        return button;
    }

    /** The choice goes with the question that asked it: a new question, or a tap, takes it away (#107). */
    private void hideChoice() {
        waitingQuestion = null;
        waitingIntent = null;
        choice.setVisible(false);
    }

    /** The chips stay visible but do nothing while the continuation runs, so one tap is one question. */
    private void setChipsEnabled(boolean enabled) {
        choiceChips.getChildren().filter(HasEnabled.class::isInstance).map(HasEnabled.class::cast)
                .forEach(c -> c.setEnabled(enabled));
    }

    /** A tap on a place chip: the same question again, with the place the person picked (#107). */
    private void choosePlace(Choice.Place place) {
        continueQuestion(place, 0);
    }

    /** A tap on a garden chip: the same question again, in the garden the person picked (#107). */
    private void chooseGarden(long gardenId) {
        continueQuestion(null, gardenId);
    }

    /**
     * Runs the waiting question again, with the place the person chose already resolved or in the garden they
     * chose (#107): no second understanding call and no daily limit, so it is a continuation, not a new
     * question. A second tap meanwhile starts nothing.
     */
    private void continueQuestion(Choice.Place place, long gardenId) {
        if (asking || waitingQuestion == null || waitingIntent == null) {
            return;
        }
        String asked = waitingQuestion;
        Intent intent = waitingIntent;
        RunContext context = context();
        String login = currentUser.login();
        boolean mayAsk = contributor;
        UI ui = getUI().or(() -> Optional.ofNullable(UI.getCurrent())).orElse(null);
        asking = true;
        askButton.setEnabled(false);
        setChipsEnabled(false);
        hideChoice();
        ThinkingCard thinking = new ThinkingCard(asked, getTranslation("card.thinking.seconds"));
        answers.addComponentAsFirst(thinking);
        executor.execute(() -> {
            try {
                GardenPipeline.Run run = gardenId > 0
                        ? pipeline.continueWith(new Question("ask", asked), intent, context, gardenId)
                        : pipeline.continueWith(new Question("ask", asked), intent, context, place);
                asking = false;
                if (gardenId > 0) {
                    update(ui, () -> continuedIn(run, asked, gardenId, thinking));
                } else {
                    update(ui, () -> finished(thinking, asked, run, context, login, mayAsk));
                }
            } catch (UsageLimitException ex) {
                asking = false;
                int limit = ex.limit();
                update(ui, () -> failed(thinking, asked, getTranslation("ask.limit", limit), mayAsk));
            } catch (ServiceUnavailableException ex) {
                asking = false;
                update(ui, () -> failed(thinking, asked, getTranslation("error.claude"), mayAsk));
            } catch (RuntimeException ex) {
                asking = false;
                update(ui, () -> {
                    clear(thinking);
                    UiErrors.show(this, ex);
                });
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
            showChoice(run, asked);
            return;
        }
        Map<String, String> route = Map.of(Routed.SPACE, String.valueOf(spaceId),
                Routed.GARDEN, String.valueOf(gardenId));
        Map<String, String> query = run.answer() != null && run.processId() != null
                ? Map.of(RUN, run.processId()) : Map.of(HISTORY, "");
        getUI().ifPresent(ui -> ui.navigate(AskView.class, new RouteParameters(route),
                QueryParameters.simple(query)));
    }

    /** Whether "another place" is offered: only when GeoNames is configured, and only to whoever asks (#30). */
    private boolean anotherPlaceAvailable() {
        return contributor && geoNames != null && geoNames.enabled();
    }

    /** The search field of "another place" (#107), built once: the same search as the garden dialog's. */
    private ComboBox<Place> anotherPlace() {
        if (anotherPlace == null) {
            anotherPlace = PlaceSearch.field(geoNames, getTranslation("ask.choice.search"), "",
                    NoteText.locale(this).getLanguage());
            anotherPlace.addClassName("rootology-ask-choice-search");
            anotherPlace.addValueChangeListener(e -> {
                Place found = e.getValue();
                if (found != null) {
                    choosePlace(new Choice.Place(found.name(), found.region(), found.countryCode(),
                            found.countryName(), BigDecimal.valueOf(found.latitude()),
                            BigDecimal.valueOf(found.longitude()), null));
                }
            });
        }
        return anotherPlace;
    }

    private void openAnotherPlace() {
        ComboBox<Place> search = anotherPlace();
        search.setVisible(true);
        search.focus();
    }

    /** The gardens overview with its new-garden dialog open: no garden, no garden question can be answered. */
    private void openNewGarden() {
        getUI().ifPresent(ui -> ui.navigate(GardensView.class,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))),
                QueryParameters.simple(Map.of(GardensView.NEW, ""))));
    }

    /**
     * The link under a place's answer that opens a garden there (#107), with the place's names, position,
     * country and altitude already in the dialog. Nothing is offered for an answer that used no place.
     */
    private Optional<Button> gardenHere(GardenPipeline.Run run) {
        ContextSnapshot context = run.answer() == null ? null : run.answer().context();
        if (run.place() == null || context == null || context.climateFrom() != ContextSnapshot.ClimateFrom.PLACE
                || hasGardenAt(run.place())) {
            return Optional.empty();
        }
        Button create = new Button(getTranslation("ask.createhere"), VaadinIcon.PLUS.create(),
                e -> createGardenAt(run.place()));
        create.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        create.addClassName("rootology-answer-create");
        return Optional.of(create);
    }

    /** True when one of the space's gardens is already in that town or within about 5 km of the place. */
    private boolean hasGardenAt(Choice.Place place) {
        return gardens.gardens(spaceId, currentUser.login()).stream().anyMatch(g ->
                (g.town() != null && g.town().strip().equalsIgnoreCase(place.name()))
                        || (g.latitude() != null && g.longitude() != null && place.latitude() != null
                        && place.longitude() != null
                        && g.latitude().subtract(place.latitude()).abs().doubleValue() < 0.05
                        && g.longitude().subtract(place.longitude()).abs().doubleValue() < 0.05));
    }

    /** The garden dialog on the place, and a toast that leads to the garden once it is saved (#107). */
    private void createGardenAt(Choice.Place place) {
        new GardenDialog(geoNames, cells, (name, town, latitude, longitude, countryCode, elevationM) ->
                gardens.saveGarden(spaceId, currentUser.login(), null, name, town, latitude, longitude,
                        countryCode, elevationM))
                .open(null, saved -> Toasts.opening(getTranslation("ask.createhere.done", saved.name()),
                        getTranslation("ask.createhere.open"), () -> openGarden(saved.id())), place);
    }

    /** A garden's home: where the toast of a garden just created leads (#107). */
    private void openGarden(long gardenId) {
        getUI().ifPresent(ui -> ui.navigate(GardenHomeView.class, new RouteParameters(Map.of(
                Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId)))));
    }

    /** The question could not be answered (#82): what to say instead of the answer, in the same stack. */
    private void failed(ThinkingCard thinking, String asked, String text, boolean mayAsk) {
        clear(thinking);
        answers.addComponentAsFirst(item(asked, mayAsk, null, new Paragraph(text)));
    }

    /** The page is usable again: the thinking card goes, the button comes back and the count is re-read. */
    private void clear(ThinkingCard thinking) {
        answers.remove(thinking);
        askButton.setEnabled(true);
        showQuestionsToday();
        loadHistory();
    }

    /** One answer of this visit (#82): the card, and under it "ask again" and "copy". */
    private Div item(String asked, boolean mayAsk, String copyText, Component content) {
        Div item = new Div(content);
        item.addClassName("rootology-ask-item");
        if (mayAsk) {
            HorizontalLayout actions = new HorizontalLayout();
            actions.addClassName("rootology-ask-actions");
            actions.add(RowActions.again(getTranslation("ask.again"), e -> question.setValue(asked)));
            if (copyText != null) {
                actions.add(RowActions.copy(getTranslation("ask.copy"), e -> copy(asked, copyText)));
            }
            item.add(actions);
        }
        return item;
    }

    /** The question and the answer's sentence, on the clipboard; the browser may refuse, so nothing depends on it. */
    private void copy(String asked, String answer) {
        getUI().ifPresent(ui -> ui.getPage().executeJs("navigator.clipboard.writeText($0)", asked + "\n\n" + answer));
        Toasts.done(getTranslation("ask.copied"));
    }

    /**
     * A change to the page from the background thread (#82). A page that is gone drops it quietly: the answer
     * is in History anyway.
     */
    private void update(UI ui, Runnable change) {
        if (ui == null) {
            change.run();
            return;
        }
        if (ui.isClosing()) {
            return;
        }
        try {
            ui.access(() -> {
                if (isAttached()) {
                    change.run();
                }
            });
        } catch (com.vaadin.flow.component.UIDetachedException | IllegalStateException detached) {
            // The visit ended while the question ran: nothing to show.
        }
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

    /** What the question field holds, for the tests: empty once a question starts. */
    String typed() {
        return question.getValue();
    }

    /** Whether a question is running, for the tests: one at a time (#82). */
    boolean asking() {
        return asking;
    }

    /** Whether the choice under the field is showing, for the tests (#107). */
    boolean choiceOpen() {
        return choice.isVisible();
    }

    /** The chips of the choice, for the tests: a click is one choice (#107). */
    List<Button> chips() {
        return choiceChips.getChildren().map(Button.class::cast).toList();
    }

    /** The line above the chips, for the tests: it names what the question was looking for (#107). */
    String choiceAsked() {
        return choiceLine.getText();
    }

    /** The answers of the visit, for the tests (#107). */
    Div answersShown() {
        return answers;
    }

    /** The way to propose the missing fact: the knowledge page of this garden, or of the space (#24, #56). */
    private RouterLink propose(String text) {
        java.util.Map<String, String> here = gardenId > 0
                ? java.util.Map.of(Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId))
                : java.util.Map.of(Routed.SPACE, String.valueOf(spaceId));
        RouterLink link = new RouterLink(getTranslation("ask.propose"), KnowledgeView.class,
                new RouteParameters(here));
        link.setQueryParameters(QueryParameters.simple(java.util.Map.of("question", text)));
        return link;
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
