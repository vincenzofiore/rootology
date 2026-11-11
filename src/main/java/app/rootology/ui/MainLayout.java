package app.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;
import app.rootology.garden.GardenService;
import app.rootology.notifications.NotificationService;
import app.rootology.roles.AppRole;
import app.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;

import java.util.List;
import java.util.Map;

/**
 * Shell of the app: the space switcher, the person and the bell always; the side menu of the level the
 * page works at (#56), read from the page's own route: the space's gardens and management outside a
 * garden, the garden's pages inside it. The URL is the only source of the context, so two browser tabs,
 * one on each garden, never mix.
 */
@PermitAll
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final NotificationService notifications;
    private final NotificationBroadcaster broadcaster;
    private final GardenService gardens;
    private final Ungardened ungardened;
    private final CurrentUser currentUser;
    private final Button bell = new Button();
    /** The space of the page being shown; choosing another one opens its gardens. */
    private final ComboBox<SpaceSummary> space = new ComboBox<>();
    /** Light or dark (#31): automatic (the device's setting), light or dark; kept in this browser. */
    private final Button scheme = new Button();
    private final SideNav nav = new SideNav();
    private final Div where = new Div();
    private ColorScheme.Value colorScheme = ColorScheme.Value.LIGHT_DARK;
    private Runnable stopListening;
    /** The context of the page being shown, so the bell opens the notifications from where the person is. */
    private long contextSpace;
    private long contextGarden;

    public MainLayout(AuthenticationContext authentication, CurrentUser currentUser, NotificationService notifications,
                      NotificationBroadcaster broadcaster, GardenService gardens, Ungardened ungardened) {
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.currentUser = currentUser;
        this.gardens = gardens;
        this.ungardened = ungardened;
        H1 title = new H1(new Wordmark());
        title.addClassName("rootology-app-title");
        // On a phone the side menu hides itself (#31): the toggle opens it.
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel(getTranslation("nav.menu"));
        addToNavbar(toggle, title, where);
        where.addClassName("rootology-where");

        space.addClassName("rootology-space-switcher");
        space.setItemLabelGenerator(SpaceSummary::name);
        space.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                currentUser.switchTo(e.getValue().id());
                UI.getCurrent().navigate(GardensView.class, new RouteParameters(
                        Map.of(Routed.SPACE, String.valueOf(e.getValue().id()))));
            }
        });
        // The person's menu, top right (#40): their account and signing out. The login hides on a phone.
        Avatar avatar = new Avatar(currentUser.login());
        avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
        avatar.getElement().setAttribute("aria-hidden", "true");
        Span who = new Span(currentUser.login());
        who.addClassName("rootology-wide-only");
        Div person = new Div(avatar, who);
        person.addClassName("rootology-person");
        MenuBar user = new MenuBar();
        user.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        user.addClassName("rootology-user-menu");
        user.getElement().setAttribute("aria-label", getTranslation("nav.usermenu"));
        SubMenu items = user.addItem(person).getSubMenu();
        items.addItem(menuItem(VaadinIcon.USER, "nav.account"), e -> UI.getCurrent().navigate(AccountView.class));
        items.addItem(menuItem(VaadinIcon.BELL_O, "nav.notifications"), e -> openNotifications());
        items.addItem(menuItem(VaadinIcon.SIGN_OUT, "nav.logout"), e -> authentication.logout());
        // The unread notifications (#26), updated live through push while the page is open.
        bell.setIcon(VaadinIcon.BELL.create());
        bell.addThemeName("tertiary");
        bell.addClickListener(e -> openNotifications());
        showUnread();
        scheme.addThemeName("tertiary");
        scheme.addClickListener(e -> nextScheme());
        addToNavbar(space, scheme, bell, user);
        // The menu fills the drawer and the service pages sit at its bottom.
        Div drawer = new Div(nav, new ServiceLinks());
        drawer.addClassName("rootology-drawer");
        addToDrawer(drawer);
    }

    /**
     * Builds the menu and the way to the space and garden from the route of the page being shown (#56),
     * never from the session's.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        if (currentUser.pending()) {
            // A pending person (#65) is on the request page by now: this layout has nothing to show them.
            return;
        }
        RouteParameters parameters = event.getRouteParameters();
        QueryParameters query = event.getLocation().getQueryParameters();
        long spaceId = number(parameters, Routed.SPACE);
        long gardenId = number(parameters, Routed.GARDEN);
        // The notifications page carries the context in its query string, so the bell keeps it (#56).
        if (spaceId <= 0) {
            spaceId = query.getSingleParameter(NotificationsView.SPACE).map(MainLayout::parse).orElse(-1L);
            gardenId = query.getSingleParameter(NotificationsView.GARDEN).map(MainLayout::parse).orElse(-1L);
        }
        contextSpace = spaceId;
        contextGarden = gardenId > 0 ? gardenId : 0;
        long pageSpace = contextSpace;
        long pageGarden = contextGarden;
        SpaceSummary current = currentUser.spaces().stream().filter(s -> s.id() == pageSpace).findFirst()
                .orElse(currentUser.space());
        space.setItems(currentUser.spaces());
        if (!current.equals(space.getValue())) {
            space.setValue(current);
        }
        showWhere(current, pageGarden);
        menu(current, pageGarden);
    }

    /**
     * Notifications, told where the person is so the page asks only when opening one changes the context
     * (#56). The query parameters are a hint: the page never reads data through them.
     */
    private void openNotifications() {
        Map<String, List<String>> query = new java.util.HashMap<>();
        if (contextSpace > 0) {
            query.put(NotificationsView.SPACE, List.of(String.valueOf(contextSpace)));
            if (contextGarden > 0) {
                query.put(NotificationsView.GARDEN, List.of(String.valueOf(contextGarden)));
            }
        }
        UI.getCurrent().navigate(NotificationsView.class, new QueryParameters(query));
    }

    /** "Space / Garden" while inside a garden; the two names lead back to the overview and the garden. */
    private void showWhere(SpaceSummary space, long gardenId) {
        where.removeAll();
        if (gardenId <= 0) {
            return;
        }
        String garden = gardens.garden(space.id(), currentUser.login(), gardenId).map(g -> g.name()).orElse("");
        where.add(link(space.name(), GardensView.class, Map.of(Routed.SPACE, String.valueOf(space.id()))),
                new Span("/"), link(garden, GardenHomeView.class, Map.of(Routed.SPACE, String.valueOf(space.id()),
                        Routed.GARDEN, String.valueOf(gardenId))));
    }

    private void menu(SpaceSummary space, long gardenId) {
        nav.removeAll();
        Map<String, String> here = gardenId > 0
                ? Map.of(Routed.SPACE, String.valueOf(space.id()), Routed.GARDEN, String.valueOf(gardenId))
                : Map.of(Routed.SPACE, String.valueOf(space.id()));
        SideNavItem gardens = new SideNavItem(getTranslation("nav.gardens"), GardensView.class,
                new RouteParameters(Map.of(Routed.SPACE, String.valueOf(space.id()))), VaadinIcon.GRID_SMALL.create());
        nav.addItem(gardens);
        if (gardenId > 0) {
            nav.addItem(new SideNavItem(getTranslation("nav.gardenhome"), GardenHomeView.class, new RouteParameters(here),
                    VaadinIcon.DASHBOARD.create()));
            nav.addItem(new SideNavItem(getTranslation("nav.plots"), PlotsView.class, new RouteParameters(here),
                    VaadinIcon.HOME_O.create()));
        }
        nav.addItem(new SideNavItem(getTranslation("nav.ask"), AskView.class, new RouteParameters(here),
                VaadinIcon.COMMENT_ELLIPSIS_O.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.escalations"), EscalationView.class, new RouteParameters(here),
                VaadinIcon.HOURGLASS.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.history"), HistoryView.class, new RouteParameters(here),
                VaadinIcon.CLOCK.create()));
        if (gardenId > 0) {
            nav.addItem(new SideNavItem(getTranslation("nav.knowledge"), KnowledgeView.class, new RouteParameters(here),
                    VaadinIcon.BOOK.create()));
        }
        if (gardenId == 0) {
            // The questions the space asked outside a garden (#56): offered only when there are some.
            if (ungardened.any(space.id(), currentUser.login())) {
                nav.addItem(new SideNavItem(getTranslation("nav.ungardened"), EscalationView.class,
                        new RouteParameters(Map.of(Routed.SPACE, String.valueOf(space.id()))),
                        VaadinIcon.QUESTION.create()));
            }
            nav.addItem(new SideNavItem(getTranslation("nav.members"), MembersView.class, new RouteParameters(
                    Map.of(Routed.SPACE, String.valueOf(space.id()))), VaadinIcon.USERS.create()));
            nav.addItem(new SideNavItem(getTranslation("nav.invitations"), InvitationsView.class,
                    new RouteParameters(Map.of(Routed.SPACE, String.valueOf(space.id()))),
                    VaadinIcon.ENVELOPE.create()));
            if (currentUser.hasRole(AppRole.CURATOR)) {
                nav.addItem(new SideNavItem(getTranslation("nav.curator"), CuratorView.class, VaadinIcon.CHECK.create()));
            }
            if (currentUser.hasRole(AppRole.QA)) {
                nav.addItem(new SideNavItem(getTranslation("nav.compare"), ComparisonView.class, VaadinIcon.FLASK.create()));
            }
            if (currentUser.hasRole(AppRole.ADMIN)) {
                nav.addItem(new SideNavItem(getTranslation("nav.admin"), AdminView.class, VaadinIcon.COG_O.create()));
            }
        }
    }

    private static RouterLink link(String text, Class<? extends Component> view,
                                   Map<String, String> parameters) {
        return new RouterLink(text, view, new RouteParameters(parameters));
    }

    private static long number(RouteParameters parameters, String name) {
        return parameters.get(name).map(MainLayout::parse).orElse(-1L);
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private Div menuItem(VaadinIcon icon, String key) {
        Div item = new Div(icon.create(), new Span(getTranslation(key)));
        item.addClassName("rootology-menu-item");
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
