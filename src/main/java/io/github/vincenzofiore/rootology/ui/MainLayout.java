package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;

/** Shell with navigation between the comparison view and the escalation queue, and the signed-in user. */
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authentication, CurrentUser currentUser) {
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
        Button logout = new Button("Log out", e -> authentication.logout());
        logout.getStyle().set("margin", "0 1rem");
        addToNavbar(space, who, logout);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Garden", GardenView.class));
        nav.addItem(new SideNavItem("Compare", ComparisonView.class));
        nav.addItem(new SideNavItem("Escalations", EscalationView.class));
        nav.addItem(new SideNavItem("History", HistoryView.class));
        nav.addItem(new SideNavItem("Space", SpaceView.class));
        addToDrawer(nav);
    }
}
