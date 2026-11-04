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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment;
import io.github.vincenzofiore.rootology.enrichment.ProposalScope;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.Question;
import jakarta.annotation.security.PermitAll;

/**
 * A free question, answered from the curated knowledge base, with the sources behind the answer.
 * An escalated question waits in Escalations, like the others.
 */
@Route(value = "ask", layout = MainLayout.class)
@PageTitle("Ask · Rootology")
@PermitAll
public class AskView extends VerticalLayout {

    private final GardenPipeline pipeline;
    private final SourceCatalog sources;
    private final CurrentUser currentUser;
    private final KnowledgeEnrichment enrichment;
    private final Div result = new Div();

    public AskView(GardenPipeline pipeline, SourceCatalog sources, CurrentUser currentUser,
                   KnowledgeEnrichment enrichment) {
        this.enrichment = enrichment;
        this.pipeline = pipeline;
        this.sources = sources;
        this.currentUser = currentUser;

        TextField question = new TextField("Your question");
        question.setPlaceholder("Which plants are companions of tomato?");
        question.setWidth("36rem");
        Button ask = new Button("Ask", e -> ask(question.getValue()));
        ask.addThemeName("primary");
        HorizontalLayout form = new HorizontalLayout(question, ask);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        add(new H3("Ask the knowledge base"),
                new Paragraph("Ask about companions and antagonists of a crop, when to sow or harvest it, "
                        + "what can be sown now or in a given month, the pests and diseases relevant for a crop "
                        + "in a season, or when a pest is active. About your garden: what grows now, which pests "
                        + "threaten it this month, what to sow in one of your plots (respecting rotation). Say north, "
                        + "centre or south of Italy if it matters; otherwise your garden's location, or the centre."),
                form, result);
    }

    private void ask(String text) {
        result.removeAll();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text.trim()), currentUser.runContext());
            if (run.answer() == null) {
                result.add(new Paragraph("Not sure enough to answer: " + run.pending().decision().reason()
                        + ". The question waits in Escalations for a member of this space."));
                return;
            }
            show(run.answer());
        } catch (RuntimeException ex) {
            Notification.show("Could not answer: " + ex.getMessage());
        }
    }

    private void show(PipelineAnswer answer) {
        result.add(new Paragraph(answer.text()));
        result.add(new Span((answer.answer().isEmpty() ? "No values" : String.join(", ", answer.answer()))
                + " · confidence %.2f".formatted(answer.confidence())));
        if (!answer.sources().isEmpty()) {
            Div list = new Div(new Paragraph("Sources:"));
            answer.sources().forEach(id -> {
                if (id.equals("garden")) {
                    list.add(new Div(new Span("Your garden (this space's plots and plantings)")));
                }
                enrichment.source(currentUser.space().id(), currentUser.login(), id).ifPresent(p -> list.add(new Div(
                        new Span(p.scope() == ProposalScope.GLOBAL
                                ? "Contributed fact #%d, reviewed by a curator: %s".formatted(p.id(), p.citation())
                                : "Proposed in this space by %s, not reviewed (#%d)".formatted(p.proposedBy(), p.id())))));
                sources.byId(id).ifPresent(s -> {
                    Anchor link = new Anchor(s.url(), s.title());
                    link.setTarget("_blank");
                    list.add(new Div(link));
                });
            });
            result.add(list);
        }
    }
}
