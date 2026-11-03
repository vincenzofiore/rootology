package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.SourceRow;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;

/**
 * Sources and licences of the knowledge base, generated from `knowledge/sources.csv`. Public:
 * attribution must be readable by anyone, signed in or not.
 */
@Route(SourcesView.ROUTE)
@PageTitle("Sources and licences · Rootology")
@AnonymousAllowed
public class SourcesView extends VerticalLayout {

    static final String ROUTE = "sources";

    public SourcesView(SourceCatalog sources) {
        Grid<SourceRow> grid = new Grid<>();
        grid.addComponentColumn(s -> {
            Anchor link = new Anchor(s.url(), s.title());
            link.setTarget("_blank");
            return link;
        }).setHeader("Source").setFlexGrow(3);
        grid.addColumn(SourceRow::license).setHeader("Licence").setFlexGrow(2);
        grid.addColumn(SourceRow::retrievedOn).setHeader("Retrieved").setAutoWidth(true);
        grid.setItems(sources.all());
        grid.setAllRowsVisible(true);

        add(new H2("Sources and licences"),
                new Paragraph("Every fact in Rootology's knowledge base points to one of these sources. "
                        + "Facts from studies and extension publications are cited, never copied."),
                grid,
                new Paragraph("Environmental requirements of crops: FAO ECOCROP, licensed under CC BY 4.0."),
                new Paragraph("Contains EPPO Codes (www.eppo.int), downloaded on 2026-11-03. "
                        + "This use does not imply any endorsement by EPPO."),
                new Paragraph("Crop identity and families: Wikidata (CC0)."));
    }
}
