package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.geocoding.GeoNames;
import io.github.vincenzofiore.rootology.pipeline.Question;
import jakarta.annotation.security.PermitAll;

import java.util.List;
import java.util.Map;

/**
 * The gardens of a space (#56): the overview a person lands on, and where a garden is chosen to work in.
 * A garden's name alone is ambiguous across spaces, so it is only ever shown under its space.
 */
@Route(value = "spaces/:spaceId/gardens", layout = MainLayout.class)
@PermitAll
public class GardensView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final GardenService gardens;
    private final GeoNames geoNames;
    private final Ungardened ungardened;
    private final CurrentUser currentUser;
    private final H3 title = new H3(getTranslation("gardens.title"));
    private final Grid<GardenSummary> grid = new Grid<>();
    private final Paragraph none = new Paragraph(getTranslation("gardens.none"));
    private final Button add = new Button(getTranslation("garden.new"));
    /** General questions with no garden to work in: the space level (#56). */
    private final HorizontalLayout ask = new HorizontalLayout();
    /** The questions without a garden, rebuilt on every entry (#56). */
    private final Div ungardenedSection = new Div();
    /** Open and edit; editing needs an OWNER or MEMBER (#33). */
    private Grid.Column<GardenSummary> actions;
    private boolean contributor;
    /** The page's own space (#56), read from its route. */
    private long spaceId;

    public GardensView(GardenService gardens, GeoNames geoNames, Ungardened ungardened, CurrentUser currentUser) {
        this.gardens = gardens;
        this.geoNames = geoNames;
        this.ungardened = ungardened;
        this.currentUser = currentUser;
        ask.setVisible(false);
        ask.setDefaultVerticalComponentAlignment(Alignment.END);
        ask.setWrap(true);
        TextField question = new TextField(getTranslation("ask.question"));
        question.setPlaceholder(getTranslation("ask.placeholder"));
        question.setMaxLength(Question.MAX_LENGTH);
        question.setWidth("32rem");
        Button send = new Button(getTranslation("ask.button"),
                e -> ask(question.getValue().trim()));
        send.addThemeName("primary");
        ask.add(question, send);
        // A guest only looks (#33): no garden is added; the role is known in beforeEnter.
        add.setVisible(false);
        add.addThemeName("primary");
        add.addClickListener(e -> new GardenDialog(geoNames, (name, town, latitude, longitude, countryCode, elevationM) ->
                gardens.saveGarden(spaceId, currentUser.login(), null, name, town, latitude, longitude, countryCode, elevationM))
                .open(null, saved -> refresh()));

        grid.addColumn(GardenSummary::name).setHeader(getTranslation("garden.garden"));
        grid.addColumn(g -> g.town() == null ? "" : g.town()).setHeader(getTranslation("garden.town"));
        grid.addColumn(g -> g.latitude() == null ? "" : g.latitude() + ", " + g.longitude())
                .setHeader(getTranslation("gardens.position")).setAutoWidth(true);
        actions = grid.addComponentColumn(g -> new HorizontalLayout(open(g), edit(g)))
                .setHeader(getTranslation("space.actions")).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        grid.setVisible(false);

        add(title, new Paragraph(getTranslation("gardens.intro")),
                new HorizontalLayout(add), ask, none, grid, ungardenedSection);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.space(event, currentUser);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        contributor = currentUser.canContribute(spaceId);
        add.setVisible(contributor);
        actions.setVisible(contributor);
        currentUser.spaces().stream().filter(sp -> sp.id() == spaceId).findFirst()
                .ifPresent(sp -> title.setText(sp.name()));
        refresh();
    }

    /** With no garden there is no garden to ask in: the question goes to the space level, without a garden. */
    private void ask(String text) {
        if (text.isEmpty()) {
            return;
        }
        getUI().ifPresent(ui -> ui.navigate(AskView.class,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId))),
                QueryParameters.simple(Map.of(AskView.QUESTION, text))));
    }

    private Button open(GardenSummary garden) {
        return new Button(getTranslation("gardens.open"), e -> getUI().ifPresent(ui -> ui.navigate(GardenHomeView.class,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId),
                        Routed.GARDEN, String.valueOf(garden.id()))))));
    }

    private Button edit(GardenSummary garden) {
        return new Button(getTranslation("garden.edit"), e ->
                new GardenDialog(geoNames, (name, town, latitude, longitude, countryCode, elevationM) ->
                        gardens.saveGarden(spaceId, currentUser.login(), garden.id(), name, town, latitude, longitude,
                                countryCode, elevationM)).open(garden, saved -> refresh()));
    }

    private void refresh() {
        List<GardenSummary> all = spaceId == 0 ? List.of() : gardens.gardens(spaceId, currentUser.login());
        grid.setItems(all);
        grid.setVisible(!all.isEmpty());
        none.setVisible(all.isEmpty());
        ask.setVisible(all.isEmpty() && contributor);
        showUngardened();
    }

    /**
     * The questions the space asked outside a garden (#56), only when it has some: the ones of a space
     * that never had a garden, the ones stored before #56, and those of a deleted garden.
     */
    private void showUngardened() {
        ungardenedSection.removeAll();
        if (spaceId == 0 || !ungardened.any(spaceId, currentUser.login())) {
            return;
        }
        Map<String, String> here = Map.of(Routed.SPACE, String.valueOf(spaceId));
        ungardenedSection.add(new H4(getTranslation("gardens.ungardened")),
                new Paragraph(getTranslation("gardens.ungardened.intro")),
                new HorizontalLayout(
                        new RouterLink(getTranslation("nav.escalations"), EscalationView.class,
                                new RouteParameters(here)),
                        new RouterLink(getTranslation("nav.history"), HistoryView.class, new RouteParameters(here))));
    }

    /** Whether the page offers the actions that need an OWNER or MEMBER (#33), for the tests. */
    boolean showsGardenActions() {
        return add.isVisible();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("gardens.title") + " · Rootology";
    }
}
