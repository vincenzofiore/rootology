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
import com.vaadin.flow.router.RouterLink;
import io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment;
import io.github.vincenzofiore.rootology.enrichment.ProposalScope;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;
import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import io.github.vincenzofiore.rootology.usage.UsageLimitException;
import io.github.vincenzofiore.rootology.usage.UsageService;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.Question;
import jakarta.annotation.security.PermitAll;

import java.util.ArrayList;
import java.util.List;

/**
 * A free question, answered from the curated knowledge base, with the sources behind the answer.
 * An escalated question waits in Escalations, like the others.
 */
@Route(value = "ask", layout = MainLayout.class)
@PermitAll
public class AskView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** A question handed over by the home page (#31), answered as soon as the page opens. */
    static final String QUESTION = "q";

    private final GardenPipeline pipeline;
    private final SourceCatalog sources;
    private final CurrentUser currentUser;
    private final KnowledgeEnrichment enrichment;
    private final UsageService usage;
    private final Span questionsToday = new Span();
    private final Div result = new Div();
    private final TextField question = new TextField();

    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment, UsageService usage) {
        this.enrichment = enrichment;
        this.usage = usage;
        this.pipeline = pipeline;
        this.sources = sources;
        this.currentUser = currentUser;

        question.setLabel(getTranslation("ask.question"));
        question.setPlaceholder(getTranslation("ask.placeholder"));
        question.setWidth("36rem");
        Button ask = new Button(getTranslation("ask.button"), e -> ask(question.getValue()));
        ask.addThemeName("primary");
        HorizontalLayout form = new HorizontalLayout(question, ask, questionsToday);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        add(new H3(getTranslation("ask.title")), new Paragraph(getTranslation("ask.intro")), form, result);
        showQuestionsToday();
        // A guest only looks (#33): each question costs Claude and Jev calls and may wait for a member.
        if (!currentUser.canContribute()) {
            form.setVisible(false);
            add(new Paragraph(getTranslation("ask.guest")));
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
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
            GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text.trim()), currentUser.runContext());
            if (run.answer() == null) {
                RouterLink propose = new RouterLink(getTranslation("ask.propose"), KnowledgeView.class);
                propose.setQueryParameters(QueryParameters.simple(java.util.Map.of("question", text.trim())));
                result.add(new AnswerCard().waiting(ReasonText.text(run.pending().decision().reason(), this))
                        .extra(propose));
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
            enrichment.source(currentUser.space().id(), currentUser.login(), id).ifPresent(p -> lines.add(
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
