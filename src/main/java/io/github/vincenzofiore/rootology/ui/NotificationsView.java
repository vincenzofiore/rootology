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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.notifications.NotificationPreferences;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.notifications.NotificationView;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** The signed-in person's notifications (#26), a way to the waiting question, and their preferences. */
@Route(value = "notifications", layout = MainLayout.class)
@PermitAll
public class NotificationsView extends VerticalLayout implements HasDynamicTitle {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Rome"));

    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final Grid<NotificationView> grid = new Grid<>();

    public NotificationsView(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;

        grid.addColumn(n -> WHEN.format(n.createdAt())).setHeader(getTranslation("notifications.when")).setAutoWidth(true);
        grid.addColumn(n -> (n.unread() ? "● " : "") + (n.weather() ? n.title() : getTranslation("notifications.waiting")))
                .setHeader(getTranslation("notifications.what")).setAutoWidth(true);
        grid.addColumn(NotificationView::body).setHeader(getTranslation("notifications.details")).setFlexGrow(3);
        grid.addColumn(n -> n.weather() ? "" : n.resolvedAt() == null ? getTranslation("notifications.status.waiting")
                        : getTranslation("notifications.status.answered", n.resolvedBy()))
                .setHeader(getTranslation("notifications.status")).setAutoWidth(true);
        grid.addComponentColumn(n -> {
            Button open = new Button(getTranslation("notifications.open"), e -> open(n));
            open.setVisible(n.resolvedAt() == null);
            return open;
        }).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        Button allRead = new Button(getTranslation("notifications.allread"), e -> {
            notifications.markRead(currentUser.login(), null);
            refresh();
        });

        add(new H3(getTranslation("notifications.title")), allRead, grid, preferences());
        refresh();
    }

    /** To the waiting question, or to the garden for a weather alert: switching to its space if needed. */
    private void open(NotificationView n) {
        notifications.markRead(currentUser.login(), n.id());
        if (currentUser.space().id() != n.spaceId()) {
            currentUser.switchTo(n.spaceId());
        }
        getUI().ifPresent(ui -> {
            if (n.weather()) {
                ui.navigate(GardenView.class);
            } else {
                ui.navigate(EscalationView.class);
            }
        });
    }

    private VerticalLayout preferences() {
        NotificationPreferences p = notifications.preferences(currentUser.login());
        TextField email = new TextField(getTranslation("notifications.email"));
        email.setWidth("20rem");
        email.setValue(p.email() == null ? "" : p.email());
        Checkbox escalations = new Checkbox(getTranslation("notifications.email.escalations"), p.emailEscalations());
        Checkbox weather = new Checkbox(getTranslation("notifications.email.weather"), p.emailWeather());
        TimePicker quietFrom = new TimePicker(getTranslation("notifications.quiet.from"), p.quietFrom());
        TimePicker quietTo = new TimePicker(getTranslation("notifications.quiet.to"), p.quietTo());
        Button save = new Button(getTranslation("common.save"), e -> {
            try {
                notifications.save(currentUser.login(), new NotificationPreferences(email.getValue(),
                        escalations.getValue(), weather.getValue(), quietFrom.getValue(), quietTo.getValue()));
                Notification.show(getTranslation("common.saved"));
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        });
        HorizontalLayout quiet = new HorizontalLayout(quietFrom, quietTo);
        return new VerticalLayout(new H4(getTranslation("notifications.preferences")),
                new Paragraph(getTranslation("notifications.preferences.intro")),
                email, escalations, weather, quiet, save);
    }

    private void refresh() {
        grid.setItems(notifications.of(currentUser.login(), 50));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notifications.title") + " · Rootology";
    }
}
