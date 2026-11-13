package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import app.rootology.access.AccessRequestService;
import app.rootology.access.AccessRequestStatus;
import app.rootology.alerts.WeatherAlerts;
import app.rootology.roles.AppRole;
import app.rootology.roles.RoleGrantView;
import app.rootology.roles.RoleService;
import app.rootology.usage.UsageService;
import jakarta.annotation.security.RolesAllowed;

import java.util.Map;
import java.util.Set;

/**
 * App roles (#32), for admins only (Vaadin checks {@code @RolesAllowed}, the service {@code @PreAuthorize}):
 * everyone the app knows with their roles, grant and revoke, and the history of every change. A change
 * applies from the person's next sign-in. It also holds the invitation requests (#65), approved or rejected
 * here, with the motivation shown as plain text.
 */
@Route(value = "admin", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminView extends VerticalLayout implements HasDynamicTitle {

    private final RoleService roles;
    private final CurrentUser currentUser;
    private final Grid<Map.Entry<String, Set<AppRole>>> people = new Grid<>();
    private final Grid<RoleGrantView> history = new Grid<>();
    private final UsageService usage;
    private final WeatherAlerts weatherAlerts;
    private final AccessRequestService accessRequests;
    private final Grid<UsageService.PersonUsage> consumption = new Grid<>();
    private final Grid<app.rootology.access.AccessRequestView> waitingRequests = new Grid<>();
    private final Grid<app.rootology.access.AccessRequestView> decidedRequests = new Grid<>();

    public AdminView(RoleService roles, UsageService usage, WeatherAlerts weatherAlerts, CurrentUser currentUser,
                     AccessRequestService accessRequests) {
        this.roles = roles;
        this.usage = usage;
        this.weatherAlerts = weatherAlerts;
        this.currentUser = currentUser;
        this.accessRequests = accessRequests;
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
        history.addComponentColumn(g -> new Span(g.grantedBy() + ", " + DateText.dateTime(g.grantedAt(), this)))
                .setHeader(getTranslation("admin.granted"));
        history.addComponentColumn(g -> new Span(g.active() ? "" : g.revokedBy() + ", "
                        + DateText.dateTime(g.revokedAt(), this)))
                .setHeader(getTranslation("admin.revoked"));
        history.setAllRowsVisible(true);

        add(new PageHeader(getTranslation("admin.title")));
        add(new H3(getTranslation("admin.roles")), new Paragraph(getTranslation("admin.roles.intro")),
                people, new H4(getTranslation("admin.history")), history);
        add(usageSection());
        add(requestsSection());
        Button checkWeather = new Button(getTranslation("admin.weather.check"), e -> Toasts.info(
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

    /**
     * The invitation requests (#65): those waiting, with the motivation as plain text, and the last 30
     * decided ones. Approving or rejecting asks first; both record who decided and when.
     */
    private VerticalLayout requestsSection() {
        waitingRequests.addComponentColumn(r -> DateText.dateTimeSpan(r.createdAt(), this))
                .setHeader(getTranslation("admin.requests.when")).setAutoWidth(true);
        waitingRequests.addColumn(r -> getTranslation("provider." + r.provider().id()))
                .setHeader(getTranslation("admin.requests.provider")).setAutoWidth(true);
        waitingRequests.addColumn(app.rootology.access.AccessRequestView::login)
                .setHeader(getTranslation("space.login")).setAutoWidth(true);
        waitingRequests.addColumn(r -> r.email() == null ? "" : r.email())
                .setHeader(getTranslation("admin.requests.email")).setAutoWidth(true);
        // A text column: the motivation is the person's own words, never markup.
        waitingRequests.addColumn(app.rootology.access.AccessRequestView::motivation)
                .setHeader(getTranslation("admin.requests.motivation")).setFlexGrow(3);
        waitingRequests.addComponentColumn(r -> new HorizontalLayout(
                        RowActions.approve(getTranslation("admin.requests.approve"), e -> confirm(r, true)),
                        RowActions.reject(getTranslation("admin.requests.reject"), e -> confirm(r, false))))
                .setAutoWidth(true);
        waitingRequests.setAllRowsVisible(true);
        waitingRequests.setEmptyStateText(getTranslation("admin.requests.none"));

        decidedRequests.addComponentColumn(r -> DateText.dateTimeSpan(r.decidedAt(), this))
                .setHeader(getTranslation("admin.requests.decided")).setAutoWidth(true);
        decidedRequests.addColumn(app.rootology.access.AccessRequestView::login)
                .setHeader(getTranslation("space.login")).setAutoWidth(true);
        decidedRequests.addColumn(r -> getTranslation("provider." + r.provider().id()))
                .setHeader(getTranslation("admin.requests.provider")).setAutoWidth(true);
        decidedRequests.addColumn(r -> getTranslation(r.status() == AccessRequestStatus.APPROVED
                ? "admin.requests.approved" : "admin.requests.rejected"))
                .setHeader(getTranslation("admin.requests.status")).setAutoWidth(true);
        decidedRequests.addColumn(app.rootology.access.AccessRequestView::decidedBy)
                .setHeader(getTranslation("admin.requests.by")).setAutoWidth(true);
        decidedRequests.setAllRowsVisible(true);
        return new VerticalLayout(new H3(getTranslation("admin.requests")),
                new Paragraph(getTranslation("admin.requests.intro")), waitingRequests,
                new H4(getTranslation("admin.requests.history")), decidedRequests);
    }

    /** One decision, after a confirmation that says what it does. */
    private void confirm(app.rootology.access.AccessRequestView request, boolean approve) {
        Dialog dialog = new Dialog(getTranslation(approve ? "admin.requests.approve.title"
                : "admin.requests.reject.title"));
        dialog.add(new Paragraph(getTranslation(approve ? "admin.requests.approve.text"
                : "admin.requests.reject.text", request.login())));
        Button confirm = new Button(getTranslation(approve ? "admin.requests.approve"
                : "admin.requests.reject"), e -> {
            dialog.close();
            act(() -> {
                if (approve) {
                    accessRequests.approve(currentUser.login(), request.id());
                } else {
                    accessRequests.reject(currentUser.login(), request.id());
                }
            });
        });
        if (approve) {
            confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        } else {
            confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        }
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            UiErrors.show(this, e);
        }
        refresh();
    }

    private void refresh() {
        people.setItems(roles.people(currentUser.login()).entrySet().stream().toList());
        consumption.setItems(usage.report(7));
        history.setItems(roles.history(currentUser.login()));
        waitingRequests.setItems(accessRequests.pending(currentUser.login()));
        decidedRequests.setItems(accessRequests.decided(currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("admin.title") + " · Rootology";
    }
}
