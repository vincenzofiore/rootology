package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
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
import io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.enrichment.ProposalScope;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;
import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import io.github.vincenzofiore.rootology.usage.UsageLimitException;
import io.github.vincenzofiore.rootology.usage.UsageService;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.Question;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;

/**
 * A free question, answered from the curated knowledge base, with the sources behind the answer (#31).
 * An escalated question waits in Escalations, like the others. Inside a garden the question belongs to it;
 * at the space level there is no garden, and the answer uses the space's first garden for the zone until
 * #55 (#56).
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
    private final Span questionsToday = new Span();
    private final Div result = new Div();
    private final Paragraph noGarden = new Paragraph(getTranslation("ask.nogarden"));
    /** A guest only looks (#33): each question costs Claude and Jev calls and may wait for a member. */
    private final Paragraph guest = new Paragraph(getTranslation("ask.guest"));
    private final HorizontalLayout form = new HorizontalLayout();
    private final TextField question = new TextField();
    /** The page's own context (#56), read from its route; 0 garden at the space level. */
    private long spaceId;
    private long gardenId;

    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment, UsageService usage, GardenService gardens) {
        this.gardens = gardens;
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
        form.add(question, ask, questionsToday);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        noGarden.setVisible(false);
        guest.setVisible(false);
        add(new H3(getTranslation("ask.title")), new Paragraph(getTranslation("ask.intro")), noGarden, guest, form,
                result);
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
        // The space level has no garden: with none at all, the answer says that the centre of Italy is assumed.
        noGarden.setVisible(gardenId == 0 && gardens.gardens(spaceId, currentUser.login()).isEmpty());
        event.getLocation().getQueryParameters().getSingleParameter(QUESTION).filter(q -> !q.isBlank()).ifPresent(q -> {
            question.setValue(q);
            ask(q);
        });
    }

    private void ask(String text) {
        result.removeAll();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text.trim()), context());
            if (run.answer() == null) {
                result.add(new AnswerCard().waiting(ReasonText.text(run.pending().decision().reason(), this))
                        .extra(propose(text.trim())));
                return;
            }
            show(run.answer());
        } catch (UsageLimitException ex) {
            result.add(new Paragraph(getTranslation("ask.limit", ex.limit())));
        } catch (ServiceUnavailableException ex) {
            result.add(new Paragraph(getTranslation("error.claude")));
        } catch (RuntimeException ex) {
            Notification.show(getTranslation("ask.failed", ex.getMessage()));
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

    /** Today's questions against the personal limit (#19). */
    private void showQuestionsToday() {
        UsageService.Today today = usage.today(currentUser.login());
        questionsToday.setText(getTranslation("ask.today", today.used(), today.limit()));
    }

    private void show(PipelineAnswer answer) {
        List<AnswerCard.SourceLine> lines = new ArrayList<>();
        for (String id : answer.sources()) {
            if (id.equals("garden")) {
                lines.add(new AnswerCard.SourceLine(VaadinIcon.HOME_O, getTranslation("ask.source.garden"), null));
            }
            enrichment.source(spaceId, currentUser.login(), id).ifPresent(p -> lines.add(
                    new AnswerCard.SourceLine(VaadinIcon.USER_STAR, p.scope() == ProposalScope.GLOBAL
                            ? getTranslation("ask.source.global", String.valueOf(p.id()), p.citation())
                            : getTranslation("ask.source.space", p.proposedBy(), String.valueOf(p.id())), null)));
            sources.byId(id).ifPresent(s -> lines.add(new AnswerCard.SourceLine(VaadinIcon.BOOK, s.title(), s.url())));
        }
        result.add(new AnswerCard().answered(answer.text(), answer.answer(), answer.confidence())
                .notes(answer.notes()).sources(lines));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("ask.page") + " · Rootology";
    }
}
