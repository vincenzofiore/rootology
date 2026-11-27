package app.rootology.ui;

import app.rootology.decision.Intent;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.HumanAnswer;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.QuestionContext.Choice;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceAccessException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A question asked on the garden home, answered where it was asked (#178): a modal dialog with the thinking card,
 * then the answer with the same notes and sources as Ask, and "OK" to stay or "Go to questions" to open it there.
 * The less common outcomes stay in the dialog too: an off-topic refusal with "Ask anyway", the choice of a place
 * or a garden (#107), a question waiting for a person (#86), the daily limit. Closing it while it thinks does not
 * stop the question: its answer lands in the history, as on Ask.
 */
class QuestionDialog extends Dialog {

    private final QuestionDialogs services;
    private final long spaceId;
    private final long gardenId;
    private final String gardenName;
    private final boolean mayAsk;
    /** What the dialog shows: the thinking card, then the outcome. */
    private final Div body = new Div();
    private final ChoiceCard choice;
    /** The run of the answer shown, so "Go to questions" opens that one. */
    private Optional<String> processId = Optional.empty();
    /** Whether the question waits for a person: "Go to questions" opens the waiting ones. */
    private boolean waiting;
    /** One question at a time: "Ask anyway" or a chip starts nothing while one runs. */
    private volatile boolean asking;

    QuestionDialog(QuestionDialogs services, long spaceId, long gardenId, String gardenName) {
        this.services = services;
        this.spaceId = spaceId;
        this.gardenId = gardenId;
        this.gardenName = gardenName;
        this.mayAsk = services.currentUser.canContribute(spaceId);
        this.choice = new ChoiceCard(services.gardens, services.geoNames, services.cells, services.currentUser,
                () -> asking, () -> mayAsk, this::continueQuestion);
        setHeaderTitle(getTranslation("ask.question"));
        addClassName("rootology-question-dialog");
        body.addClassName("rootology-question-dialog-body");
        add(body);
        Button toQuestions = new Button(getTranslation("home.ask.toquestions"), e -> goToQuestions());
        toQuestions.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button ok = new Button(getTranslation("home.ask.ok"), e -> close());
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(toQuestions, ok);
    }

    /** Runs the question in the background; askAnyway skips the domain filter once, for "Ask anyway" (#53). */
    void ask(String asked, boolean askAnyway) {
        if (asking) {
            return;
        }
        RunContext context = services.currentUser.runContext(spaceId, gardenId);
        start(asked, () -> services.pipeline.askKnowledgeBase(new Question("ask", asked), context, askAnyway),
                outcome(asked, context));
    }

    /**
     * The question again with the place or the garden the person chose (#107), as on Ask: no second understanding
     * call. In another garden its answer opens on that garden's Questions page, never in this garden's dialog.
     */
    private void continueQuestion(String asked, Intent intent, Choice.Place place, long chosenGarden) {
        if (asking || intent == null) {
            return;
        }
        RunContext context = services.currentUser.runContext(spaceId, gardenId);
        QuestionRunner.Outcome here = outcome(asked, context);
        start(asked, () -> chosenGarden > 0
                ? services.pipeline.continueWith(new Question("ask", asked), intent, context, chosenGarden)
                : services.pipeline.continueWith(new Question("ask", asked), intent, context, place),
                chosenGarden <= 0 ? here : new QuestionRunner.Outcome() {
                    @Override
                    public void finished(GardenPipeline.Run run) {
                        if (run.choice() != null) {
                            here.finished(run);
                            return;
                        }
                        close();
                        Map<String, String> query = run.answer() != null && run.processId() != null
                                ? Map.of(AskView.RUN, run.processId()) : Map.of(AskView.HISTORY, "");
                        toAsk(chosenGarden, query);
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

    private void start(String asked, java.util.function.Supplier<GardenPipeline.Run> work,
                       QuestionRunner.Outcome outcome) {
        asking = true;
        processId = Optional.empty();
        waiting = false;
        choice.hide();
        show(new ThinkingCard(asked, getTranslation("card.thinking.seconds")));
        // The dialog is not attached yet on the first question: the push update needs the UI of the click.
        UI ui = getUI().or(() -> Optional.ofNullable(UI.getCurrent())).orElse(null);
        QuestionRunner.start(services.executor, ui, this, work, () -> asking = false, outcome);
    }

    /** What the dialog does once a question ends, back on the UI thread. */
    private QuestionRunner.Outcome outcome(String asked, RunContext context) {
        return new QuestionRunner.Outcome() {
            @Override
            public void finished(GardenPipeline.Run run) {
                QuestionDialog.this.finished(asked, run, context);
            }

            @Override
            public void failed(String text) {
                show(asked(asked), new Paragraph(text));
            }

            @Override
            public void failedWith(RuntimeException ex) {
                if (ex instanceof SpaceAccessException) {
                    // The access changed meanwhile (#166): reading the page again sends the person where they belong.
                    getUI().ifPresent(u -> u.getPage().reload());
                    return;
                }
                close();
                UiErrors.show(QuestionDialog.this, ex);
            }
        };
    }

    /** The outcome in the dialog, as Ask shows it in its stack. */
    private void finished(String asked, GardenPipeline.Run run, RunContext context) {
        if (run.offDomain()) {
            AnswerCard card = new AnswerCard().refused(getTranslation("ask.offdomain"));
            if (mayAsk) {
                card.extra(new Button(getTranslation("ask.offdomain.anyway"), e -> ask(asked, true)));
            }
            show(asked(asked), card);
            return;
        }
        if (run.choice() != null) {
            choice.show(run, asked, spaceId);
            show(choice);
            return;
        }
        if (run.answer() == null) {
            // The same card as Ask and Escalations (#86): a member answers it here, without leaving the home.
            waiting = true;
            String login = services.currentUser.login();
            WaitingQuestionCard card = new WaitingQuestionCard(run.pending(), mayAsk, suggestions(),
                    (values, note) -> services.pipeline.respond(run.pending().processId(), context.spaceId(),
                            context.gardenId(), new HumanAnswer(values, note, login)),
                    answer -> AnswerCards.of(answer, services.clock.instant(), zone(context.gardenId()),
                            context.spaceId(), services.currentUser, gardenName, services.sources,
                            services.enrichment, this),
                    false, true, gardenName);
            card.askedBy(services.currentUser.nameOf(run.pending().context().startedBy()));
            show(asked(asked), card);
            return;
        }
        processId = Optional.ofNullable(run.processId());
        AnswerCard card = AnswerCards.of(run.answer(), services.clock.instant(), zone(context.gardenId()),
                context.spaceId(), services.currentUser, gardenName, services.sources, services.enrichment, this);
        choice.gardenHere(run, spaceId).ifPresent(card::extra);
        show(asked(asked), card);
    }

    /** The garden's own time zone (#158), or the app's own when the garden cannot be read. */
    private java.time.ZoneId zone(long gardenId) {
        app.rootology.garden.GardenViews.GardenSummary garden = gardenId == 0 ? null
                : services.gardens.garden(spaceId, services.currentUser.login(), gardenId).orElse(null);
        return app.rootology.garden.GardenTime.zone(garden, services.clock);
    }

    private void show(Component... content) {
        body.removeAll();
        body.add(content);
    }

    /** The question above its outcome, as the thinking card showed it. */
    private H4 asked(String asked) {
        H4 question = new H4(asked);
        question.addClassName("rootology-thinking-question");
        return question;
    }

    /** The values a person can add when answering, in the page's language, as on Ask. */
    private List<String> suggestions() {
        boolean english = NoteText.locale(this).getLanguage().equals("en");
        return WaitingQuestionCard.suggestions(
                services.crops.all().stream().map(c -> english ? c.englishName() : c.italianName()),
                services.knowledge.organisms().stream().map(o -> english ? o.englishName() : o.italianName()));
    }

    /** This garden's Questions page: the answer shown, the waiting questions, or the history. */
    private void goToQuestions() {
        Map<String, String> query = questionsQuery();
        close();
        toAsk(gardenId, query);
    }

    /** The Questions page of the garden, or of the space for a question asked without one (#179). */
    private void toAsk(long garden, Map<String, String> query) {
        Map<String, String> route = garden > 0
                ? Map.of(Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(garden))
                : Map.of(Routed.SPACE, String.valueOf(spaceId));
        UI.getCurrent().navigate(AskView.class, new RouteParameters(route), QueryParameters.simple(query));
    }

    /** What the dialog shows now, for the tests. */
    Div body() {
        return body;
    }

    /** Where "Go to questions" leads now, for the tests: the run, the waiting questions or the history. */
    Map<String, String> questionsQuery() {
        return processId.map(run -> Map.of(AskView.RUN, run))
                .orElse(waiting ? Map.of(AskView.WAITING, "") : Map.of(AskView.HISTORY, ""));
    }
}
