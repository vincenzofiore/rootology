package app.rootology.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import app.rootology.facts.ClimateCells;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.WeatherService;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The gardens of a space (#56): the overview a person lands on, and where a garden is chosen to work in.
 * A garden's name alone is ambiguous across spaces, so it is only ever shown under its space.
 *
 * <p>Since #66 the gardens are also on a map, above the table and linked to it: a marker selects its row,
 * a row centres the map on its marker, and a click on empty ground offers to add a garden there. The person
 * chooses map, table or both, and the browser remembers it.
 */
@Route(value = "spaces/:spaceId/gardens", layout = MainLayout.class)
@PermitAll
public class GardensView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** What the person wants to see; remembered in the browser like the colour scheme (#66). */
    enum ViewMode { MAP, TABLE, BOTH }

    /** The browser key of the choice; only read and written inside a guard, a refused storage keeps the default. */
    static final String VIEW_KEY = "rootology.gardenView";

    private final GardenService gardens;
    private final GeoNames geoNames;
    private final Ungardened ungardened;
    private final CurrentUser currentUser;
    private final ClimateCells climateCells;
    private final GardenPipeline pipeline;
    private final WeatherService weather;
    private final H3 title = new H3(getTranslation("gardens.title"));
    private final Grid<GardenSummary> grid = new Grid<>();
    /** What the page shows when the space has no garden yet (#81); the map stays. */
    private final EmptyState none = new EmptyState(getTranslation("gardens.none"));
    private final Button add = new Button(getTranslation("garden.new"));
    private final GardenMap map;
    private final RadioButtonGroup<ViewMode> viewMode = new RadioButtonGroup<>();
    /** Finds a place by name and moves the map there: the one place search of the page (#66). */
    private final ComboBox<Place> search = new ComboBox<>();
    /** General questions with no garden to work in: the space level (#56). */
    private final HorizontalLayout ask = new HorizontalLayout();
    /** The questions without a garden, rebuilt on every entry (#56). */
    private final Div ungardenedSection = new Div();
    /** The map and the table side by side, one above the other on a narrow screen (#66). */
    private final Div split = new Div();
    private boolean contributor;
    /** The page's own space (#56), read from its route. */
    private long spaceId;
    /** The gardens on this page, in the table's order: the map and the table both read them. */
    private List<GardenSummary> shown = List.of();

    public GardensView(GardenService gardens, GeoNames geoNames, Ungardened ungardened, CurrentUser currentUser,
                       ClimateCells climateCells, GardenPipeline pipeline, WeatherService weather,
                       @Value("${rootology.map.style-url}") String styleUrl) {
        this.gardens = gardens;
        this.geoNames = geoNames;
        this.ungardened = ungardened;
        this.currentUser = currentUser;
        this.climateCells = climateCells;
        this.pipeline = pipeline;
        this.weather = weather;
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
        add.addClickListener(e -> addGarden());

        map = new GardenMap(styleUrl);
        map.setLabels(new GardenMap.Labels(getTranslation("gardens.open"), getTranslation("common.edit"),
                getTranslation("common.delete"), getTranslation("gardens.map.altitude.label"),
                getTranslation("gardens.map.weather.label"), getTranslation("weather.credit"),
                getTranslation("gardens.map.crops.label"),
                getTranslation("gardens.map.waiting.label"),
                getTranslation("gardens.map.addhere"),
                getTranslation("gardens.map.unavailable"), getTranslation("gardens.map.water")));
        map.setContributor(false);
        map.setVisible(false);
        // A marker selects its row; its popup has the row's actions, so the map alone is enough (#66).
        map.addSelectedListener(e -> map.select(e.gardenId()).ifPresent(this::selectRow));
        map.addOpenListener(e -> map.select(e.gardenId()).ifPresent(this::openGarden));
        map.addEditListener(e -> map.change(e.gardenId()).ifPresent(id -> shownGarden(id).ifPresent(this::editGarden)));
        map.addDeleteListener(e -> map.change(e.gardenId())
                .ifPresent(id -> shownGarden(id).ifPresent(this::confirmDelete)));
        map.addAddHereListener(e -> map.point(e.latitude(), e.longitude()).ifPresent(this::addGardenAt));

        search.setLabel(getTranslation("gardens.map.search"));
        search.setPlaceholder(getTranslation("garden.place.hint"));
        search.addClassName("rootology-map-search");
        search.setItemLabelGenerator(Place::label);
        search.setClearButtonVisible(true);
        search.setVisible(false);
        if (geoNames != null && geoNames.enabled()) {
            search.setItems(query -> geoNames.search(query.getFilter().orElse(""), NoteText.locale(this).getLanguage())
                    .stream().skip(query.getOffset()).limit(query.getLimit()));
            search.addValueChangeListener(e -> {
                if (e.getValue() != null) {
                    map.showPlace(e.getValue().latitude(), e.getValue().longitude());
                }
            });
        }

        viewMode.setLabel(getTranslation("gardens.view"));
        viewMode.setItems(ViewMode.values());
        viewMode.setItemLabelGenerator(this::viewModeText);
        viewMode.setValue(ViewMode.MAP);
        viewMode.addValueChangeListener(e -> {
            updateVisibility();
            remember(e.getValue());
        });

        grid.addColumn(GardenSummary::name).setHeader(getTranslation("garden.garden"));
        grid.addColumn(g -> g.town() == null ? "" : g.town()).setHeader(getTranslation("garden.town"));
        // One menu per row: open for everyone, edit and delete for an OWNER or MEMBER (#33). The coordinates
        // are in the map's popup.
        grid.addComponentColumn(g -> actions(g).getTarget()).setHeader(getTranslation("space.actions")).setAutoWidth(true)
                .setFlexGrow(0);
        grid.setAllRowsVisible(true);
        grid.setVisible(false);
        // The other direction: a selected row centres the map on its marker and opens its popup (#66).
        // Only a person's pick in the table moves the map: a row the map selected must not zoom it again.
        grid.addSelectionListener(e -> {
            if (e.isFromClient()) {
                e.getFirstSelectedItem().ifPresent(this::focusOnMap);
            }
        });

        split.addClassName("rootology-gardens-split");
        split.add(map, grid);
        add(title, new Paragraph(getTranslation("gardens.intro")), new HorizontalLayout(add, viewMode), ask, none,
                search, split, ungardenedSection);
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
        map.setContributor(contributor);
        currentUser.spaces().stream().filter(sp -> sp.id() == spaceId).findFirst()
                .ifPresent(sp -> title.setText(sp.name()));
        refresh();
    }

    /** The choice of map, table or both is read from the browser as the page opens (#66). */
    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        event.getUI().getPage()
                .executeJs("try { return localStorage.getItem($0); } catch (e) { return null; }", VIEW_KEY)
                .then(String.class, saved -> {
                    if (saved == null) {
                        return;
                    }
                    for (ViewMode mode : ViewMode.values()) {
                        if (mode.name().equals(saved)) {
                            viewMode.setValue(mode);
                        }
                    }
                });
    }

    private void remember(ViewMode mode) {
        if (mode != null) {
            getUI().ifPresent(ui -> ui.getPage()
                    .executeJs("try { localStorage.setItem($0, $1); } catch (e) {} ", VIEW_KEY, mode.name()));
        }
    }

    private String viewModeText(ViewMode mode) {
        return getTranslation(switch (mode) {
            case MAP -> "gardens.view.map";
            case TABLE -> "gardens.view.table";
            case BOTH -> "gardens.view.both";
        });
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

    /**
     * A row's actions behind one gear button, opened by a click: the table keeps few columns and fits beside
     * the map. A plain button and its menu, not a MenuBar, whose overflow button would add a second level.
     */
    ContextMenu actions(GardenSummary garden) {
        Button gear = new Button(VaadinIcon.COG.create());
        gear.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        gear.setAriaLabel(getTranslation("gardens.actions", garden.name()));
        gear.setTooltipText(getTranslation("gardens.actions", garden.name()));
        ContextMenu menu = new ContextMenu(gear);
        menu.setOpenOnClick(true);
        menu.addItem(menuEntry(VaadinIcon.ARROW_RIGHT, "gardens.open"), e -> openGarden(garden.id()));
        if (contributor) {
            menu.addItem(menuEntry(VaadinIcon.EDIT, "common.edit"), e -> editGarden(garden));
            menu.addItem(menuEntry(VaadinIcon.TRASH, "common.delete"), e -> confirmDelete(garden));
        }
        return menu;
    }

    /** A menu line with the same icon as the row buttons elsewhere, and its word. */
    private HorizontalLayout menuEntry(VaadinIcon icon, String key) {
        HorizontalLayout entry = new HorizontalLayout(icon.create(), new Span(getTranslation(key)));
        entry.addClassName("rootology-menu-entry");
        entry.setSpacing(false);
        entry.setAlignItems(Alignment.CENTER);
        return entry;
    }

    /** One of the gardens on this page, by id. */
    private java.util.Optional<GardenSummary> shownGarden(long gardenId) {
        return shown.stream().filter(g -> g.id() == gardenId).findFirst();
    }

    private void openGarden(long gardenId) {
        getUI().ifPresent(ui -> ui.navigate(GardenHomeView.class,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId),
                        Routed.GARDEN, String.valueOf(gardenId)))));
    }

    private void editGarden(GardenSummary garden) {
        new GardenDialog(geoNames, climateCells, save(garden.id())).open(garden, saved -> refresh());
    }

    /**
     * Deleting a garden asks first, naming it and its plots. Plots and plantings go with it (on delete cascade
     * in the database); its questions, escalations and notifications stay in the space, without a garden (#56).
     */
    void confirmDelete(GardenSummary garden) {
        int plots = gardens.plots(spaceId, currentUser.login(), garden.id()).size();
        RowActions.confirmDelete(getTranslation("garden.delete.title"),
                getTranslation("garden.delete.text", garden.name(), plots), getTranslation("garden.delete.kept"),
                getTranslation("garden.delete.button"), getTranslation("common.cancel"),
                () -> deleteGarden(garden.id()));
    }

    void deleteGarden(long gardenId) {
        gardens.deleteGarden(spaceId, currentUser.login(), gardenId);
        refresh();
    }

    /** Creates a garden, or stores an existing one; the caller knows the space and the person. */
    private GardenDialog.Save save(Long gardenId) {
        return (name, town, latitude, longitude, countryCode, elevationM) -> gardens.saveGarden(spaceId,
                currentUser.login(), gardenId, name, town, latitude, longitude, countryCode, elevationM);
    }

    private void addGarden() {
        new GardenDialog(geoNames, climateCells, save(null)).open(null, saved -> refresh());
    }

    /** A point chosen on the map: the dialog opens on it, and the town comes from the nearest place (#66). */
    private void addGardenAt(GardenMap.Point point) {
        new GardenDialog(geoNames, climateCells, save(null)).open(null, saved -> refresh(), point.latitude(), point.longitude());
    }

    /** The table's row of a garden the map selected, scrolled into view (#66). */
    private void selectRow(long gardenId) {
        for (int i = 0; i < shown.size(); i++) {
            GardenSummary garden = shown.get(i);
            if (garden.id() == gardenId) {
                grid.select(garden);
                grid.scrollToIndex(i);
                return;
            }
        }
    }

    /** A garden picked in the table: the map centres on its marker, when it has one. */
    private void focusOnMap(GardenSummary garden) {
        if (garden.latitude() != null && garden.longitude() != null) {
            map.focus(garden.id());
        }
    }

    private void refresh() {
        List<GardenSummary> all = spaceId == 0 ? List.of() : gardens.gardens(spaceId, currentUser.login());
        shown = all;
        grid.setItems(all);
        map.setGroups(groups(all));
        none.setVisible(all.isEmpty());
        ask.setVisible(all.isEmpty() && contributor);
        updateVisibility();
        showUngardened();
    }

    /**
     * Map, table or both (#66). The map also comes up with no garden at all, on a view of Italy; a garden
     * with no coordinates stays in the table only, and when that is every garden the table is shown too,
     * so a choice of "map" never hides them.
     */
    private void updateVisibility() {
        boolean anyMarker = shown.stream().anyMatch(g -> g.latitude() != null && g.longitude() != null);
        ViewMode mode = viewMode.getValue() == null ? ViewMode.MAP : viewMode.getValue();
        map.setVisible(mode != ViewMode.TABLE && (anyMarker || shown.isEmpty()));
        search.setVisible(map.isVisible() && geoNames != null && geoNames.enabled());
        boolean tableWanted = mode != ViewMode.MAP || (!anyMarker && !shown.isEmpty());
        grid.setVisible(tableWanted && !shown.isEmpty());
    }

    /**
     * The markers: gardens on the same rounded point (about 1 km) share one, which counts them (#66).
     * Everything a popup shows is computed here, in this space and for this person only: the coordinates,
     * what grows today, the questions waiting and the weather now from the stored forecast (#67).
     */
    private List<GardenMap.Group> groups(List<GardenSummary> all) {
        Map<String, List<GardenSummary>> byPoint = new LinkedHashMap<>();
        for (GardenSummary garden : all) {
            if (garden.latitude() != null && garden.longitude() != null) {
                byPoint.computeIfAbsent(garden.latitude() + "/" + garden.longitude(), key -> new ArrayList<>())
                        .add(garden);
            }
        }
        List<GardenMap.Group> groups = new ArrayList<>();
        byPoint.forEach((key, same) -> {
            GardenSummary first = same.getFirst();
            // Gardens on one rounded point share one stored position, so they share one reading (#67).
            Optional<ForecastHour> hour = weather.storedNow(first.latitude(), first.longitude());
            List<GardenMap.Pin> pins = same.stream().map(garden -> pin(garden, hour)).toList();
            groups.add(new GardenMap.Group(first.latitude().doubleValue(), first.longitude().doubleValue(),
                    same.size() > 1 ? getTranslation("gardens.map.point", same.size()) : first.name(),
                    getTranslation("gardens.map.position", first.latitude().toPlainString(),
                            first.longitude().toPlainString()), pins));
        });
        return groups;
    }

    private GardenMap.Pin pin(GardenSummary garden, Optional<ForecastHour> hour) {
        String login = currentUser.login();
        LocalDate today = LocalDate.now(GardenHomeView.ITALY);
        int growing = gardens.growingOn(spaceId, login, garden.id(), today).size();
        int waiting = pipeline.pending(spaceId, garden.id(), login).size();
        return new GardenMap.Pin(Long.toString(garden.id()), garden.name(),
                garden.town() == null ? getTranslation("garden.notown") : garden.town(),
                garden.elevationM() == null ? getTranslation("gardens.map.altitude.none")
                        : getTranslation("gardens.map.altitude", garden.elevationM()),
                hour.map(ForecastHour::symbol).orElse(null),
                hour.map(this::weatherText).orElse(null),
                growing, waiting);
    }

    /**
     * The weather now in a popup, from the stored forecast hour (#67): whole degrees, and the precipitation
     * only when the hour covers one hour, so a six-hour total is never read as the rain of now.
     */
    private String weatherText(ForecastHour hour) {
        long temperature = Math.round(hour.airTemperature());
        if (hour.stepHours() == 1 && hour.precipitation() != null) {
            return getTranslation("gardens.map.weather", temperature, hour.precipitation());
        }
        return getTranslation("gardens.map.weather.temperature", temperature);
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

    /** The map of this page, for the tests (#66). */
    public GardenMap map() {
        return map;
    }

    /** The table of this page, for the tests (#66). */
    Grid<GardenSummary> table() {
        return grid;
    }

    /** The map / table / both choice, for the tests (#66). */
    RadioButtonGroup<ViewMode> viewMode() {
        return viewMode;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("gardens.title") + " · Rootology";
    }
}
