package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.alerts.WeatherAlerts;
import io.github.vincenzofiore.rootology.roles.AppRole;
import io.github.vincenzofiore.rootology.roles.RoleGrantView;
import io.github.vincenzofiore.rootology.roles.RoleService;
import io.github.vincenzofiore.rootology.usage.UsageService;
import jakarta.annotation.security.RolesAllowed;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/**
 * App roles (#32), for admins only (Vaadin checks {@code @RolesAllowed}, the service {@code @PreAuthorize}):
 * everyone the app knows with their roles, grant and revoke, and the history of every change. A change
 * applies from the person's next sign-in.
 */
@Route(value = "admin", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminView extends VerticalLayout implements HasDynamicTitle {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Rome"));

    private final RoleService roles;
    private final CurrentUser currentUser;
    private final Grid<Map.Entry<String, Set<AppRole>>> people = new Grid<>();
    private final Grid<RoleGrantView> history = new Grid<>();
    private final UsageService usage;
    private final WeatherAlerts weatherAlerts;
    private final Grid<UsageService.PersonUsage> consumption = new Grid<>();

    public AdminView(RoleService roles, UsageService usage, WeatherAlerts weatherAlerts, CurrentUser currentUser) {
        this.roles = roles;
        this.usage = usage;
        this.weatherAlerts = weatherAlerts;
        this.currentUser = currentUser;
        people.addColumn(Map.Entry::getKey).setHeader(getTranslation("space.login")).setAutoWidth(true);
        for (AppRole role : AppRole.values()) {
            people.addComponentColumn(p -> {
                Checkbox box = new Checkbox(p.getValue().contains(role));
                box.addValueChangeListener(e -> {
                    if (e.isFromClient()) {
                        act(() -> {
                            if (e.getValue()) {
                                roles.grant(currentUser.login(), p.getKey(), role);
                            } else {
                                roles.revoke(currentUser.login(), p.getKey(), role);
                            }
                        });
                    }
                });
                return box;
            }).setHeader(role.name()).setAutoWidth(true);
        }
        people.setAllRowsVisible(true);

        history.addColumn(RoleGrantView::githubLogin).setHeader(getTranslation("admin.who")).setAutoWidth(true);
        history.addColumn(g -> g.role().name()).setHeader(getTranslation("space.role")).setAutoWidth(true);
        history.addColumn(g -> g.grantedBy() + ", " + WHEN.format(g.grantedAt())).setHeader(getTranslation("admin.granted"));
        history.addColumn(g -> g.active() ? "" : g.revokedBy() + ", " + WHEN.format(g.revokedAt()))
                .setHeader(getTranslation("admin.revoked"));
        history.setAllRowsVisible(true);

        add(new H3(getTranslation("admin.roles")), new Paragraph(getTranslation("admin.roles.intro")),
                people, new H4(getTranslation("admin.history")), history);
        add(usageSection());
        Button checkWeather = new Button(getTranslation("admin.weather.check"), e -> Notification.show(
                getTranslation("admin.weather.result", weatherAlerts.checkNow())));
        add(new H3(getTranslation("admin.weather")), new Paragraph(getTranslation("admin.weather.intro")), checkWeather);
        refresh();
    }

    /** Usage of the paid services per person over the last 7 days, and personal daily limits (#19). */
    private VerticalLayout usageSection() {
        consumption.addColumn(UsageService.PersonUsage::login).setHeader(getTranslation("space.login")).setAutoWidth(true);
        consumption.addColumn(u -> getTranslation("admin.usage.today.value", u.questionsToday(), u.limit()))
                .setHeader(getTranslation("admin.usage.today")).setAutoWidth(true);
        consumption.addColumn(UsageService.PersonUsage::questions).setHeader(getTranslation("admin.usage.questions"))
                .setAutoWidth(true);
        consumption.addColumn(UsageService.PersonUsage::reviews).setHeader(getTranslation("admin.usage.reviews"))
                .setAutoWidth(true);
        consumption.addColumn(u -> u.promptTokens() + " / " + u.completionTokens()).setHeader(getTranslation("admin.usage.tokens"))
                .setAutoWidth(true);
        consumption.addColumn(u -> "$%.4f".formatted(u.costUsd())).setHeader(getTranslation("admin.usage.cost")).setAutoWidth(true);
        consumption.addColumn(UsageService.PersonUsage::jevCalls).setHeader(getTranslation("admin.usage.jev")).setAutoWidth(true);
        consumption.addComponentColumn(u -> {
            IntegerField limit = new IntegerField();
            limit.setMin(0);
            limit.setWidth("6rem");
            limit.setValue(u.personalLimit() ? u.limit() : null);
            limit.setPlaceholder(String.valueOf(usage.defaultLimit()));
            Button save = new Button(getTranslation("admin.usage.set"), e -> act(() ->
                    usage.setLimit(currentUser.login(), u.login(), limit.getValue())));
            return new HorizontalLayout(limit, save);
        }).setHeader(getTranslation("admin.usage.limit")).setAutoWidth(true);
        consumption.setAllRowsVisible(true);
        return new VerticalLayout(new H3(getTranslation("admin.usage")),
                new Paragraph(getTranslation("admin.usage.intro", usage.defaultLimit())), consumption);
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            Notification.show(e.getMessage());
        }
        refresh();
    }

    private void refresh() {
        people.setItems(roles.people(currentUser.login()).entrySet().stream().toList());
        consumption.setItems(usage.report(7));
        history.setItems(roles.history(currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("admin.title") + " · Rootology";
    }
}
