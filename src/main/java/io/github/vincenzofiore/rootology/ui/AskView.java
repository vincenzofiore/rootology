package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
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

/**
 * A free question, answered from the curated knowledge base, with the sources behind the answer.
 * An escalated question waits in Escalations, like the others.
 */
@Route(value = "ask", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PermitAll
public class AskView extends VerticalLayout implements HasDynamicTitle {

    private final GardenPipeline pipeline;
    private final SourceCatalog sources;
    private final CurrentUser currentUser;
    private final KnowledgeEnrichment enrichment;
    private final UsageService usage;
    private final Span questionsToday = new Span();
    private final Div result = new Div();

    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment, UsageService usage) {
        this.enrichment = enrichment;
        this.usage = usage;
        this.pipeline = pipeline;
        this.sources = sources;
        this.currentUser = currentUser;

        TextField question = new TextField(getTranslation("ask.question"));
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

    private void ask(String text) {
        result.removeAll();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text.trim()), currentUser.runContext());
            if (run.answer() == null) {
                result.add(new Paragraph(getTranslation("ask.escalated", run.pending().decision().reason())));
                RouterLink propose = new RouterLink(getTranslation("ask.propose"), KnowledgeView.class);
                propose.setQueryParameters(QueryParameters.simple(java.util.Map.of("question", text.trim())));
                result.add(propose);
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
        result.add(new Paragraph(answer.text()));
        result.add(new Span(getTranslation("ask.values", answer.answer().isEmpty() ? getTranslation("ask.novalues")
                : String.join(", ", answer.answer()), answer.confidence())));
        answer.notes().forEach(note -> result.add(new Paragraph(note)));
        if (!answer.sources().isEmpty()) {
            Div list = new Div(new Paragraph(getTranslation("ask.sources")));
            answer.sources().forEach(id -> {
                if (id.equals("garden")) {
                    list.add(new Div(new Span(getTranslation("ask.source.garden"))));
                }
                enrichment.source(currentUser.space().id(), currentUser.login(), id).ifPresent(p -> list.add(new Div(
                        new Span(p.scope() == ProposalScope.GLOBAL
                                ? getTranslation("ask.source.global", String.valueOf(p.id()), p.citation())
                                : getTranslation("ask.source.space", p.proposedBy(), String.valueOf(p.id()))))));
                sources.byId(id).ifPresent(s -> {
                    Anchor link = new Anchor(s.url(), s.title());
                    link.setTarget("_blank");
                    list.add(new Div(link));
                });
            });
            result.add(list);
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("ask.page") + " · Rootology";
    }
}
