package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.SourceRow;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;

/**
 * Sources and licences of the knowledge base, generated from `knowledge/sources.csv`. Public:
 * attribution must be readable by anyone, signed in or not.
 */
@Route(value = SourcesView.ROUTE, layout = PublicLayout.class)
@AnonymousAllowed
public class SourcesView extends VerticalLayout implements HasDynamicTitle {

    static final String ROUTE = "sources";

    public SourcesView(SourceCatalog sources) {
        Grid<SourceRow> grid = new Grid<>();
        grid.addComponentColumn(s -> {
            Anchor link = new Anchor(s.url(), s.title());
            link.setTarget("_blank");
            return link;
        }).setHeader(getTranslation("sources.source")).setFlexGrow(3);
        grid.addColumn(SourceRow::license).setHeader(getTranslation("sources.licence")).setFlexGrow(2);
        grid.addColumn(SourceRow::retrievedOn).setHeader(getTranslation("sources.retrieved")).setAutoWidth(true);
        grid.setItems(sources.all());
        grid.setAllRowsVisible(true);

        add(new H2(getTranslation("sources.title")), new Paragraph(getTranslation("sources.intro")), grid,
                new Paragraph(getTranslation("sources.ecocrop")), new Paragraph(getTranslation("sources.eppo")),
                new Paragraph(getTranslation("sources.wikidata")), new Paragraph(getTranslation("sources.weather")),
                new Paragraph(getTranslation("sources.icons")), new Paragraph(getTranslation("sources.agera5")),
                new Paragraph(getTranslation("sources.dem")), new Paragraph(getTranslation("sources.gtopo30")));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("sources.title") + " · Rootology";
    }
}
