package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;

/** Shell with navigation between the comparison view and the escalation queue. */
public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("Rootology");
        title.getStyle().set("font-size", "1.2rem").set("margin", "0 1rem");
        addToNavbar(title);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Compare", ComparisonView.class));
        nav.addItem(new SideNavItem("Escalations", EscalationView.class));
        addToDrawer(nav);
    }
}
