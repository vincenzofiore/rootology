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
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.Background;
import com.vaadin.flow.theme.lumo.LumoUtility.BorderRadius;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The home of one garden (#39, #56): a question field, the weather there, the weather alerts of the last
 * days, the questions waiting for a person, and what grows now, plot by plot. Only this garden's.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId", layout = MainLayout.class)
@PermitAll
public class GardenHomeView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");

    private final GardenService gardens;
    private final ClimateCells climateCells;
    private final WeatherService weather;
    private final NotificationService notifications;
    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final H3 title = new H3();
    private final Div body = new Div();
    private final TextField question = new TextField();
    /** The question field: a guest only looks (#33); its visibility depends on the route's space. */
    private final HorizontalLayout form = new HorizontalLayout();
    /** The page's own context (#56), read from its route. */
    private long spaceId;
    private long gardenId;

    public GardenHomeView(GardenService gardens, ClimateCells climateCells, WeatherService weather,
                          NotificationService notifications, GardenPipeline pipeline, CurrentUser currentUser) {
        this.gardens = gardens;
        this.climateCells = climateCells;
        this.weather = weather;
        this.notifications = notifications;
        this.pipeline = pipeline;
        this.currentUser = currentUser;
        question.setMaxLength(Question.MAX_LENGTH);
        question.setPlaceholder(getTranslation("ask.placeholder"));
        question.setWidth("32rem");
        question.setMaxWidth("100%");
        question.setPrefixComponent(VaadinIcon.COMMENT_ELLIPSIS_O.create());
        Button ask = new Button(getTranslation("ask.button"), e -> ask());
        ask.addThemeName("primary");
        question.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> ask.click());
        form.add(question, ask);
        form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
        form.setWrap(true);
        form.setVisible(false);
        add(title, form, body);
    }

    /** Hands the question to this garden's Ask page, which answers it as the page opens. */
    private void ask() {
        if (question.getValue().isBlank()) {
            return;
        }
        UI.getCurrent().navigate(AskView.class, new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId),
                        Routed.GARDEN, String.valueOf(gardenId))),
                QueryParameters.simple(Map.of(AskView.QUESTION, question.getValue().trim())));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        gardenId = where.get().gardenId();
        form.setVisible(currentUser.canContribute(spaceId));
        GardenSummary garden = gardens.garden(spaceId, currentUser.login(), gardenId).orElseThrow();
        title.setText(garden.name());
        body.removeAll();
        showClimate(garden);
        showWeather(garden);
        showAlerts();
        showWaiting();
        showGrowing();
        body.add(go(VaadinIcon.ARROW_RIGHT, getTranslation("home.garden.open"), PlotsView.class));
    }

    /** The garden's climate profile (#62): altitude, both 1-in-5-year frost dates and the cell. */
    private void showClimate(GardenSummary garden) {
        if (garden.latitude() == null || garden.longitude() == null) {
            return;
        }
        body.add(section(VaadinIcon.SUN_O, getTranslation("home.climate")));
        java.util.Locale locale = NoteText.locale(this);
        climateCells.nearest(garden.latitude().doubleValue(), garden.longitude().doubleValue())
                .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null))
                .ifPresentOrElse(profile -> body.add(climate(profile, locale)),
                        () -> body.add(new Paragraph(getTranslation("home.climate.noprofile"))));
    }

    private Div climate(SiteProfile profile, java.util.Locale locale) {
        Div lines = new Div();
        lines.add(new Paragraph(profile.altitudeM() == null
                ? getTranslation("home.climate.altitude.none")
                : getTranslation("home.climate.altitude", profile.altitudeM())));
        lines.add(new Paragraph(getTranslation("home.climate.last", profile.lastFrostDay() == null
                ? getTranslation("home.climate.none.spring")
                : ClimateText.frost(profile.lastFrostDay(), this::getTranslation, locale))));
        lines.add(new Paragraph(getTranslation("home.climate.first", profile.firstFrostDay() == null
                ? getTranslation("home.climate.none.autumn")
                : ClimateText.frost(profile.firstFrostDay(), this::getTranslation, locale))));
        lines.add(new Paragraph(getTranslation("home.climate.cell", profile.cell())));
        return lines;
    }

    /** The coming week at this garden (#31): a hint for coordinates, or nothing when there is no forecast. */
    private void showWeather(GardenSummary garden) {
        if (garden.latitude() == null || garden.longitude() == null) {
            Paragraph hint = new Paragraph(getTranslation("weather.nocoordinates"));
            hint.addClassNames(TextColor.SECONDARY);
            body.add(hint);
            return;
        }
        weather.around(garden.latitude(), garden.longitude()).ifPresent(forecast -> {
            List<DailyForecast> days = DailyForecast.of(forecast, ITALY, LocalDate.now(ITALY), 7);
            if (!days.isEmpty()) {
                body.add(new WeatherCard(days, forecast.fetchedAt(), forecast.stale()));
            }
        });
    }

    /** The weather alerts (#37) of this garden in the last three days. */
    private void showAlerts() {
        Instant since = Instant.now().minus(3, ChronoUnit.DAYS);
        List<NotificationView> alerts = notifications.of(currentUser.login(), 50).stream()
                .filter(n -> n.weather() && n.spaceId() == spaceId && n.gardenId() == gardenId
                        && n.createdAt().isAfter(since)).toList();
        if (!alerts.isEmpty()) {
            body.add(section(VaadinIcon.WARNING, getTranslation("home.alerts")));
            alerts.forEach(a -> body.add(callout(a.title() + ": " + a.body())));
        }
    }

    /** The questions of this garden waiting for a person. */
    private void showWaiting() {
        var waiting = pipeline.pending(spaceId, gardenId, currentUser.login());
        if (!waiting.isEmpty()) {
            body.add(section(VaadinIcon.HOURGLASS, getTranslation("home.waiting", waiting.size())));
            waiting.stream().limit(3).forEach(r -> body.add(new Paragraph("• " + r.question().text())));
            body.add(go(VaadinIcon.ARROW_RIGHT, getTranslation("home.waiting.open"), EscalationView.class));
        }
    }

    /** What grows in this garden's plots today. */
    private void showGrowing() {
        List<PlantingSummary> growing = gardens.growingOn(spaceId, currentUser.login(), gardenId, LocalDate.now(ITALY));
        body.add(section(VaadinIcon.HOME_O, getTranslation("home.growing")));
        if (growing.isEmpty()) {
            body.add(new Paragraph(getTranslation("home.growing.none")));
            return;
        }
        Map<String, String> byPlot = growing.stream().collect(Collectors.groupingBy(PlantingSummary::plotName,
                LinkedHashMap::new, Collectors.mapping(PlantingSummary::cropName, Collectors.joining(", "))));
        byPlot.forEach((plot, crops) -> body.add(new Paragraph(plot + ": " + crops)));
    }

    private Button go(VaadinIcon icon, String text, Class<? extends com.vaadin.flow.component.Component> view) {
        Button button = new Button(text, icon.create(), e -> getUI().ifPresent(ui -> ui.navigate(view,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(spaceId),
                        Routed.GARDEN, String.valueOf(gardenId))))));
        button.addThemeName("tertiary");
        return button;
    }

    private static H4 section(VaadinIcon icon, String text) {
        Icon i = icon.create();
        i.setSize("1em");
        H4 title = new H4(i, new Span(" " + text));
        title.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.SMALL);
        return title;
    }

    private static Div callout(String text) {
        Icon i = VaadinIcon.WARNING.create();
        i.addClassNames(TextColor.ERROR);
        i.setSize("1.1em");
        Div callout = new Div(i, new Span(text));
        callout.addClassNames(Display.FLEX, AlignItems.START, Gap.SMALL, Padding.SMALL, BorderRadius.MEDIUM,
                FontSize.SMALL, Background.ERROR_10);
        callout.setMaxWidth("48rem");
        return callout;
    }

    @Override
    public String getPageTitle() {
        return title.getText().isBlank() ? "Rootology" : title.getText() + " · Rootology";
    }
}
