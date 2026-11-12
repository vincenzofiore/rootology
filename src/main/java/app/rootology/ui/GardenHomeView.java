package app.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
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
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.history.RunHistory;
import app.rootology.notifications.NotificationService;
import app.rootology.notifications.NotificationView;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.weather.DailyForecast;
import app.rootology.weather.WeatherService;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The home of one garden (#39, #56, #84): the quick actions on top (ask, add a planting, open the plots), the
 * first steps while they last, and a grid of cards with the weather there, the alerts of the last days, the
 * questions waiting for a person, what grows now plot by plot, and the climate of its site. Only this garden's.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId", layout = MainLayout.class)
@PermitAll
public class GardenHomeView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    /** How far back the home shows the weather alerts of #37. */
    static final int ALERT_DAYS = 3;
    /** How many waiting questions the card lists; the escalations page has them all. */
    static final int WAITING_SHOWN = 3;

    private final GardenService gardens;
    private final ClimateCells climateCells;
    private final WeatherService weather;
    private final NotificationService notifications;
    private final GardenPipeline pipeline;
    private final RunHistory history;
    private final CurrentUser currentUser;
    private final H3 title = new H3();
    private final TextField question = new TextField();
    /** The question field: a guest only looks (#33); its visibility depends on the route's space. */
    private final HorizontalLayout form = new HorizontalLayout();
    /** The compact actions under it: add a planting (contributors only) and open the plots (#84). */
    private final HorizontalLayout actions = new HorizontalLayout();
    private final Button addPlanting = new Button(getTranslation("home.actions.plant"), VaadinIcon.PLUS.create());
    private final Button openPlots = new Button(getTranslation("home.garden.open"), MainLayout.PLOTS_ICON.create());
    /** The first steps of #81, while they last; then the cards. */
    private final Div firstSteps = new Div();
    private final Div grid = new Div();
    /** The page's own context (#56), read from its route. */
    private long spaceId;
    private long gardenId;

    public GardenHomeView(GardenService gardens, ClimateCells climateCells, WeatherService weather,
                          NotificationService notifications, GardenPipeline pipeline, RunHistory history,
                          CurrentUser currentUser) {
        this.gardens = gardens;
        this.climateCells = climateCells;
        this.weather = weather;
        this.notifications = notifications;
        this.pipeline = pipeline;
        this.history = history;
        this.currentUser = currentUser;
        question.setMaxLength(Question.MAX_LENGTH);
        question.setPlaceholder(getTranslation("ask.placeholder"));
        question.setPrefixComponent(VaadinIcon.COMMENT_ELLIPSIS_O.create());
        Button ask = new Button(getTranslation("ask.button"), e -> ask());
        ask.addThemeName("primary");
        question.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> ask.click());
        form.add(question, ask);
        form.addClassName("rootology-home-ask");
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        form.setWrap(true);
        form.setVisible(false);
        addPlanting.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        addPlanting.setTooltipText(getTranslation("home.actions.plant"));
        addPlanting.setAriaLabel(getTranslation("home.actions.plant"));
        addPlanting.addClickListener(e -> goToPlots());
        openPlots.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY);
        openPlots.setTooltipText(getTranslation("home.garden.open"));
        openPlots.setAriaLabel(getTranslation("home.garden.open"));
        openPlots.addClickListener(e -> goToPlots());
        actions.add(addPlanting, openPlots);
        actions.addClassName("rootology-home-actions");
        grid.addClassName("rootology-home-grid");
        add(title, form, actions, firstSteps, grid);
    }

    /** Hands the question to this garden's Ask page, which answers it as the page opens. */
    private void ask() {
        if (question.getValue().isBlank()) {
            return;
        }
        UI.getCurrent().navigate(AskView.class, gardenParameters(),
                QueryParameters.simple(Map.of(AskView.QUESTION, question.getValue().trim())));
    }

    /** A planting needs a plot chosen on the plots page, so the action opens it (#84). */
    private void goToPlots() {
        getUI().ifPresent(ui -> ui.navigate(AreasView.class, gardenParameters()));
    }

    private RouteParameters gardenParameters() {
        return new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId),
                Routed.GARDEN, String.valueOf(gardenId)));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        boolean contributor = currentUser.canContribute(spaceId);
        form.setVisible(contributor);
        // A guest only looks (#33): no planting, but the plots page is there to read.
        addPlanting.setVisible(contributor);
        GardenSummary garden = gardens.garden(spaceId, currentUser.login(), gardenId).orElseThrow();
        title.setText(garden.name());
        firstSteps.removeAll();
        grid.removeAll();
        List<PlotSummary> plots = gardens.plots(spaceId, currentUser.login(), gardenId);
        List<PlantingSummary> growing = gardens.growingOn(spaceId, currentUser.login(), gardenId, LocalDate.now(ITALY));
        boolean asked = !history.recent(spaceId, gardenId, currentUser.login()).isEmpty();
        FirstSteps steps = FirstSteps.of(garden, plots, growing, asked);
        if (!steps.allDone()) {
            firstSteps.add(new FirstStepsCard(steps, spaceId, gardenId));
        }
        weatherCard(garden);
        alertsCard();
        waitingCard();
        growingCard(growing);
        climateCard(garden);
    }

    /** The coming week at this garden (#31), or a hint to add its coordinates. */
    private void weatherCard(GardenSummary garden) {
        if (garden.latitude() == null || garden.longitude() == null) {
            Card card = new Card(VaadinIcon.CLOUD_O, getTranslation("weather.title"));
            card.content().add(paragraph(getTranslation("weather.nocoordinates")));
            grid.add(card);
            return;
        }
        weather.around(garden.latitude(), garden.longitude()).ifPresent(forecast -> {
            List<DailyForecast> days = DailyForecast.of(forecast, ITALY, LocalDate.now(ITALY), 7);
            if (!days.isEmpty()) {
                grid.add(new WeatherCard(days, forecast.fetchedAt(), forecast.stale()));
            }
        });
    }

    /** The weather alerts (#37) of this garden in the last three days, coloured by severity. */
    private void alertsCard() {
        Instant since = Instant.now().minus(ALERT_DAYS, ChronoUnit.DAYS);
        List<NotificationView> alerts = notifications.of(currentUser.login(), 50).stream()
                .filter(n -> n.weather() && n.spaceId() == spaceId && n.gardenId() == gardenId
                        && n.createdAt().isAfter(since)).toList();
        if (alerts.isEmpty()) {
            return;
        }
        Card card = new Card(VaadinIcon.WARNING, getTranslation("home.alerts"));
        alerts.forEach(a -> card.content().add(alert(a)));
        grid.add(card);
    }

    /** One alert: its icon and colour say the rule, then the title and what the morning check saw. */
    private static Div alert(NotificationView alert) {
        AlertSeverity severity = AlertSeverity.of(alert.dedupKey());
        Icon icon = severity.icon().create();
        icon.addClassName("rootology-alert-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        Span alertTitle = new Span(alert.title());
        alertTitle.addClassName("rootology-alert-title");
        Div text = new Div(alertTitle, new Span(alert.body()));
        Div line = new Div(icon, text);
        line.addClassName("rootology-alert");
        line.addClassName(severity.className());
        return line;
    }

    /** The questions of this garden waiting for a person: a big count, and up to three of them. */
    private void waitingCard() {
        var waiting = pipeline.pending(spaceId, gardenId, currentUser.login());
        if (waiting.isEmpty()) {
            // Nothing waiting is the normal case: the home stays quiet (#81).
            return;
        }
        Card card = new Card(VaadinIcon.HOURGLASS, getTranslation("home.waiting.title"));
        card.iconClass("rootology-waiting-icon");
        Span count = new Span(String.valueOf(waiting.size()));
        count.addClassName("rootology-count");
        Span countWord = new Span(getTranslation("home.waiting.count", waiting.size()));
        Div summary = new Div(count, countWord);
        summary.addClassName("rootology-waiting-summary");
        card.content().add(summary);
        Div list = new Div();
        list.addClassName("rootology-waiting-list");
        waiting.stream().limit(WAITING_SHOWN).forEach(pending -> {
            RouterLink link = new RouterLink("", EscalationView.class, gardenParameters());
            link.addClassName("rootology-home-waiting-question");
            link.add(new Span(pending.question().text()));
            list.add(link);
        });
        card.content().add(list);
        grid.add(card);
    }

    /** What grows in this garden's plots today, as a chip per crop under its plot. */
    private void growingCard(List<PlantingSummary> growing) {
        Card card = new Card(MainLayout.PLOTS_ICON, getTranslation("home.growing"));
        if (growing.isEmpty()) {
            card.content().add(new EmptyState(getTranslation("home.growing.none")));
            grid.add(card);
            return;
        }
        Map<String, List<String>> byPlot = growing.stream().collect(Collectors.groupingBy(PlantingSummary::plotName,
                LinkedHashMap::new, Collectors.mapping(PlantingSummary::cropName, Collectors.toList())));
        byPlot.forEach((plot, crops) -> {
            Div group = new Div();
            group.addClassName("rootology-chip-group");
            Span plotName = new Span(plot);
            plotName.addClassName("rootology-chip-plot");
            Div chips = new Div();
            chips.addClassName("rootology-chips");
            crops.forEach(crop -> {
                Span chip = new Span(crop);
                chip.addClassName("rootology-grow-chip");
                chips.add(chip);
            });
            group.add(plotName, chips);
            card.content().add(group);
        });
        grid.add(card);
    }

    /** The climate profile of the garden's site (#62): the frost dates and the altitude as values. */
    private void climateCard(GardenSummary garden) {
        if (garden.latitude() == null || garden.longitude() == null) {
            return;
        }
        Card card = new Card(VaadinIcon.SUN_O, getTranslation("home.climate"));
        Locale locale = NoteText.locale(this);
        climateCells.nearest(garden.latitude().doubleValue(), garden.longitude().doubleValue())
                .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null))
                .ifPresentOrElse(profile -> climate(card, profile, locale),
                        () -> card.content().add(paragraph(getTranslation("home.climate.noprofile"))));
        grid.add(card);
    }

    private void climate(Card card, SiteProfile profile, Locale locale) {
        card.content().add(fact(VaadinIcon.ASTERISK, getTranslation("home.climate.last.title"),
                profile.lastFrostDay() == null ? getTranslation("home.climate.none.spring")
                        : ClimateText.frost(profile.lastFrostDay(), this::getTranslation, locale)));
        card.content().add(fact(VaadinIcon.ASTERISK, getTranslation("home.climate.first.title"),
                profile.firstFrostDay() == null ? getTranslation("home.climate.none.autumn")
                        : ClimateText.frost(profile.firstFrostDay(), this::getTranslation, locale)));
        card.content().add(fact(VaadinIcon.ARROW_UP, getTranslation("home.climate.altitude.title"),
                profile.altitudeM() == null ? getTranslation("home.climate.altitude.none")
                        : getTranslation("home.climate.altitude", profile.altitudeM())));
        // The AgERA5 cell and its period are a source, not something to decide by (#84).
        Span source = new Span(getTranslation("home.climate.cell", profile.cell()));
        source.addClassName("rootology-card-source");
        card.content().add(source);
    }

    /** One value with its icon: the word stays the tooltip and what a screen reader says (#84). */
    private static Div fact(VaadinIcon icon, String label, String value) {
        Icon drawing = icon.create();
        drawing.addClassName("rootology-fact-icon");
        drawing.getElement().setAttribute("aria-hidden", "true");
        Span word = new Span(label);
        word.addClassName("rootology-visually-hidden");
        Div row = new Div(drawing, word, new Span(value));
        row.addClassName("rootology-fact");
        row.getElement().setAttribute("title", label);
        return row;
    }

    private static Paragraph paragraph(String text) {
        Paragraph paragraph = new Paragraph(text);
        paragraph.addClassName("rootology-card-text");
        return paragraph;
    }

    /** A card of the home (#84): a title with its icon, then its content; the look is in the stylesheet. */
    private static final class Card extends Div {

        private final Div content = new Div();
        private final Icon drawing;

        Card(VaadinIcon icon, String title) {
            addClassName("rootology-card");
            drawing = icon.create();
            drawing.getElement().setAttribute("aria-hidden", "true");
            H4 heading = new H4(drawing, new Span(" " + title));
            heading.addClassName("rootology-card-title");
            content.addClassName("rootology-card-content");
            add(heading, content);
        }

        Div content() {
            return content;
        }

        /** A state colour for the card's icon (#87). */
        void iconClass(String className) {
            drawing.addClassName(className);
        }
    }

    @Override
    public String getPageTitle() {
        return title.getText().isBlank() ? "Rootology" : title.getText() + " · Rootology";
    }
}
