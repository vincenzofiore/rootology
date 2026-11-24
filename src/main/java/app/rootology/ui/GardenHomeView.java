package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.AbstractIcon;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
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
import app.rootology.facts.ClimateWindows;
import app.rootology.facts.ClimateWindows.ClimateWindow;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.PlantingMethod;
import app.rootology.history.RunHistory;
import app.rootology.notifications.NotificationService;
import app.rootology.officialwarnings.OfficialWarning;
import app.rootology.officialwarnings.OfficialWarnings;
import app.rootology.officialwarnings.WarningZones;
import app.rootology.notifications.NotificationView;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.weather.DailyForecast;
import app.rootology.weather.WeatherService;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    /** The home's own drawings (#130), in the hand-drawn line style of the areas, in the text's colour. */
    static final String SPROUT = "icons/home/sprout.svg";
    static final String THERMOMETER = "icons/home/thermometer.svg";
    static final String ALTITUDE = "icons/home/altitude.svg";
    /** Frost is the snowflake of the winter sun hours (#89): one symbol for the cold everywhere. */
    static final String FROST = "icons/seasons/winter.svg";

    /** The weather and the climate side by side on a wide screen, one under the other on a phone (#130). */
    private final Div weatherRow = new Div();

    private final GardenService gardens;
    private final ClimateCells climateCells;
    private final ClimateWindows climateWindows;
    private final WeatherService weather;
    private final NotificationService notifications;
    private final OfficialWarnings official;
    private final GardenPipeline pipeline;
    private final RunHistory history;
    private final CurrentUser currentUser;
    private final PageHeader title = new PageHeader("");
    private final Span place = new Span();
    private final TextField question = new TextField();
    /** The question field, in the Questions card (#133): a guest only looks (#33). */
    private final HorizontalLayout form = new HorizontalLayout();
    /** The first steps of #81, while they last; then the cards. */
    private final Div firstSteps = new Div();
    private final Div grid = new Div();
    /** The page's own context (#56), read from its route. */
    private long spaceId;
    private long gardenId;

    public GardenHomeView(GardenService gardens, ClimateCells climateCells, ClimateWindows climateWindows,
                          WeatherService weather, NotificationService notifications, OfficialWarnings official,
                          GardenPipeline pipeline, RunHistory history, CurrentUser currentUser) {
        this.gardens = gardens;
        this.climateCells = climateCells;
        this.climateWindows = climateWindows;
        this.weather = weather;
        this.notifications = notifications;
        this.official = official;
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
        grid.addClassName("rootology-home-grid");
        weatherRow.addClassName("rootology-home-weather-row");
        place.addClassName("rootology-page-place");
        title.besideTitle(place);
        add(title, firstSteps, grid);
    }

    /** Hands the question to this garden's Ask page, which answers it as the page opens. */
    private void ask() {
        if (question.getValue().isBlank()) {
            return;
        }
        UI.getCurrent().navigate(AskView.class, gardenParameters(),
                QueryParameters.simple(Map.of(AskView.QUESTION, question.getValue().trim())));
    }

    /** The areas page with one of its dialogs open (#133): a new area, or a planting in an area. */
    private void goToAreas(String parameter, String value) {
        getUI().ifPresent(ui -> ui.navigate(AreasView.class, gardenParameters(),
                QueryParameters.simple(Map.of(parameter, value))));
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
        GardenSummary garden = gardens.garden(spaceId, currentUser.login(), gardenId).orElseThrow();
        title.setHeading(garden.name());
        // Where the garden is, beside its name (#132): town and country, as on the areas page.
        place.setText(garden.town() == null ? getTranslation("garden.notown")
                : garden.town() + (garden.countryCode() == null ? "" : ", " + garden.countryCode()));
        firstSteps.removeAll();
        grid.removeAll();
        weatherRow.removeAll();
        grid.add(weatherRow);
        List<PlotSummary> plots = gardens.plots(spaceId, currentUser.login(), gardenId);
        List<PlantingSummary> growing = gardens.growingOn(spaceId, currentUser.login(), gardenId, LocalDate.now(ITALY));
        boolean asked = !history.recent(spaceId, gardenId, currentUser.login()).isEmpty();
        FirstSteps steps = FirstSteps.of(garden, plots, growing, asked);
        if (!steps.allDone()) {
            firstSteps.add(new FirstStepsCard(steps, spaceId, gardenId));
        }
        weatherCard(garden);
        alertsCard();
        officialWarningsCard(garden);
        questionsCard();
        if (contributor) {
            freeAreasCard(plots, growing);
        }
        growingCard(growing, plots, contributor, harvest(garden));
        climateCard(garden);
    }

    /** The coming week at this garden (#31), or a hint to add its coordinates. */
    private void weatherCard(GardenSummary garden) {
        if (garden.latitude() == null || garden.longitude() == null) {
            Card card = new Card(VaadinIcon.CLOUD_O, getTranslation("weather.title"));
            card.content().add(paragraph(getTranslation("weather.nocoordinates")));
            weatherRow.add(card);
            return;
        }
        weather.around(garden.latitude(), garden.longitude(), garden.elevationM()).ifPresent(forecast -> {
            List<DailyForecast> days = DailyForecast.of(forecast, ITALY, LocalDate.now(ITALY), 7);
            if (!days.isEmpty()) {
                weatherRow.add(new WeatherCard(days, WeatherCard.now(forecast.hours(), Instant.now()),
                        forecast.fetchedAt(), forecast.stale()));
            }
        });
    }

    /** The weather alerts (#37) of this garden in the last three days, coloured by severity. */
    private void alertsCard() {
        List<NotificationView> alerts = notifications.weatherAlerts(currentUser.login(), spaceId, gardenId,
                ALERT_DAYS);
        if (alerts.isEmpty()) {
            return;
        }
        Card card = new Card(VaadinIcon.WARNING, getTranslation("home.alerts"));
        alerts.forEach(a -> card.content().add(alert(a)));
        grid.add(card);
    }

    /**
     * One alert: its icon and colour say the rule (its short name stays as tooltip and aria label), then the
     * title and what the morning check saw.
     */
    private Div alert(NotificationView alert) {
        AlertSeverity severity = AlertSeverity.of(alert.dedupKey());
        String rule = getTranslation(severity.label());
        Icon icon = severity.icon().create();
        icon.addClassName("rootology-alert-icon");
        icon.getElement().setAttribute("title", rule);
        icon.getElement().setAttribute("aria-label", rule);
        icon.getElement().setAttribute("role", "img");
        Span alertTitle = new Span(alert.title());
        alertTitle.addClassName("rootology-alert-title");
        Div text = new Div(alertTitle, new Span(alert.body()));
        Div line = new Div(icon, text);
        line.addClassName("rootology-alert");
        line.addClassName(severity.className());
        return line;
    }

    /**
     * The official warnings of the garden's region (#136), apart from the computed alerts and never mixed with
     * them: the regions of Italy only, nothing for a garden abroad or whose region is not known yet, and nothing
     * when its region has no active warning. What is shown has the issuing service, the time of issue, the link
     * and MeteoAlarm's disclaimer; when the feed could not be read for ten minutes the card only says so.
     */
    void officialWarningsCard(GardenSummary garden) {
        Optional<String> zone = WarningZones.emmaId(garden.countryCode(), garden.regionCode());
        if (zone.isEmpty()) {
            return;
        }
        if (official.stale()) {
            Card card = new Card(VaadinIcon.MEGAPHONE, getTranslation("home.official"));
            card.content().add(paragraph(getTranslation("home.official.unavailable")), meteoAlarmLink());
            grid.add(card);
            return;
        }
        List<OfficialWarning> warnings = official.active(zone.get());
        if (warnings.isEmpty()) {
            return;
        }
        Card card = new Card(VaadinIcon.MEGAPHONE, getTranslation("home.official"));
        warnings.forEach(warning -> card.content().add(officialWarning(warning)));
        card.content().add(meteoAlarmLink(), disclaimer());
        grid.add(card);
    }

    /**
     * One official warning: the level (its colour and name) and the phenomenon as two icons, then the headline
     * exactly as MeteoAlarm wrote it in Italian, its period and who issued it when.
     */
    private Div officialWarning(OfficialWarning warning) {
        OfficialWarningLevel level = OfficialWarningLevel.of(warning.level());
        OfficialWarningType type = OfficialWarningType.of(warning.type());
        Span headline = new Span(warning.headline() == null || warning.headline().isBlank()
                ? warning.event() : warning.headline());
        headline.addClassName("rootology-official-headline");
        Span period = new Span(period(warning));
        period.addClassName("rootology-official-period");
        Span issued = new Span(issued(warning));
        issued.addClassName("rootology-official-issued");
        Div text = new Div(headline, period, issued);
        text.addClassName("rootology-official-text");
        Div line = new Div(icon(level.icon(), getTranslation(level.label())),
                icon(type.icon(), getTranslation(type.label())), text);
        line.addClassName("rootology-official-warning");
        line.addClassName(level.className());
        return line;
    }

    /** An icon that says what it is to a screen reader too, its translated name as tooltip and aria label. */
    private Icon icon(VaadinIcon symbol, String label) {
        Icon drawn = symbol.create();
        drawn.addClassName("rootology-official-icon");
        drawn.getElement().setAttribute("title", label);
        drawn.getElement().setAttribute("aria-label", label);
        drawn.getElement().setAttribute("role", "img");
        return drawn;
    }

    /** When the warned phenomenon begins and ends, in Italian time; the one date when the other is missing. */
    private String period(OfficialWarning warning) {
        String from = warning.onset() == null ? null : DateText.dateTime(warning.onset(), this);
        String to = warning.expires() == null ? null : DateText.dateTime(warning.expires(), this);
        if (from == null) {
            return to == null ? "" : to;
        }
        return to == null ? from : from + " – " + to;
    }

    /** "Emessa da … il …": the issuing service is always shown with the time of issue, as MeteoAlarm asks. */
    private String issued(OfficialWarning warning) {
        String sender = warning.senderName() == null || warning.senderName().isBlank()
                ? getTranslation("home.official.sender") : warning.senderName();
        return warning.sent() == null ? sender
                : getTranslation("home.official.issued", sender, DateText.dateTime(warning.sent(), this));
    }

    /** MeteoAlarm asks for a link to its site wherever its warnings are shown. */
    private static Anchor meteoAlarmLink() {
        Anchor link = new Anchor("https://meteoalarm.org", "meteoalarm.org");
        link.setTarget("_blank");
        link.addClassName("rootology-official-link");
        return link;
    }

    /** The disclaimer MeteoAlarm requires, unchanged in both languages. */
    private Paragraph disclaimer() {
        Paragraph disclaimer = new Paragraph(getTranslation("home.official.disclaimer"));
        disclaimer.addClassName("rootology-official-disclaimer");
        return disclaimer;
    }

    /**
     * Questions (#133): the field (contributors only), then, when some wait for a person, their count and the
     * three newest, and the links to Questions and to every waiting question (#132).
     */
    private void questionsCard() {
        var waiting = pipeline.pending(spaceId, gardenId, currentUser.login()).stream()
                .sorted(java.util.Comparator.comparing(GardenPipeline.PendingReview::askedAt).reversed()).toList();
        Card card = new Card(VaadinIcon.COMMENTS_O, getTranslation("nav.questions"));
        card.content().add(form);
        RouterLink open = new RouterLink("", AskView.class, gardenParameters());
        open.add(new Span(getTranslation("home.questions.open")));
        open.addClassName("rootology-home-see-all");
        Div links = new Div(open);
        links.addClassName("rootology-home-links");
        if (waiting.isEmpty()) {
            // Nothing waiting is the normal case: the card stays quiet (#81).
            card.content().add(links);
            grid.add(card);
            return;
        }
        Span count = new Span(String.valueOf(waiting.size()));
        count.addClassName("rootology-count");
        Span countWord = new Span(getTranslation("home.waiting.count", waiting.size()));
        Div summary = new Div(count, countWord);
        summary.addClassName("rootology-waiting-summary");
        card.content().add(summary);
        Div list = new Div();
        list.addClassName("rootology-waiting-list");
        waiting.stream().limit(WAITING_SHOWN).forEach(pending -> {
            // Questions with the waiting ones open (#132): a contributor answers there, a guest follows them.
            RouterLink link = new RouterLink("", AskView.class, gardenParameters());
            link.setQueryParameters(QueryParameters.simple(Map.of(AskView.WAITING, "")));
            link.addClassName("rootology-home-waiting-question");
            link.add(new Span(pending.question().text()));
            Icon go = VaadinIcon.ANGLE_RIGHT.create();
            go.getElement().setAttribute("aria-hidden", "true");
            go.addClassName("rootology-home-waiting-go");
            link.add(go);
            list.add(link);
        });
        card.content().add(list);
        // The three newest are shown; every one is a tap away, with the history filtered to them (#130, #132).
        RouterLink all = new RouterLink("", AskView.class, gardenParameters());
        all.setQueryParameters(QueryParameters.simple(Map.of(AskView.WAITING, "")));
        all.add(new Span(getTranslation("home.waiting.all", waiting.size())));
        all.addClassName("rootology-home-see-all");
        links.add(all);
        card.content().add(links);
        grid.add(card);
    }

    /**
     * Free areas (#141): how many areas have nothing growing today, and "Propose" to the crops for them. Only
     * when one is free, and only for a contributor, since a proposal is a question.
     */
    private void freeAreasCard(List<PlotSummary> plots, List<PlantingSummary> growing) {
        java.util.Set<Long> busy = growing.stream().map(PlantingSummary::plotId).collect(Collectors.toSet());
        long free = plots.stream().filter(p -> !busy.contains(p.id())).count();
        if (free == 0) {
            return;
        }
        Card card = new Card(MainLayout.PLOTS_ICON, getTranslation("home.free"));
        Span count = new Span(String.valueOf(free));
        count.addClassName("rootology-count");
        count.getElement().setAttribute("aria-label", getTranslation("home.free.count", free));
        Button propose = new Button(getTranslation("home.free.propose"), drawing(SPROUT),
                e -> UI.getCurrent().navigate(ProposalsView.class, gardenParameters()));
        propose.addThemeName("primary");
        Div row = new Div(count, propose);
        row.addClassName("rootology-home-free");
        card.content().add(row);
        grid.add(card);
    }

    /** The harvest months of every crop at this garden's site (#131), by crop id; empty when it has none. */
    private Map<String, List<ClimateWindow>> harvest(GardenSummary garden) {
        return HarvestText.site(garden, climateCells)
                .map(site -> HarvestText.harvest(site, climateWindows)).orElse(Map.of());
    }

    /**
     * What grows in this garden today, area by area (#130): every area, with a plus beside its name to sow or
     * plant there, and under the areas the way to add one and the link to the areas page (#133).
     */
    private void growingCard(List<PlantingSummary> growing, List<PlotSummary> plots, boolean contributor,
                             Map<String, List<ClimateWindow>> harvest) {
        Card card = new Card(drawing(SPROUT), getTranslation("home.growing"));
        if (growing.isEmpty() && (plots.isEmpty() || !contributor)) {
            card.content().add(new EmptyState(getTranslation("home.growing.none")));
        }
        Map<Long, List<PlantingSummary>> byPlot = growing.stream().collect(Collectors.groupingBy(
                PlantingSummary::plotId, LinkedHashMap::new, Collectors.toList()));
        for (PlotSummary area : plots) {
            List<PlantingSummary> crops = byPlot.getOrDefault(area.id(), List.of());
            if (crops.isEmpty() && !contributor) {
                continue;
            }
            Div group = new Div();
            group.addClassName("rootology-chip-group");
            Div plotName = new Div(new Span(area.name()));
            plotName.addClassName("rootology-chip-plot");
            // The area's sun this month beside its name (#130): exposure belongs to an area, not to the garden.
            areaSun(area).ifPresent(plotName::add);
            if (contributor) {
                Button plant = RowActions.add(getTranslation("garden.sowin", area.name()),
                        e -> goToAreas(AreasView.PLANT, String.valueOf(area.id())));
                plant.addClassName("rootology-chip-plot-add");
                plotName.add(plant);
            }
            group.add(plotName);
            if (!crops.isEmpty()) {
                Div chips = new Div();
                chips.addClassName("rootology-chips");
                crops.forEach(planting -> chips.add(growChip(planting, harvest.get(planting.cropId()))));
                group.add(chips);
            }
            card.content().add(group);
        }
        Div links = new Div();
        links.addClassName("rootology-home-links");
        if (contributor) {
            RouterLink addArea = new RouterLink("", AreasView.class, gardenParameters());
            addArea.setQueryParameters(QueryParameters.simple(Map.of(AreasView.NEW, "")));
            addArea.add(VaadinIcon.PLUS.create(), new Span(getTranslation("garden.addplot")));
            addArea.addClassName("rootology-home-see-all");
            links.add(addArea);
        }
        // Straight to the areas, where what grows is changed (#130).
        RouterLink areas = new RouterLink("", AreasView.class, gardenParameters());
        areas.add(MainLayout.PLOTS_ICON.create(), new Span(getTranslation("home.garden.open")));
        areas.addClassName("rootology-home-see-all");
        links.add(areas);
        card.content().add(links);
        grid.add(card);
    }

    /** The sun icon of the area's level this month, with the same sentence as the area card (#89, #130). */
    private java.util.Optional<Icon> areaSun(PlotSummary area) {
        app.rootology.garden.DirectSun sun = area.directSunIn(LocalDate.now(ITALY).getMonth());
        if (sun == null) {
            return java.util.Optional.empty();
        }
        String said = AreasView.sunTooltip(getTranslation("directsun." + sun.name()), area.sunHoursSummer(),
                area.sunHoursWinter(), null, this::getTranslation);
        Icon icon = AreasView.sunIcon(sun).create();
        icon.addClassName("rootology-chip-plot-sun");
        icon.setTooltipText(said);
        icon.getElement().setAttribute("aria-label", said);
        icon.getElement().setAttribute("role", "img");
        return java.util.Optional.of(icon);
    }

    /**
     * A crop growing now (#130): its name, then how and when it went into the ground, "semina 3 mar" or
     * "trapianto 10 apr" (nouns, so no agreement with the crop's gender), then when it is usually harvested at
     * this site (#131), when the site has a HARVEST window for it.
     */
    private Span growChip(PlantingSummary planting, List<ClimateWindow> harvest) {
        Span name = new Span(planting.cropName());
        Span chip = new Span(name);
        chip.addClassName("rootology-grow-chip");
        if (planting.plantedOn() != null && planting.method() != null) {
            Span when = new Span(getTranslation(planting.method() == PlantingMethod.SOWN ? "home.growing.sown"
                    : "home.growing.transplanted", DateText.date(planting.plantedOn(), this)));
            when.addClassName("rootology-grow-when");
            chip.add(when);
        }
        HarvestText.value(harvest, this::getTranslation, NoteText.locale(this)).ifPresent(chip::add);
        return chip;
    }

    /**
     * The climate profile of the garden's site (#62), beside the weather (#130): open on a wide screen, closed
     * on a phone under a one-line summary, since the forecast matters more right now.
     */
    private void climateCard(GardenSummary garden) {
        if (garden.latitude() == null || garden.longitude() == null) {
            return;
        }
        Locale locale = NoteText.locale(this);
        java.util.Optional<SiteProfile> profile = climateCells.nearest(garden.latitude().doubleValue(),
                        garden.longitude().doubleValue())
                .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null));
        SvgIcon icon = drawing(THERMOMETER);
        icon.getElement().setAttribute("aria-hidden", "true");
        H4 title = new H4(icon, new Span(" " + getTranslation("home.climate")));
        title.addClassName("rootology-card-title");
        Span summary = new Span(profile.map(p -> climateSummary(p, locale)).orElse(""));
        summary.addClassName("rootology-climate-summary");
        Div head = new Div(title, summary);
        head.addClassName("rootology-climate-head");
        Div content = new Div();
        content.addClassName("rootology-card-content");
        profile.ifPresentOrElse(p -> climate(content, p, locale),
                () -> content.add(paragraph(getTranslation("home.climate.noprofile"))));
        Details details = new Details(head, content);
        details.addClassNames("rootology-card", "rootology-climate");
        details.setOpened(true);
        // On a phone the climate starts closed; the summary says the essentials (#130).
        UI.getCurrent().getPage().retrieveExtendedClientDetails(client -> {
            if (client.getWindowInnerWidth() <= 640) {
                details.setOpened(false);
            }
        });
        weatherRow.add(details);
    }

    /** The closed climate in one line: until when frosts come, and the altitude (#130). */
    private String climateSummary(SiteProfile profile, Locale locale) {
        String frost = profile.lastFrostDay() == null ? getTranslation("home.climate.summary.nofrost")
                : getTranslation("home.climate.summary.frost",
                        ClimateText.frost(profile.lastFrostDay(), this::getTranslation, locale));
        return profile.altitudeM() == null ? frost
                : frost + " · " + getTranslation("home.climate.altitude", profile.altitudeM());
    }

    private void climate(Div content, SiteProfile profile, Locale locale) {
        content.add(fact(drawing(FROST), getTranslation("home.climate.last.title"),
                profile.lastFrostDay() == null ? getTranslation("home.climate.none.spring")
                        : ClimateText.frost(profile.lastFrostDay(), this::getTranslation, locale)));
        content.add(fact(drawing(FROST), getTranslation("home.climate.first.title"),
                profile.firstFrostDay() == null ? getTranslation("home.climate.none.autumn")
                        : ClimateText.frost(profile.firstFrostDay(), this::getTranslation, locale)));
        content.add(fact(drawing(ALTITUDE), getTranslation("home.climate.altitude.title"),
                profile.altitudeM() == null ? getTranslation("home.climate.altitude.none")
                        : profile.altitudeM() + " m"));
        // The AgERA5 cell is where the values come from: a source, said as such (#130).
        Span label = new Span(getTranslation("home.climate.source.title"));
        label.addClassName("rootology-card-source-title");
        Span source = new Span(getTranslation(profile.correction() == SiteProfile.Correction.NONE
                ? "home.climate.source" : "home.climate.source.altitude", profile.cell()));
        Div from = new Div(label, source);
        from.addClassName("rootology-card-source");
        content.add(from);
    }

    private static SvgIcon drawing(String path) {
        return new SvgIcon(path);
    }

    /** One value with its icon: the word stays the tooltip and what a screen reader says (#84). */
    private static Div fact(AbstractIcon<?> drawing, String label, String value) {
        drawing.addClassName("rootology-fact-icon");
        drawing.getElement().setAttribute("aria-hidden", "true");
        // The two frosts share the ice crystal, so the word is shown, small, before the value (#130).
        Span word = new Span(label);
        word.addClassName("rootology-fact-label");
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
        private final AbstractIcon<?> drawing;

        Card(VaadinIcon icon, String title) {
            this(icon.create(), title);
        }

        Card(AbstractIcon<?> icon, String title) {
            addClassName("rootology-card");
            drawing = icon;
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
