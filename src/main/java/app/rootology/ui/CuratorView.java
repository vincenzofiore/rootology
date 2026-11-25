package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import app.rootology.enrichment.InvalidProposalException;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.enrichment.ProposalSummary;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import jakarta.annotation.security.RolesAllowed;

/**
 * The review queue of #24, for curators: the facts proposed for everyone, with their source, to approve
 * or reject. Outside the spaces and gardens (#56), since a curator reviews for all of them.
 */
@Route(value = "curator", layout = MainLayout.class)
@RolesAllowed("CURATOR")
public class CuratorView extends VerticalLayout implements HasDynamicTitle {

    private final KnowledgeEnrichment enrichment;
    private final CurrentUser currentUser;
    private final ProposalText text;
    private final Grid<ProposalSummary> queue = new Grid<>();
    private final TextField note = new TextField(getTranslation("knowledge.note"));

    /** How far back the names asked and not found are counted (#180). */
    static final java.time.Duration UNKNOWN_SINCE = java.time.Duration.ofDays(90);

    public CuratorView(KnowledgeEnrichment enrichment, CropCatalog crops, KnowledgeGraph knowledge,
                       CurrentUser currentUser, app.rootology.history.RunHistory history, java.time.Clock clock) {
        this.enrichment = enrichment;
        this.currentUser = currentUser;
        this.text = new ProposalText(crops, knowledge);
        note.setWidth("36rem");
        queue.addColumn(p -> text.describe(this, p)).setHeader(getTranslation("knowledge.fact")).setFlexGrow(3);
        queue.addColumn(ProposalSummary::citation).setHeader(getTranslation("knowledge.source")).setFlexGrow(2);
        queue.addColumn(p -> currentUser.nameOf(p.proposedBy())).setHeader(getTranslation("knowledge.by")).setAutoWidth(true);
        queue.addComponentColumn(p -> new HorizontalLayout(
                new Button(getTranslation("knowledge.approve"), e -> act(() ->
                        enrichment.approve(currentUser.login(), p.id(), note.getValue()))),
                new Button(getTranslation("knowledge.reject"), e -> act(() ->
                        enrichment.reject(currentUser.login(), p.id(), note.getValue())))))
                .setAutoWidth(true);
        queue.setAllRowsVisible(true);
        add(new PageHeader(getTranslation("knowledge.curator")), new Paragraph(getTranslation("knowledge.curator.intro")),
                note, queue);
        // What people asked about and the base does not have (#180): the candidates to add, with their sources.
        var unknown = history.unknownNames(clock.instant().minus(UNKNOWN_SINCE));
        if (!unknown.isEmpty()) {
            Grid<app.rootology.history.RunHistory.UnknownName> names = new Grid<>();
            names.addColumn(app.rootology.history.RunHistory.UnknownName::name)
                    .setHeader(getTranslation("knowledge.curator.unknown.name")).setFlexGrow(2);
            names.addColumn(app.rootology.history.RunHistory.UnknownName::questions)
                    .setHeader(getTranslation("knowledge.curator.unknown.count")).setAutoWidth(true);
            names.addColumn(n -> DateText.date(n.last().atZone(GardenHomeView.ITALY).toLocalDate(), this))
                    .setHeader(getTranslation("knowledge.curator.unknown.last")).setAutoWidth(true);
            names.setItems(unknown);
            names.setAllRowsVisible(true);
            add(new com.vaadin.flow.component.html.H2(getTranslation("knowledge.curator.unknown")),
                    new Paragraph(getTranslation("knowledge.curator.unknown.intro")), names);
        }
        refresh();
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (InvalidProposalException ex) {
            Toasts.problem(String.join("; ", ex.problems()));
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
        refresh();
    }

    private void refresh() {
        queue.setItems(enrichment.pending(currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("knowledge.curator") + " · Rootology";
    }
}
