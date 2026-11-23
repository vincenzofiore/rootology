package app.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
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
 * Shell of the app, navigation by levels (#132, ADR 0002): the space with its gardens, and a garden. The top
 * bar has a way back to the level above, the clickable path, the level's sections as actions with a count, and
 * the space switcher, the theme, the bell and the person's menu; on a phone the same actions sit in a bar at the
 * bottom, the level's home first. The level is read from the page's own route (#56): the URL is the only
 * source of the context, so two browser tabs, one on each garden, never mix.
 */
@PermitAll
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final NotificationService notifications;
    private final NotificationBroadcaster broadcaster;
    private final GardenService gardens;
    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;
    private final Button bell = new Button();
    private final Avatar avatar = new Avatar();
    private final Span who = new Span();
    /** The space of the page being shown; choosing another one opens its gardens (#56). */
    private final ComboBox<SpaceSummary> space = new ComboBox<>();
    /** Light or dark (#31): automatic (the device's setting), light or dark; kept in this browser. */
    private final Button scheme = new Button();
    private final Div where = new Div();
    /** The way to the level above (#132): the garden's home from its sections, the gardens from the rest. */
    private final Div back = new Div();
    /** The level's sections as actions, always visible above 640 px (#132). */
    private final Div actions = new Div();
    /** The same actions at the bottom of a phone, the level's home first (#88, #132). */
    private final Div bottomBar = new Div();
    /** The first Tab of the page is a way to the content, past the navbar (#88). */
    private final Anchor skip = new Anchor();
    private ColorScheme.Value colorScheme = ColorScheme.Value.LIGHT_DARK;
    private Runnable stopListening;
    /** The context of the page being shown, so the bell opens the notifications from where the person is. */
    private long contextSpace;
    private long contextGarden;

    public MainLayout(AuthenticationContext authentication, CurrentUser currentUser, NotificationService notifications,
                      NotificationBroadcaster broadcaster, GardenService gardens, GardenPipeline pipeline) {
        this.notifications = notifications;
        this.broadcaster = broadcaster;
        this.currentUser = currentUser;
        this.gardens = gardens;
        this.pipeline = pipeline;
        H1 title = new H1(new Wordmark());
        title.addClassName("rootology-app-title");
        // The first Tab skips the navbar (#88): it moves the focus into the content.
        skip.setText(getTranslation("nav.skip"));
        skip.addClassName("rootology-skip");
        skip.setRouterIgnore(true);
        skip.getElement().setAttribute("href", "#main-content");
        // In the browser, not through the server: the hash alone would leave the focus on the link.
        // The tabindex lives only while the content has the focus: a shadow host with tabindex -1 takes all of
        // its content out of the Tab order.
        skip.getElement().executeJs("this.addEventListener('click', e => { e.preventDefault();"
                + " const main = document.getElementById('main-content'); if (main) {"
                + " main.setAttribute('tabindex', '-1'); main.focus();"
                + " main.addEventListener('blur', () => main.removeAttribute('tabindex'), { once: true }); } })");
        // The name always comes with the slogan (#87).
        back.addClassName("rootology-back");
        addToNavbar(skip, back, Wordmark.withSlogan(title), where);
        where.addClassName("rootology-where");
        where.getElement().setAttribute("role", "navigation");
        where.getElement().setAttribute("aria-label", getTranslation("nav.where"));
        actions.addClassName("rootology-level-actions");
        actions.getElement().setAttribute("role", "navigation");
        actions.getElement().setAttribute("aria-label", getTranslation("nav.sections"));

        switcher(space, "rootology-space-switcher");
        // The person's menu, top right (#40): their account and signing out. The name hides on a phone; it is
        // the display name the person chose (#106), never the login.
        showName();
        avatar.addThemeVariants(AvatarVariant.LUMO_XSMALL);
        avatar.getElement().setAttribute("aria-hidden", "true");
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
        // On a phone the switcher is hidden (#79): the person changes space here (#132).
        List<SpaceSummary> all = currentUser.pending() ? List.of() : currentUser.spaces();
        if (all.size() > 1) {
            SubMenu spaces = items.addItem(menuItem(VaadinIcon.EXCHANGE, "nav.spaces")).getSubMenu();
            all.forEach(s -> spaces.addItem(s.name(), e -> openSpace(s)));
        }
        // The pages of a role, rare and for few people (#132): here rather than in the bar.
        if (currentUser.hasRole(AppRole.CURATOR)) {
            items.addItem(menuItem(VaadinIcon.CHECK, "nav.curator"), e -> UI.getCurrent().navigate(CuratorView.class));
        }
        if (currentUser.hasRole(AppRole.QA)) {
            items.addItem(menuItem(VaadinIcon.FLASK, "nav.compare"), e -> UI.getCurrent().navigate(ComparisonView.class));
        }
        if (currentUser.hasRole(AppRole.ADMIN)) {
            items.addItem(menuItem(VaadinIcon.COG_O, "nav.admin"), e -> UI.getCurrent().navigate(AdminView.class));
        }
        // Sources and Privacy are public pages (PublicLayout): a full page load, as their links always were.
        items.addItem(menuItem(VaadinIcon.OPEN_BOOK, "nav.sources"),
                e -> UI.getCurrent().getPage().setLocation("/" + SourcesView.ROUTE));
        items.addItem(menuItem(VaadinIcon.LOCK, "nav.privacy"),
                e -> UI.getCurrent().getPage().setLocation("/" + PrivacyView.ROUTE));
        items.addItem(menuItem(VaadinIcon.SIGN_OUT, "nav.logout"), e -> authentication.logout());
        // The unread notifications (#26), updated live through push while the page is open.
        bell.setIcon(VaadinIcon.BELL.create());
        bell.addThemeName("tertiary");
        bell.addClickListener(e -> openNotifications());
        showUnread();
        scheme.addThemeName("tertiary");
        scheme.addClickListener(e -> nextScheme());
        // The end of the navbar stays together and reaches the right edge (#79); below 640 px the
        // switcher and the actions are hidden: the person's menu and the bottom bar take their place.
        Div end = new Div(actions, space, scheme, bell, user);
        end.addClassName("rootology-navbar-end");
        addToNavbar(end);
        // The bar of a phone (#88, #132): hidden above 640 px, where the top bar's actions are enough.
        bottomBar.addClassName("rootology-bottom-nav");
        bottomBar.getElement().setAttribute("role", "navigation");
        bottomBar.getElement().setAttribute("aria-label", getTranslation("nav.sections"));
        addToNavbar(bottomBar);
    }

    /** Opens a space's gardens, from the switcher or the person's menu. */
    private void openSpace(SpaceSummary chosen) {
        currentUser.switchTo(chosen.id());
        UI.getCurrent().navigate(GardensView.class, new RouteParameters(
                Map.of(Routed.SPACE, String.valueOf(chosen.id()))));
    }

    /** The plots are beds, not the home of the garden (#79): the same icon in the bar and in the links. */
    static final VaadinIcon PLOTS_ICON = VaadinIcon.GRID_BIG;

    /** The space switcher: choosing a space opens its gardens. */
    private void switcher(ComboBox<SpaceSummary> box, String className) {
        box.addClassName(className);
        box.setItemLabelGenerator(SpaceSummary::name);
        box.getElement().setAttribute("aria-label", getTranslation("nav.spaces"));
        box.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                openSpace(e.getValue());
            }
        });
    }

    /**
     * Builds the actions and the way to the space and garden from the route of the page being shown (#56),
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
        Class<?> target = target(event);
        showWhere(current, pageGarden);
        showBack(current, pageGarden, target);
        List<Action> level = actions(current, pageGarden);
        showActions(level, target);
        bottomMenu(current, pageGarden, level, target);
        getElement().getClassList().set("rootology-with-bottom-nav", true);
        focusTarget();
    }

    /** The page being shown: the one of the chain that is not this layout (#88, #132: the current action). */
    private static Class<?> target(AfterNavigationEvent event) {
        return event.getActiveChain().stream().filter(c -> !(c instanceof MainLayout)).findFirst()
                .map(Object::getClass).orElse(null);
    }

    /**
     * The way in for the skip link (#88): the page's own element gets an id to jump to; the link makes it
     * focusable only while it has the focus.
     */
    private void focusTarget() {
        Component content = getContent();
        if (content != null) {
            content.getElement().setAttribute("id", "main-content");
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

    /** "Space / Garden": the two names lead to the gardens and to the garden's home (#56, #132). */
    private void showWhere(SpaceSummary space, long gardenId) {
        where.removeAll();
        where.add(link(space.name(), GardensView.class, Map.of(Routed.SPACE, String.valueOf(space.id()))));
        if (gardenId > 0) {
            where.add(new Span("/"), link(gardenName(space, gardenId), GardenHomeView.class,
                    Map.of(Routed.SPACE, String.valueOf(space.id()), Routed.GARDEN, String.valueOf(gardenId))));
        }
    }

    private String gardenName(SpaceSummary space, long gardenId) {
        return gardens.garden(space.id(), currentUser.login(), gardenId).map(g -> g.name()).orElse("");
    }

    /**
     * The arrow to the level above (#132): from a garden's section to its home, from anywhere else to the
     * space's gardens; none on the gardens themselves. The parent, never the browser's history, so the person
     * always knows where it leads.
     */
    private void showBack(SpaceSummary space, long gardenId, Class<?> target) {
        back.removeAll();
        if (target == GardensView.class) {
            return;
        }
        boolean toGarden = gardenId > 0 && target != GardenHomeView.class;
        RouterLink link = toGarden
                ? link("", GardenHomeView.class, Map.of(Routed.SPACE, String.valueOf(space.id()),
                        Routed.GARDEN, String.valueOf(gardenId)))
                : link("", GardensView.class, Map.of(Routed.SPACE, String.valueOf(space.id())));
        link.add(VaadinIcon.ARROW_LEFT.create());
        String label = getTranslation("nav.back", toGarden ? gardenName(space, gardenId) : getTranslation("nav.gardens"));
        link.getElement().setAttribute("aria-label", label);
        link.getElement().setAttribute("title", label);
        back.add(link);
    }

    /** A section of the level (#132): its icon, its word, its page and the count that helps a decision. */
    record Action(VaadinIcon icon, String key, Class<? extends Component> view, Map<String, String> parameters,
                  int count, boolean waiting) {
    }

    /**
     * The sections of the level (#132, ADR 0002). Inside a garden: its areas, Ask, the waiting questions and
     * the knowledge; outside: Ask, the waiting questions, the knowledge and the members. The waiting questions
     * only for whoever can answer them (#104): a guest follows them in Ask's history.
     */
    List<Action> actions(SpaceSummary space, long gardenId) {
        Map<String, String> spaceOnly = Map.of(Routed.SPACE, String.valueOf(space.id()));
        Map<String, String> here = gardenId > 0
                ? Map.of(Routed.SPACE, String.valueOf(space.id()), Routed.GARDEN, String.valueOf(gardenId))
                : spaceOnly;
        List<Action> level = new java.util.ArrayList<>();
        if (gardenId > 0) {
            level.add(new Action(PLOTS_ICON, "nav.plots", AreasView.class, here,
                    gardens.plots(space.id(), currentUser.login(), gardenId).size(), false));
        }
        // Questions holds Ask and the waiting questions (#132): its count is how many wait for whoever can answer.
        int waiting = !currentUser.canContribute(space.id()) ? 0
                : (gardenId > 0 ? pipeline.pending(space.id(), gardenId, currentUser.login())
                : pipeline.pendingInSpace(space.id(), currentUser.login())).size();
        level.add(new Action(VaadinIcon.COMMENTS_O, "nav.questions", AskView.class, here, waiting, true));
        // The proposals belong to the space, so Knowledge is offered inside and outside a garden.
        level.add(new Action(VaadinIcon.BOOK, "nav.knowledge", KnowledgeView.class, here, 0, false));
        if (gardenId <= 0) {
            level.add(new Action(VaadinIcon.USERS, "nav.members", MembersView.class, spaceOnly, 0, false));
        }
        return level;
    }

    /** The actions in the top bar (#132): the icon and a count, the word beside them on a wide screen. */
    private void showActions(List<Action> level, Class<?> target) {
        actions.removeAll();
        level.forEach(action -> actions.add(item(action, target, "rootology-level-action")));
    }

    /**
     * The bar of a phone (#88, #132): the level's home first (the garden's, or the gardens), then the same
     * actions as the top bar. Above 640 px it is hidden.
     */
    private void bottomMenu(SpaceSummary space, long gardenId, List<Action> level, Class<?> target) {
        bottomBar.removeAll();
        Action home = gardenId > 0
                ? new Action(VaadinIcon.DASHBOARD, "nav.gardenhome", GardenHomeView.class, Map.of(
                        Routed.SPACE, String.valueOf(space.id()), Routed.GARDEN, String.valueOf(gardenId)), 0, false)
                : new Action(VaadinIcon.GRID_SMALL, "nav.gardens", GardensView.class,
                        Map.of(Routed.SPACE, String.valueOf(space.id())), 0, false);
        bottomBar.add(item(home, target, "rootology-bottom-nav-item"));
        level.forEach(action -> bottomBar.add(item(action, target, "rootology-bottom-nav-item")));
    }

    /**
     * One section as a link: the icon, the word and the count; the whole sentence as its title and aria label.
     * The page being shown says so. A count of waiting questions takes the waiting colour.
     */
    private RouterLink item(Action action, Class<?> target, String className) {
        RouterLink link = new RouterLink();
        link.setRoute(action.view(), new RouteParameters(action.parameters()));
        Span word = new Span(getTranslation(action.key()));
        word.addClassName("rootology-action-word");
        link.add(action.icon().create(), word);
        link.addClassName(className);
        String described = action.count() > 0
                ? getTranslation(action.waiting() ? "nav.waiting.count" : "nav.count", action.count(),
                        getTranslation(action.key()))
                : getTranslation(action.key());
        link.getElement().setAttribute("aria-label", described);
        link.getElement().setAttribute("title", described);
        if (action.view() == target) {
            link.addClassName("rootology-action-current");
            link.getElement().setAttribute("aria-current", "page");
        }
        if (action.count() > 0) {
            Span count = new Span(String.valueOf(action.count()));
            count.addClassName("rootology-action-count");
            if (action.waiting()) {
                count.addClassName("rootology-action-count-waiting");
            }
            count.getElement().setAttribute("aria-hidden", "true");
            link.add(count);
        }
        return link;
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
        // The name other people see (#106), asked once: at the first sign-in after it, and for every new person.
        if (currentUser.asksForName()) {
            new NamePrompt(currentUser, this::showName).open();
        }
    }

    /** The person's own name in the menu (#106), again after they choose one. */
    void showName() {
        String name = currentUser.name();
        avatar.setName(name);
        who.setText(name);
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
