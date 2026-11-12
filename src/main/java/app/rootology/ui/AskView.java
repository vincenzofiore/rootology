package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
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
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.garden.GardenService;
import app.rootology.facts.SourceCatalog;
import app.rootology.resilience.ServiceUnavailableException;
import app.rootology.usage.UsageLimitException;
import app.rootology.usage.UsageService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.HumanAnswer;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import jakarta.annotation.security.PermitAll;

import java.util.List;

/**
 * A free question, answered from the curated knowledge base, with the sources behind the answer (#31)
 * and the context it was answered in (#55). An escalated question waits in Escalations, like the others.
 * Inside a garden the question belongs to it; at the space level there is no garden, and the answer says
 * which defaults it used (#56).
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/ask", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/ask", layout = MainLayout.class)
@PermitAll
public class AskView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** A question handed over by the home page (#31), answered as soon as the page opens. */
    static final String QUESTION = "q";

    private final GardenPipeline pipeline;
    private final SourceCatalog sources;
    private final CurrentUser currentUser;
    private final KnowledgeEnrichment enrichment;
    private final UsageService usage;
    private final GardenService gardens;
    private final CropCatalog crops;
    private final KnowledgeGraph knowledge;
    private final Span questionsToday = new Span();
    private final Div result = new Div();
    /** A guest only looks (#33): each question costs Claude and Jev calls and may wait for a member. */
    private final Paragraph guest = new Paragraph(getTranslation("ask.guest"));
    private final HorizontalLayout form = new HorizontalLayout();
    private final TextField question = new TextField();
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;

    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment, UsageService usage, GardenService gardens,
                   CropCatalog crops, KnowledgeGraph knowledge) {
        this.gardens = gardens;
        this.crops = crops;
        this.knowledge = knowledge;
        this.enrichment = enrichment;
        this.usage = usage;
        this.pipeline = pipeline;
        this.sources = sources;
        this.currentUser = currentUser;

        question.setLabel(getTranslation("ask.question"));
        question.setPlaceholder(getTranslation("ask.placeholder"));
        question.setWidth("36rem");
        question.setMaxLength(Question.MAX_LENGTH);
        Button ask = new Button(getTranslation("ask.button"), e -> ask(question.getValue()));
        ask.addThemeName("primary");
        questionsToday.addClassName("rootology-count-badge");
        form.add(question, ask, questionsToday);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        guest.setVisible(false);
        add(new H3(getTranslation("ask.title")), new Paragraph(getTranslation("ask.intro")), guest, form, result);
        showQuestionsToday();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        // The person's role is the one they have in the page's space, known only now (#56).
        boolean contributor = currentUser.canContribute(spaceId);
        form.setVisible(contributor);
        guest.setVisible(!contributor);
        event.getLocation().getQueryParameters().getSingleParameter(QUESTION).filter(q -> !q.isBlank()).ifPresent(q -> {
            question.setValue(q);
            ask(q);
        });
    }

    private void ask(String text) {
        ask(text, false);
    }

    /** askAnyway skips the domain filter once, for the "Ask anyway" button of a refusal (#53). */
    private void ask(String text, boolean askAnyway) {
        result.removeAll();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text.trim()), context(), askAnyway);
            if (run.offDomain()) {
                // A fixed text in the UI's language: no Claude call detected the question's language (#53).
                AnswerCard card = new AnswerCard().refused(getTranslation("ask.offdomain"));
                if (currentUser.canContribute(spaceId)) {
                    card.extra(new Button(getTranslation("ask.offdomain.anyway"), e -> ask(text, true)));
                }
                result.add(card);
                return;
            }
            if (run.answer() == null) {
                // The same card as Escalations (#86): the person answers without leaving Ask.
                WaitingQuestionCard card = new WaitingQuestionCard(run.pending(),
                        currentUser.canContribute(spaceId), suggestions(),
                        (values, note) -> pipeline.respond(run.pending().processId(), spaceId, gardenId,
                                new HumanAnswer(values, note, currentUser.login())),
                        answer -> AnswerCards.of(answer, spaceId, currentUser.login(), sources, enrichment, this));
                card.extra(propose(text.trim()));
                result.add(card);
                return;
            }
            show(run.answer());
        } catch (UsageLimitException ex) {
            result.add(new Paragraph(getTranslation("ask.limit", ex.limit())));
        } catch (ServiceUnavailableException ex) {
            result.add(new Paragraph(getTranslation("error.claude")));
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        } finally {
            showQuestionsToday();
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

    private void show(PipelineAnswer answer) {
        result.add(AnswerCards.of(answer, spaceId, currentUser.login(), sources, enrichment, this));
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
