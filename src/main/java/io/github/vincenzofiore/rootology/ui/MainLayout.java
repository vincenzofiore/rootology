package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.AppRole;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;

/** Shell with navigation between the comparison view and the escalation queue, and the signed-in user. */
@PermitAll
public class MainLayout extends AppLayout {

    private final NotificationService notifications;
    private final NotificationBroadcaster broadcaster;
    private final CurrentUser currentUser;
    private final Button bell = new Button();
    private Runnable stopListening;

    public MainLayout(AuthenticationContext authentication, CurrentUser currentUser, NotificationService notifications,
                      NotificationBroadcaster broadcaster) {
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.currentUser = currentUser;
        H1 title = new H1("Rootology");
        title.getStyle().set("font-size", "1.2rem").set("margin", "0 1rem");
        addToNavbar(title);
        ComboBox<SpaceSummary> space = new ComboBox<>();
        space.setItems(currentUser.spaces());
        space.setItemLabelGenerator(SpaceSummary::name);
        space.setValue(currentUser.space());
        space.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                currentUser.switchTo(e.getValue().id());
                UI.getCurrent().getPage().reload();
            }
        });
        space.getStyle().set("margin-left", "auto");
        Span who = new Span(currentUser.login());
        who.getStyle().set("margin-left", "1rem");
        Button logout = new Button(getTranslation("nav.logout"), e -> authentication.logout());
        logout.getStyle().set("margin", "0 1rem");
        // The unread notifications (#26), updated live through push while the page is open.
        bell.setIcon(VaadinIcon.BELL.create());
        bell.addThemeName("tertiary");
        bell.addClickListener(e -> UI.getCurrent().navigate(NotificationsView.class));
        showUnread();
        addToNavbar(space, bell, who, logout);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem(getTranslation("nav.ask"), AskView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.garden"), GardenView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.escalations"), EscalationView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.notifications"), NotificationsView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.history"), HistoryView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.knowledge"), KnowledgeView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.space"), SpaceView.class));
        nav.addItem(new SideNavItem(getTranslation("nav.sources"), SourcesView.class));
        if (currentUser.hasRole(AppRole.QA)) {
            nav.addItem(new SideNavItem(getTranslation("nav.compare"), ComparisonView.class));
        }
        if (currentUser.hasRole(AppRole.ADMIN)) {
            nav.addItem(new SideNavItem(getTranslation("nav.admin"), AdminView.class));
        }
        addToDrawer(nav);
    }

    private void showUnread() {
        int unread = notifications.unread(currentUser.login());
        bell.setText(unread == 0 ? "" : String.valueOf(unread));
        bell.setAriaLabel(getTranslation("nav.unread", unread));
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();
        stopListening = broadcaster.register(currentUser.login(), () -> ui.access(this::showUnread));
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent event) {
        if (stopListening != null) {
            stopListening.run();
        }
        super.onDetach(event);
    }
}
