package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.contextmenu.ContextMenu;
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
 * <p>The gardens are on a map (#66) beside a list of cards (#87): a marker selects its card, a card centres the
 * map on its marker, and a click on empty ground offers to add a garden there. The place search and "where I
 * am" sit on the map. On a phone the map and the list take turns, with one button to switch.
 */
@Route(value = "spaces/:spaceId/gardens", layout = MainLayout.class)
@PermitAll
public class GardensView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final GardenService gardens;
    private final GeoNames geoNames;
    private final Ungardened ungardened;
    private final CurrentUser currentUser;
    private final ClimateCells climateCells;
    private final GardenPipeline pipeline;
    private final WeatherService weather;
    private final Button add = new Button(getTranslation("garden.new"), VaadinIcon.PLUS.create());
    /** The space's name and the main action, "New garden" (#87). */
    private final PageHeader header = new PageHeader(getTranslation("gardens.title"), add);
    /** One card per garden, beside the map (#87). */
    private final Div cards = new Div();
    /** The cards with, on a phone, the button back to the map. */
    private final Div list = new Div();
    /** The map with its search, "where I am", the hint and, on a phone, the button to the list. */
    private final Div frame = new Div();
    private final Span hint = new Span(getTranslation("gardens.map.hint"));
    private final Button showList = new Button(getTranslation("gardens.show.list"), VaadinIcon.LIST_UL.create());
    private final Button showMap = new Button(getTranslation("gardens.show.map"), VaadinIcon.MAP_MARKER.create());
    /** The garden picked on the map or in the list, its card marked. */
    private long selected;
    /** What the page shows when the space has no garden yet (#81); the map stays. */
    private final EmptyState none = new EmptyState(getTranslation("gardens.none"));
    private final GardenMap map;
    /** Finds a place by name and moves the map there: the one place search of the page (#66). */
    private final ComboBox<Place> search = new ComboBox<>();
    /** General questions with no garden to work in: the space level (#56). */
    private final HorizontalLayout ask = new HorizontalLayout();
    /** The questions without a garden, rebuilt on every entry (#56). */
    private final Div ungardenedSection = new Div();
    /** The map and the list side by side; on a phone one at a time (#87). */
    private final Div split = new Div();
    private boolean contributor;
    /** The page's own space (#56), read from its route. */
    private long spaceId;
    /** The gardens on this page, in the list's order: the map and the list both read them. */
    private List<GardenSummary> shown = List.of();

    public GardensView(GardenService gardens, GeoNames geoNames, Ungardened ungardened, CurrentUser currentUser,
                       ClimateCells climateCells, GardenPipeline pipeline, WeatherService weather,
                       @Value("${rootology.map.style-url}") String styleUrl,
                       @Value("${rootology.map.dark-style-url:}") String darkStyleUrl) {
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
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.addClickListener(e -> addGarden());

        map = new GardenMap(styleUrl, darkStyleUrl);
        map.setLabels(new GardenMap.Labels(getTranslation("gardens.open"), getTranslation("common.edit"),
                getTranslation("common.delete"), getTranslation("gardens.map.altitude.label"),
                getTranslation("gardens.map.weather.label"), getTranslation("weather.credit"),
                getTranslation("gardens.map.crops.label"),
                getTranslation("gardens.map.waiting.label"),
                getTranslation("gardens.map.addhere"),
                getTranslation("gardens.map.unavailable"), getTranslation("gardens.map.water")));
        map.setContributor(false);
        // A marker selects its card; its popup has the card's actions, so the map alone is enough (#66).
        map.addSelectedListener(e -> map.select(e.gardenId()).ifPresent(this::selectCard));
        map.addOpenListener(e -> map.select(e.gardenId()).ifPresent(this::openGarden));
        map.addEditListener(e -> map.change(e.gardenId()).ifPresent(id -> shownGarden(id).ifPresent(this::editGarden)));
        map.addDeleteListener(e -> map.change(e.gardenId())
                .ifPresent(id -> shownGarden(id).ifPresent(this::confirmDelete)));
        map.addAddHereListener(e -> map.point(e.latitude(), e.longitude()).ifPresent(this::addGardenAt));
        map.addLocateFailedListener(e -> Toasts.problem(getTranslation("gardens.map.locate.failed")));

        // The search sits on the map, as in the map apps people know (#87): no label, a lens and a placeholder.
        search.setPlaceholder(getTranslation("gardens.map.search"));
        search.setAriaLabel(getTranslation("gardens.map.search"));
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
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

        Button locate = new Button(VaadinIcon.CROSSHAIRS.create(), e -> map.locate());
        locate.addThemeVariants(ButtonVariant.LUMO_ICON);
        locate.setAriaLabel(getTranslation("gardens.map.locate"));
        locate.setTooltipText(getTranslation("gardens.map.locate"));
        locate.addClassName("rootology-map-locate");
        Div overlay = new Div(search, locate);
        overlay.addClassName("rootology-map-overlay");
        hint.addClassName("rootology-map-hint");
        hint.setVisible(false);

        // On a phone the map and the list take turns (#87); on a wide screen both buttons are hidden.
        showList.addClassName("rootology-view-toggle");
        showList.addClickListener(e -> show("list"));
        showMap.addClassName("rootology-view-toggle");
        showMap.addClickListener(e -> show("map"));
        frame.addClassName("rootology-map-frame");
        frame.add(map, overlay, hint, showList);
        cards.addClassName("rootology-garden-cards");
        list.addClassName("rootology-garden-list");
        list.add(cards, showMap);

        split.addClassName("rootology-gardens-split");
        split.add(frame, list);
        show("map");
        add(header, ask, none, split, ungardenedSection);
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
                .ifPresent(sp -> header.setHeading(sp.name()));
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

    /**
     * A card's actions behind one "more" button, opened by a click. A plain button and its menu, not a MenuBar,
     * whose overflow button would add a second level.
     */
    ContextMenu actions(GardenSummary garden) {
        Button gear = new Button(VaadinIcon.ELLIPSIS_DOTS_H.create());
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

    /** The card of a garden the map selected, marked and scrolled into view (#66, #87). */
    private void selectCard(long gardenId) {
        selected = gardenId;
        cards.getChildren().forEach(card -> {
            boolean mine = Long.toString(gardenId).equals(card.getElement().getAttribute("data-garden"));
            card.getElement().getClassList().set("rootology-garden-card-selected", mine);
            if (mine) {
                card.scrollIntoView();
            }
        });
    }

    /** A garden picked in the list: its card is marked and the map centres on its marker, when it has one. */
    private void focusOnMap(GardenSummary garden) {
        selectCard(garden.id());
        if (garden.latitude() != null && garden.longitude() != null) {
            map.focus(garden.id());
            show("map");
        }
    }

    /** On a phone, the map or the list (#87); a wide screen shows both and ignores it. */
    private void show(String what) {
        split.getElement().setAttribute("data-show", what);
    }

    private void refresh() {
        List<GardenSummary> all = spaceId == 0 ? List.of() : gardens.gardens(spaceId, currentUser.login());
        shown = all;
        Map<String, Optional<ForecastHour>> hours = new LinkedHashMap<>();
        cards.removeAll();
        all.forEach(garden -> cards.add(card(garden, hour(garden, hours))));
        map.setGroups(groups(all, hours));
        none.setVisible(all.isEmpty());
        ask.setVisible(all.isEmpty() && contributor);
        updateVisibility();
        showUngardened();
    }

    /**
     * The list beside the map only when there are gardens (#87). The map always comes up, on a view of Italy with
     * no garden, so one can be added there; a garden with no coordinates is in the list only, and on a phone the
     * list then comes first. The hint to tap the map is for a contributor with no garden yet.
     */
    private void updateVisibility() {
        boolean anyMarker = shown.stream().anyMatch(g -> g.latitude() != null && g.longitude() != null);
        search.setVisible(geoNames != null && geoNames.enabled());
        list.setVisible(!shown.isEmpty());
        showList.setVisible(!shown.isEmpty());
        hint.setVisible(contributor && shown.isEmpty());
        show(!shown.isEmpty() && !anyMarker ? "list" : "map");
    }

    /** The weather now at a garden's point, read once per point for the cards and the markers (#67). */
    private Optional<ForecastHour> hour(GardenSummary garden, Map<String, Optional<ForecastHour>> hours) {
        if (garden.latitude() == null || garden.longitude() == null) {
            return Optional.empty();
        }
        return hours.computeIfAbsent(garden.latitude() + "/" + garden.longitude(),
                key -> weather.storedNow(garden.latitude(), garden.longitude()));
    }

    /**
     * A garden's card (#87): its name, which opens it, its town, the weather now from the stored forecast and the
     * menu of its actions. A click elsewhere on the card shows it on the map.
     */
    private Div card(GardenSummary garden, Optional<ForecastHour> hour) {
        // A plain link to the garden's home (spaces/{space}/gardens/{garden}), which the router follows in place.
        Anchor name = new Anchor("spaces/" + spaceId + "/gardens/" + garden.id(), garden.name());
        name.addClassName("rootology-garden-card-name");
        Span town = new Span(garden.town() == null ? getTranslation("garden.notown") : garden.town());
        town.addClassName("rootology-garden-card-town");
        Div text = new Div(name, town);
        text.addClassName("rootology-garden-card-text");
        Div card = new Div(text);
        hour.ifPresent(h -> {
            Span now = new Span();
            if (h.symbol() != null) {
                now.add(new Image("weather-icons/" + h.symbol() + ".svg", h.symbol().replace('_', ' ')));
            }
            now.add(new Span(weatherText(h)));
            now.addClassName("rootology-garden-card-weather");
            card.add(now);
        });
        card.add(actions(garden).getTarget());
        card.addClassName("rootology-garden-card");
        card.getElement().setAttribute("data-garden", Long.toString(garden.id()));
        card.getElement().getClassList().set("rootology-garden-card-selected", garden.id() == selected);
        card.addClickListener(e -> focusOnMap(garden));
        return card;
    }

    /**
     * The markers: gardens on the same rounded point (about 1 km) share one, which counts them (#66).
     * Everything a popup shows is computed here, in this space and for this person only: the coordinates,
     * what grows today, the questions waiting and the weather now from the stored forecast (#67).
     */
    private List<GardenMap.Group> groups(List<GardenSummary> all, Map<String, Optional<ForecastHour>> hours) {
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
            Optional<ForecastHour> hour = hour(first, hours);
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

    /** The gardens of the list, in order, for the tests (#87). */
    List<GardenSummary> listed() {
        return shown;
    }

    /** Whether the list of cards is on the page, for the tests (#87). */
    boolean listShown() {
        return list.isVisible();
    }

    /** What a phone shows first, "map" or "list", for the tests (#87). */
    String phoneShows() {
        return split.getElement().getAttribute("data-show");
    }

    /** The garden whose card is marked, 0 for none, for the tests (#87). */
    long selectedGarden() {
        return selected;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("gardens.title") + " · Rootology";
    }
}
