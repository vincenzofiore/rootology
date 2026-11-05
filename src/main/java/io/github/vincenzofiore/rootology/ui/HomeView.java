package io.github.vincenzofiore.rootology.ui;

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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.Background;
import com.vaadin.flow.theme.lumo.LumoUtility.BorderRadius;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.notifications.NotificationView;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.weather.DailyForecast;
import io.github.vincenzofiore.rootology.weather.WeatherService;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The home page (#31): a question field, the weather at the space's garden, the weather alerts of the last
 * days, the questions waiting for a person, and what grows now, plot by plot.
 */
@Route(value = "", layout = MainLayout.class)
@PermitAll
public class HomeView extends VerticalLayout implements HasDynamicTitle {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");

    public HomeView(GardenService gardens, WeatherService weather, NotificationService notifications,
                    GardenPipeline pipeline, CurrentUser currentUser) {
        long spaceId = currentUser.space().id();
        String login = currentUser.login();
        add(new H3(currentUser.space().name()));

        if (currentUser.canContribute()) {
            TextField question = new TextField();
            question.setPlaceholder(getTranslation("ask.placeholder"));
            question.setWidth("32rem");
            question.setMaxWidth("100%");
            question.setPrefixComponent(VaadinIcon.COMMENT_ELLIPSIS_O.create());
            Button ask = new Button(getTranslation("ask.button"), e -> {
                if (!question.getValue().isBlank()) {
                    getUI().ifPresent(ui -> ui.navigate(AskView.class,
                            QueryParameters.simple(Map.of(AskView.QUESTION, question.getValue().trim()))));
                }
            });
            ask.addThemeName("primary");
            question.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> ask.click());
            HorizontalLayout form = new HorizontalLayout(question, ask);
            form.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);
            form.setWrap(true);
            add(form);
        }

        List<GardenSummary> all = gardens.gardens(spaceId, login);
        if (all.isEmpty()) {
            add(new Paragraph(getTranslation("home.nogarden")), go(VaadinIcon.PLUS, getTranslation("garden.new"), GardenView.class));
            return;
        }
        Optional<GardenSummary> located = all.stream().filter(g -> g.latitude() != null && g.longitude() != null).findFirst();
        located.flatMap(g -> weather.around(g.latitude(), g.longitude())).ifPresent(forecast -> {
            List<DailyForecast> days = DailyForecast.of(forecast, ITALY, LocalDate.now(ITALY), 7);
            if (!days.isEmpty()) {
                add(new WeatherCard(days, forecast.fetchedAt(), forecast.stale()));
            }
        });
        if (located.isEmpty()) {
            Paragraph hint = new Paragraph(getTranslation("weather.nocoordinates"));
            hint.addClassNames(TextColor.SECONDARY);
            add(hint);
        }

        Instant since = Instant.now().minus(3, ChronoUnit.DAYS);
        List<NotificationView> alerts = notifications.of(login, 50).stream()
                .filter(n -> n.weather() && n.spaceId() == spaceId && n.createdAt().isAfter(since)).toList();
        if (!alerts.isEmpty()) {
            add(section(VaadinIcon.WARNING, getTranslation("home.alerts")));
            alerts.forEach(a -> add(callout(a.title() + ": " + a.body())));
        }

        var waiting = pipeline.pending(spaceId);
        if (!waiting.isEmpty()) {
            add(section(VaadinIcon.HOURGLASS, getTranslation("home.waiting", waiting.size())));
            waiting.stream().limit(3).forEach(r -> add(new Paragraph("• " + r.question().text())));
            add(go(VaadinIcon.ARROW_RIGHT, getTranslation("home.waiting.open"), EscalationView.class));
        }

        List<PlantingSummary> growing = gardens.growingOn(spaceId, login, LocalDate.now(ITALY));
        add(section(VaadinIcon.HOME_O, getTranslation("home.growing")));
        if (growing.isEmpty()) {
            add(new Paragraph(getTranslation("home.growing.none")));
        } else {
            Map<String, String> byPlot = growing.stream().collect(Collectors.groupingBy(PlantingSummary::plotName,
                    LinkedHashMap::new, Collectors.mapping(PlantingSummary::cropName, Collectors.joining(", "))));
            byPlot.forEach((plot, crops) -> add(new Paragraph(plot + ": " + crops)));
        }
        add(go(VaadinIcon.ARROW_RIGHT, getTranslation("home.garden.open"), GardenView.class));
    }

    private Button go(VaadinIcon icon, String text, Class<? extends com.vaadin.flow.component.Component> view) {
        Button button = new Button(text, icon.create(), e -> getUI().ifPresent(ui -> ui.navigate(view)));
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
        return getTranslation("home.title") + " · Rootology";
    }
}
