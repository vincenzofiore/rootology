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
import com.vaadin.flow.component.html.Anchor;
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
import app.rootology.pipeline.GardenPipeline;
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
    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final Button bell = new Button();
    /** The space of the page being shown; choosing another one opens its gardens (#56). */
    private final ComboBox<SpaceSummary> space = new ComboBox<>();
    /** On a phone the navbar is full (#79): the same switcher lives in the side menu, below the name. */
    private final ComboBox<SpaceSummary> drawerSpace = new ComboBox<>();
    /** Light or dark (#31): automatic (the device's setting), light or dark; kept in this browser. */
    private final Button scheme = new Button();
    private final SideNav nav = new SideNav();
    private final Div where = new Div();
    /** The space and garden at the top of the drawer below 640 px, where the navbar hides them (#79, #88). */
    private final Div drawerWhere = new Div();
    /** Home, Ask, Plots and Waiting questions at the bottom of a phone inside a garden (#88). */
    private final Div bottomBar = new Div();
    /** The first Tab of the page is a way to the content, past the navbar and the whole side menu (#88). */
    private final Anchor skip = new Anchor();
    private ColorScheme.Value colorScheme = ColorScheme.Value.LIGHT_DARK;
    private Runnable stopListening;
    /** The context of the page being shown, so the bell opens the notifications from where the person is. */
    private long contextSpace;
    private long contextGarden;

    public MainLayout(AuthenticationContext authentication, CurrentUser currentUser, NotificationService notifications,
                      NotificationBroadcaster broadcaster, GardenService gardens, Ungardened ungardened,
                      GardenPipeline pipeline) {
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.currentUser = currentUser;
        this.gardens = gardens;
        this.ungardened = ungardened;
        this.pipeline = pipeline;
        H1 title = new H1(new Wordmark());
        title.addClassName("rootology-app-title");
        // On a phone the side menu hides itself (#31): the toggle opens it.
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel(getTranslation("nav.menu"));
        // The first Tab skips the navbar and the whole side menu (#88): it moves the focus into the content.
        skip.setText(getTranslation("nav.skip"));
        skip.addClassName("rootology-skip");
        skip.setRouterIgnore(true);
        skip.getElement().setAttribute("href", "#main-content");
        // In the browser, not through the server: the hash alone would leave the focus on the link.
        skip.getElement().executeJs("this.addEventListener('click', e => { e.preventDefault();"
                + " const main = document.getElementById('main-content'); if (main) { main.focus(); } })");
        addToNavbar(skip, toggle, title, where);
        where.addClassName("rootology-where");

        switcher(space, "rootology-space-switcher");
        switcher(drawerSpace, "rootology-space-switcher-narrow");
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
        // The end of the navbar stays together and reaches the right edge (#79); below 640 px the
        // switcher is hidden and the drawer's copy takes its place.
        Div end = new Div(space, scheme, bell, user);
        end.addClassName("rootology-navbar-end");
        addToNavbar(end);
        // The bar of a phone inside a garden (#88): hidden above 640 px, where the side menu is enough.
        bottomBar.addClassName("rootology-bottom-nav");
        bottomBar.getElement().setAttribute("role", "navigation");
        bottomBar.getElement().setAttribute("aria-label", getTranslation("nav.gardennavigation"));
        addToNavbar(bottomBar);
        // The menu fills the drawer and the service pages sit at its bottom.
        Div drawerSpaceBox = new Div(drawerSpace);
        drawerSpaceBox.addClassName("rootology-drawer-space");
        drawerWhere.addClassName("rootology-drawer-where");
        Div drawer = new Div(drawerSpaceBox, drawerWhere, nav, new ServiceLinks());
        drawer.addClassName("rootology-drawer");
        addToDrawer(drawer);
    }

    /** The plots are beds, not the home of the garden (#79): the same icon in the menu and in the links. */
    static final VaadinIcon PLOTS_ICON = VaadinIcon.GRID_BIG;

    /** A space switcher: choosing a space opens its gardens. Two of them, the navbar's and the drawer's. */
    private void switcher(ComboBox<SpaceSummary> box, String className) {
        box.addClassName(className);
        box.setItemLabelGenerator(SpaceSummary::name);
        box.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                currentUser.switchTo(e.getValue().id());
                UI.getCurrent().navigate(GardensView.class, new RouteParameters(
                        Map.of(Routed.SPACE, String.valueOf(e.getValue().id()))));
            }
        });
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
        // A garden in the route itself, not one carried in a query string (#56): only there is a page a
        // phone's bar belongs to (#88).
        long routeGarden = gardenId;
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
        drawerSpace.setItems(currentUser.spaces());
        if (!current.equals(space.getValue())) {
            space.setValue(current);
        }
        if (!current.equals(drawerSpace.getValue())) {
            drawerSpace.setValue(current);
        }
        showWhere(current, pageGarden);
        showDrawerWhere(current, pageGarden);
        menu(current, pageGarden);
        bottomMenu(current, routeGarden, target(event));
        getElement().getClassList().set("rootology-with-bottom-nav", routeGarden > 0);
        focusTarget();
    }

    /** The page being shown, the last of the chain: the layout is the first (#88, the bar's current item). */
    private static Class<?> target(AfterNavigationEvent event) {
        List<com.vaadin.flow.component.HasElement> chain = event.getActiveChain();
        return chain.isEmpty() ? null : chain.get(chain.size() - 1).getClass();
    }

    /**
     * The way in for the skip link (#88): the page's own element gets an id to jump to, and it may take the
     * focus even though it is not part of the Tab order.
     */
    private void focusTarget() {
        Component content = getContent();
        if (content != null) {
            content.getElement().setAttribute("id", "main-content");
            content.getElement().setAttribute("tabindex", "-1");
        }
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

    /**
     * The same "Space / Garden" at the top of the drawer (#88): the navbar hides it on a phone (#79), and the
     * drawer is where the person looks for it there.
     */
    private void showDrawerWhere(SpaceSummary space, long gardenId) {
        drawerWhere.removeAll();
        if (gardenId <= 0) {
            return;
        }
        String garden = gardens.garden(space.id(), currentUser.login(), gardenId).map(g -> g.name()).orElse("");
        drawerWhere.add(new Span(space.name()), new Span(" / "), new Span(garden));
    }

    /**
     * The bar of a phone inside a garden (#88): its home, Ask, the plots and the waiting questions, with the
     * same icons as the side menu. Above 640 px it is hidden and the side menu says everything. The waiting
     * count is read with the rest of the menu, so the bell's live counter is the only one that changes.
     */
    private void bottomMenu(SpaceSummary space, long gardenId, Class<?> target) {
        bottomBar.removeAll();
        if (gardenId <= 0) {
            return;
        }
        Map<String, String> here = Map.of(Routed.SPACE, String.valueOf(space.id()),
                Routed.GARDEN, String.valueOf(gardenId));
        int waiting = pipeline.pending(space.id(), gardenId, currentUser.login()).size();
        bottomBar.add(bottomItem(VaadinIcon.DASHBOARD, "nav.gardenhome", GardenHomeView.class, here, target, 0),
                bottomItem(VaadinIcon.COMMENT_ELLIPSIS_O, "nav.ask", AskView.class, here, target, 0),
                bottomItem(PLOTS_ICON, "nav.plots", AreasView.class, here, target, 0),
                bottomItem(VaadinIcon.HOURGLASS, "nav.waiting", EscalationView.class, here, target, waiting));
    }

    /**
     * One page of the bar: the icon and a short word, the whole word as its title and aria label, and a badge
     * of the waiting questions. The page being shown says so, as the side menu does.
     */
    private RouterLink bottomItem(VaadinIcon icon, String key, Class<? extends Component> view,
                                  Map<String, String> parameters, Class<?> target, int count) {
        RouterLink link = new RouterLink();
        link.setRoute(view, new RouteParameters(parameters));
        Span label = new Span(getTranslation(key));
        link.add(icon.create(), label);
        link.addClassName("rootology-bottom-nav-item");
        String described = count > 0 ? getTranslation("nav.waiting.count", count) : getTranslation(key);
        link.getElement().setAttribute("aria-label", described);
        link.getElement().setAttribute("title", described);
        if (view == target) {
            link.addClassName("rootology-bottom-nav-current");
            link.getElement().setAttribute("aria-current", "page");
        }
        if (count > 0) {
            Span badge = new Span(String.valueOf(count));
            badge.addClassName("rootology-bottom-nav-badge");
            badge.getElement().setAttribute("aria-hidden", "true");
            link.add(badge);
        }
        return link;
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
            nav.addItem(new SideNavItem(getTranslation("nav.plots"), AreasView.class, new RouteParameters(here),
                    PLOTS_ICON.create()));
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
