package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.AppRole;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;

/** Shell with navigation between the comparison view and the escalation queue, and the signed-in user. */
@PermitAll
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final NotificationService notifications;
    private final NotificationBroadcaster broadcaster;
    private final CurrentUser currentUser;
    private final Button bell = new Button();
    /** The current space; a view may switch it on entering (a notification's link, #51). */
    private final ComboBox<SpaceSummary> space = new ComboBox<>();
    /** Light or dark (#31): automatic (the device's setting), light or dark; kept in this browser. */
    private final Button scheme = new Button();
    private ColorScheme.Value colorScheme = ColorScheme.Value.LIGHT_DARK;
    private Runnable stopListening;

    public MainLayout(AuthenticationContext authentication, CurrentUser currentUser, NotificationService notifications,
                      NotificationBroadcaster broadcaster) {
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.currentUser = currentUser;
        H1 title = new H1(new Wordmark());
        title.addClassName("rootology-app-title");
        // On a phone the side menu hides itself (#31): the toggle opens it.
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel(getTranslation("nav.menu"));
        addToNavbar(toggle, title);
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
        space.setWidth("12rem");
        space.getStyle().set("min-width", "6rem");
        // The person's menu, top right (#40): their account and signing out. The login hides on a phone.
        Avatar avatar = new Avatar(currentUser.login());
        avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
        avatar.getElement().setAttribute("aria-hidden", "true");
        Span who = new Span(currentUser.login());
        who.addClassName("rootology-wide-only");
        Div person = new Div(avatar, who);
        person.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");
        MenuBar user = new MenuBar();
        user.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        user.getElement().setAttribute("aria-label", getTranslation("nav.usermenu"));
        user.getStyle().set("margin-right", "0.5rem");
        SubMenu items = user.addItem(person).getSubMenu();
        items.addItem(menuItem(VaadinIcon.USER, "nav.account"), e -> UI.getCurrent().navigate(AccountView.class));
        items.addItem(menuItem(VaadinIcon.BELL_O, "nav.notifications"), e -> UI.getCurrent().navigate(NotificationsView.class));
        items.addItem(menuItem(VaadinIcon.SIGN_OUT, "nav.logout"), e -> authentication.logout());
        // The unread notifications (#26), updated live through push while the page is open.
        bell.setIcon(VaadinIcon.BELL.create());
        bell.addThemeName("tertiary");
        bell.addClickListener(e -> UI.getCurrent().navigate(NotificationsView.class));
        showUnread();
        scheme.addThemeName("tertiary");
        scheme.addClickListener(e -> nextScheme());
        addToNavbar(space, scheme, bell, user);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem(getTranslation("nav.home"), HomeView.class, VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.ask"), AskView.class, VaadinIcon.COMMENT_ELLIPSIS_O.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.garden"), GardenView.class, VaadinIcon.HOME_O.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.escalations"), EscalationView.class, VaadinIcon.HOURGLASS.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.notifications"), NotificationsView.class, VaadinIcon.BELL_O.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.history"), HistoryView.class, VaadinIcon.CLOCK.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.knowledge"), KnowledgeView.class, VaadinIcon.BOOK.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.space"), SpaceView.class, VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.sources"), SourcesView.class, VaadinIcon.BOOKMARK_O.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.privacy"), PrivacyView.class, VaadinIcon.SHIELD.create()));
        if (currentUser.hasRole(AppRole.QA)) {
            nav.addItem(new SideNavItem(getTranslation("nav.compare"), ComparisonView.class, VaadinIcon.FLASK.create()));
        }
        if (currentUser.hasRole(AppRole.ADMIN)) {
            nav.addItem(new SideNavItem(getTranslation("nav.admin"), AdminView.class, VaadinIcon.COG_O.create()));
        }
        addToDrawer(nav);
    }

    /** Shows the space the view ended up in, also when the view switched it from a link (#51). */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (!currentUser.space().equals(space.getValue())) {
            space.setItems(currentUser.spaces());
            space.setValue(currentUser.space());
        }
    }

    private Div menuItem(VaadinIcon icon, String key) {
        Div item = new Div(icon.create(), new Span(getTranslation(key)));
        item.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");
        return item;
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
        ui.getPage().executeJs("return localStorage.getItem($0)", SCHEME_KEY).then(String.class, saved -> {
            if (saved != null) {
                applyScheme(java.util.Arrays.stream(SCHEMES).filter(s -> s.name().equals(saved)).findFirst()
                        .orElse(ColorScheme.Value.LIGHT_DARK));
            }
        });
        showScheme();
    }

    @Override
    protected void onDetach(com.vaadin.flow.component.DetachEvent event) {
        if (stopListening != null) {
            stopListening.run();
        }
        super.onDetach(event);
    }

    static final String SCHEME_KEY = "rootology.colorScheme";
    private static final ColorScheme.Value[] SCHEMES =
            {ColorScheme.Value.LIGHT_DARK, ColorScheme.Value.LIGHT, ColorScheme.Value.DARK};

    private void nextScheme() {
        int next = (java.util.Arrays.asList(SCHEMES).indexOf(colorScheme) + 1) % SCHEMES.length;
        applyScheme(SCHEMES[next]);
        getUI().ifPresent(ui -> ui.getPage().executeJs("localStorage.setItem($0, $1)", SCHEME_KEY, colorScheme.name()));
    }

    private void applyScheme(ColorScheme.Value value) {
        colorScheme = value;
        getUI().ifPresent(ui -> ui.getPage().setColorScheme(value));
        showScheme();
    }

    private void showScheme() {
        String key = switch (colorScheme) {
            case LIGHT -> "theme.light";
            case DARK -> "theme.dark";
            default -> "theme.auto";
        };
        scheme.setIcon((colorScheme == ColorScheme.Value.DARK ? VaadinIcon.MOON_O
                : colorScheme == ColorScheme.Value.LIGHT ? VaadinIcon.SUN_O : VaadinIcon.ADJUST).create());
        scheme.setTooltipText(getTranslation(key));
        scheme.setAriaLabel(getTranslation(key));
    }
}
