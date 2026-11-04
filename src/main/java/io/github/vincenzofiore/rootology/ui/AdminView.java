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
import com.vaadin.flow.router.PageTitle;
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
@PageTitle("Admin · Rootology")
@RolesAllowed("ADMIN")
public class AdminView extends VerticalLayout {

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
        people.addColumn(Map.Entry::getKey).setHeader("GitHub login").setAutoWidth(true);
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

        history.addColumn(RoleGrantView::githubLogin).setHeader("Who").setAutoWidth(true);
        history.addColumn(g -> g.role().name()).setHeader("Role").setAutoWidth(true);
        history.addColumn(g -> g.grantedBy() + ", " + WHEN.format(g.grantedAt())).setHeader("Granted");
        history.addColumn(g -> g.active() ? "" : g.revokedBy() + ", " + WHEN.format(g.revokedAt())).setHeader("Revoked");
        history.setAllRowsVisible(true);

        add(new H3("Roles"),
                new Paragraph("ADMIN manages roles, CURATOR reviews facts proposed for everyone, QA runs the quality "
                        + "suite. A change applies from the person's next sign-in; there is always at least one admin."),
                people, new H4("History"), history);
        add(usageSection());
        Button checkWeather = new Button("Check weather alerts now", e -> Notification.show(
                "Weather alerts: " + weatherAlerts.checkNow() + " new"));
        add(new H3("Weather alerts"), new Paragraph("They run every morning at 7; this runs the same check now, "
                + "and an alert already sent is never sent again."), checkWeather);
        refresh();
    }

    /** Usage of the paid services per person over the last 7 days, and personal daily limits (#19). */
    private VerticalLayout usageSection() {
        consumption.addColumn(UsageService.PersonUsage::login).setHeader("GitHub login").setAutoWidth(true);
        consumption.addColumn(u -> u.questionsToday() + " of " + u.limit()).setHeader("Today").setAutoWidth(true);
        consumption.addColumn(UsageService.PersonUsage::questions).setHeader("Questions").setAutoWidth(true);
        consumption.addColumn(UsageService.PersonUsage::reviews).setHeader("Answers to escalations").setAutoWidth(true);
        consumption.addColumn(u -> u.promptTokens() + " / " + u.completionTokens()).setHeader("Claude tokens in / out")
                .setAutoWidth(true);
        consumption.addColumn(u -> "$%.4f".formatted(u.costUsd())).setHeader("Claude cost (est.)").setAutoWidth(true);
        consumption.addColumn(UsageService.PersonUsage::jevCalls).setHeader("Jev calls").setAutoWidth(true);
        consumption.addComponentColumn(u -> {
            IntegerField limit = new IntegerField();
            limit.setMin(0);
            limit.setWidth("6rem");
            limit.setValue(u.personalLimit() ? u.limit() : null);
            limit.setPlaceholder(String.valueOf(usage.defaultLimit()));
            Button save = new Button("Set", e -> act(() -> usage.setLimit(currentUser.login(), u.login(), limit.getValue())));
            return new HorizontalLayout(limit, save);
        }).setHeader("Daily limit (empty: default)").setAutoWidth(true);
        consumption.setAllRowsVisible(true);
        return new VerticalLayout(new H3("Usage"),
                new Paragraph("Last 7 days. Each question calls Claude twice and Jev once; answers to escalations "
                        + "call Claude once and are never blocked. The default limit is " + usage.defaultLimit()
                        + " questions a day."), consumption);
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
}
