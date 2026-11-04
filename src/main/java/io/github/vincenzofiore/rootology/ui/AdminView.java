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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.roles.AppRole;
import io.github.vincenzofiore.rootology.roles.RoleGrantView;
import io.github.vincenzofiore.rootology.roles.RoleService;
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

    public AdminView(RoleService roles, CurrentUser currentUser) {
        this.roles = roles;
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
        refresh();
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
        history.setItems(roles.history(currentUser.login()));
    }
}
