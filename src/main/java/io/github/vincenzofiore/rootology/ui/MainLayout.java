package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Shell with navigation between the comparison view and the escalation queue, and the signed-in user. */
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authentication) {
        H1 title = new H1("Rootology");
        title.getStyle().set("font-size", "1.2rem").set("margin", "0 1rem");
        addToNavbar(title);
        authentication.getAuthenticatedUser(OAuth2User.class).ifPresent(user -> {
            Span who = new Span(String.valueOf(user.<Object>getAttribute("login")));
            who.getStyle().set("margin-left", "auto");
            Button logout = new Button("Log out", e -> authentication.logout());
            logout.getStyle().set("margin", "0 1rem");
            addToNavbar(who, logout);
        });

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Compare", ComparisonView.class));
        nav.addItem(new SideNavItem("Escalations", EscalationView.class));
        addToDrawer(nav);
    }
}
