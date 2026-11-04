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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.notifications.NotificationPreferences;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.notifications.NotificationView;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** The signed-in person's notifications (#26), a way to the waiting question, and their preferences. */
@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications · Rootology")
@PermitAll
public class NotificationsView extends VerticalLayout {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Rome"));

    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final Grid<NotificationView> grid = new Grid<>();

    public NotificationsView(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;

        grid.addColumn(n -> WHEN.format(n.createdAt())).setHeader("When").setAutoWidth(true);
        grid.addColumn(n -> (n.unread() ? "● " : "") + n.title()).setHeader("What").setAutoWidth(true);
        grid.addColumn(NotificationView::body).setHeader("Question").setFlexGrow(3);
        grid.addColumn(n -> n.resolvedAt() == null ? "waiting" : "answered by " + n.resolvedBy())
                .setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(n -> {
            Button open = new Button("Open", e -> open(n));
            open.setVisible(n.resolvedAt() == null);
            return open;
        }).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        Button allRead = new Button("Mark all as read", e -> {
            notifications.markRead(currentUser.login(), null);
            refresh();
        });

        add(new H3("Notifications"), allRead, grid, preferences());
        refresh();
    }

    /** To the waiting question: switch to its space if needed, then the escalations. */
    private void open(NotificationView n) {
        notifications.markRead(currentUser.login(), n.id());
        if (currentUser.space().id() != n.spaceId()) {
            currentUser.switchTo(n.spaceId());
        }
        getUI().ifPresent(ui -> ui.navigate(EscalationView.class));
    }

    private VerticalLayout preferences() {
        NotificationPreferences p = notifications.preferences(currentUser.login());
        TextField email = new TextField("Email for notifications");
        email.setWidth("20rem");
        email.setValue(p.email() == null ? "" : p.email());
        Checkbox escalations = new Checkbox("Email me when a question waits for an answer", p.emailEscalations());
        TimePicker quietFrom = new TimePicker("Quiet from", p.quietFrom());
        TimePicker quietTo = new TimePicker("Quiet until", p.quietTo());
        Button save = new Button("Save", e -> {
            try {
                notifications.save(currentUser.login(), new NotificationPreferences(email.getValue(),
                        escalations.getValue(), quietFrom.getValue(), quietTo.getValue()));
                Notification.show("Saved");
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        });
        HorizontalLayout quiet = new HorizontalLayout(quietFrom, quietTo);
        return new VerticalLayout(new H4("Preferences"),
                new Paragraph("In-app notifications are always on. Emails group the questions of a couple of minutes "
                        + "into one message and wait for the end of your quiet hours (Italian time)."),
                email, escalations, quiet, save);
    }

    private void refresh() {
        grid.setItems(notifications.of(currentUser.login(), 50));
    }
}
