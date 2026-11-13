package app.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.notifications.NotificationService;
import app.rootology.notifications.NotificationView;
import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The signed-in person’s notifications (#26) and a way to the waiting question; their preferences live in
 * Notification settings (#88). Notifications are never filtered (#56): one of another space or garden asks
 * first, naming both, and then goes there. On a computer they are a table; below 640 px one card each (#93).
 */
@Route(value = "notifications", layout = MainLayout.class)
@PermitAll
public class NotificationsView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** The context the person came from, as a hint for the confirmation before opening one (#56). */
    static final String SPACE = "space";
    static final String GARDEN = "garden";

    private final NotificationService notifications;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final Grid<NotificationView> grid = new Grid<>();
    /** The same rows as cards on a phone (#93), where a table's "What" column has no room. */
    private final Div cards = new Div();
    /** What the page shows when nothing has happened yet (#81). */
    private final EmptyState empty = new EmptyState(getTranslation("empty.notifications"))
            .drawing(EmptyState.Drawing.BELL);
    /** The space and garden the bell was used from; only a hint, never used to read data (#56). */
    private long contextSpace;
    private long contextGarden;

    public NotificationsView(NotificationService notifications, GardenService gardens, CurrentUser currentUser) {
        this.notifications = notifications;
        this.gardens = gardens;
        this.currentUser = currentUser;

        grid.addComponentColumn(n -> DateText.dateTimeSpan(n.createdAt(), this))
                .setHeader(getTranslation("notifications.when")).setAutoWidth(true);
        grid.addColumn(n -> (n.unread() ? "● " : "") + what(n))
                .setHeader(getTranslation("notifications.what")).setAutoWidth(true);
        grid.addColumn(this::where).setHeader(getTranslation("notifications.where")).setAutoWidth(true);
        grid.addColumn(this::details).setHeader(getTranslation("notifications.details")).setFlexGrow(3);
        grid.addColumn(this::status)
                .setHeader(getTranslation("notifications.status")).setAutoWidth(true);
        grid.addComponentColumn(this::openButton).setAutoWidth(true);
        grid.addClassName("rootology-notification-grid");
        grid.setAllRowsVisible(true);
        cards.addClassName("rootology-notification-cards");
        Button allRead = new Button(getTranslation("notifications.allread"), e -> {
            notifications.markRead(currentUser.login(), null);
            refresh();
        });

        // The preferences are a page of their own now (#88): this one only says what happened.
        Button preferences = new Button(getTranslation("notifications.preferences"),
                e -> UI.getCurrent().navigate(NotificationSettingsView.class));

        empty.setVisible(false);
        allRead.addThemeName("tertiary");
        preferences.addThemeName("tertiary");
        add(new PageHeader(getTranslation("notifications.title"), allRead, preferences), grid, cards, empty);
        refresh();
    }

    /**
     * The context the person came from, from the query string of the bell and the person menu (#56). A
     * space the person is not in, or a garden that is not there, is ignored: it decides nothing but the
     * confirmation.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        long spaceId = number(event, SPACE);
        long gardenId = number(event, GARDEN);
        if (spaceId <= 0 || !currentUser.memberOf(spaceId)) {
            return;
        }
        contextSpace = spaceId;
        contextGarden = gardenId > 0 && gardens.garden(spaceId, currentUser.login(), gardenId).isPresent() ? gardenId : 0;
    }

    private static long number(BeforeEnterEvent event, String name) {
        return event.getLocation().getQueryParameters().getSingleParameter(name).map(value -> {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                return -1L;
            }
        }).orElse(-1L);
    }

    /**
     * To the waiting question, to the answer in the history (#51), or to the garden for a weather alert:
     * switching to its space if needed.
     */
    private void open(NotificationView n) {
        notifications.markRead(currentUser.login(), n.id());
        if (n.accessRequest()) {
            // #65: it is about the app, not about a space, and only an admin can decide.
            getUI().ifPresent(ui -> ui.navigate(AdminView.class));
            refresh();
            return;
        }
        if (!currentUser.memberOf(n.spaceId())) {
            Toasts.info(getTranslation("notifications.gone"));
            refresh();
            return;
        }
        if (asksBeforeOpening(n)) {
            Dialog dialog = new Dialog(getTranslation("notifications.open.other"));
            dialog.add(new Paragraph(getTranslation("notifications.open.other.text", where(n))));
            dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()),
                    new Button(getTranslation("notifications.open"), e -> {
                        dialog.close();
                        go(n);
                    }));
            dialog.open();
            return;
        }
        go(n);
    }

    /**
     * Whether opening it changes the context the person came from (#56): another space or garden, or no
     * context at all. Same space and garden: it opens directly.
     */
    boolean asksBeforeOpening(NotificationView n) {
        return !n.accessRequest() && (contextSpace != n.spaceId() || contextGarden != n.gardenId());
    }

    /**
     * The page a notification opens, with its context (#56): the waiting question, the answer in the
     * history, or the garden of a weather alert. An alert without a garden (stored before #56, or of a
     * deleted one) belongs to the space's gardens, so it opens the overview, as its email link does.
     */
    record Target(Class<? extends Component> view, com.vaadin.flow.router.RouteParameters route,
                  QueryParameters query) {

        Target(Class<? extends Component> view, com.vaadin.flow.router.RouteParameters route) {
            this(view, route, QueryParameters.empty());
        }
    }

    Target target(NotificationView n) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Routed.SPACE, String.valueOf(n.spaceId()));
        if (n.gardenId() > 0) {
            parameters.put(Routed.GARDEN, String.valueOf(n.gardenId()));
        }
        com.vaadin.flow.router.RouteParameters route = new com.vaadin.flow.router.RouteParameters(parameters);
        if (n.accessRequest()) {
            return new Target(AdminView.class, new com.vaadin.flow.router.RouteParameters(Map.of()));
        }
        if (n.weather()) {
            return new Target(n.gardenId() > 0 ? GardenHomeView.class : GardensView.class, route);
        }
        if (n.answered()) {
            // The answer opens on Ask, beside the history (#100).
            return new Target(AskView.class, route, n.processId() == null ? QueryParameters.empty()
                    : QueryParameters.simple(Map.of(AskView.RUN, n.processId())));
        }
        return new Target(EscalationView.class, route);
    }

    private void go(NotificationView n) {
        Target target = target(n);
        getUI().ifPresent(ui -> ui.navigate(target.view(), target.route(), target.query()));
    }

    /** Its space, and its garden when it has one: a garden alone is ambiguous across spaces (#56). */
    private String where(NotificationView n) {
        if (n.accessRequest()) {
            return getTranslation("notifications.where.admin");
        }
        String space = currentUser.spaces().stream().filter(s -> s.id() == n.spaceId()).map(s -> s.name())
                .findFirst().orElse("");
        if (n.gardenId() <= 0) {
            return space;
        }
        String garden = gardens.garden(n.spaceId(), currentUser.login(), n.gardenId()).map(GardenSummary::name)
                .orElse("");
        return space + " · " + garden;
    }

    /** What happened, in a word or in the alert's own title: the table's "What" and the card's main line. */
    String what(NotificationView n) {
        return n.weather() ? n.title()
                : n.accessRequest() ? getTranslation("notifications.accessrequest")
                : n.answered() ? getTranslation("notifications.answered") : getTranslation("notifications.waiting");
    }

    /** What the notification says beyond its title, in the page's language (#94). */
    String details(NotificationView n) {
        return NotificationText.details(n, this::getTranslation);
    }

    /** Waiting for a person or answered by one; the other kinds carry their news in the title (#93). */
    String status(NotificationView n) {
        if (n.weather() || n.answered() || n.accessRequest()) {
            return "";
        }
        return n.resolvedAt() == null ? getTranslation("notifications.status.waiting")
                : getTranslation("notifications.status.answered", n.resolvedBy());
    }

    private Button openButton(NotificationView n) {
        Button open = RowActions.open(getTranslation("notifications.open"), e -> open(n));
        open.setVisible(n.resolvedAt() == null);
        return open;
    }

    /**
     * One notification as a card for a phone (#93): unread dot, when, what happened on as many lines as it
     * needs, the details, its space and garden, and the open action. Opening it behaves as the row's.
     */
    private Div card(NotificationView n) {
        Div card = new Div();
        card.addClassName("rootology-notification-card");
        Div head = new Div();
        head.addClassName("rootology-notification-card-head");
        if (n.unread()) {
            Span unread = new Span("●");
            unread.addClassName("rootology-notification-card-unread");
            unread.getElement().setAttribute("role", "img");
            unread.getElement().setAttribute("aria-label", getTranslation("notifications.unread"));
            unread.setTitle(getTranslation("notifications.unread"));
            head.add(unread);
        }
        head.add(DateText.dateTimeSpan(n.createdAt(), this));

        Span happened = new Span(what(n));
        happened.addClassName("rootology-notification-card-what");

        card.add(head, happened);
        // The details in the reader's language (#94), on the card as in the table's column: an invitation
        // request already says everything in its title.
        String details = n.accessRequest() ? "" : details(n);
        if (!details.isBlank()) {
            Span line = new Span(details);
            line.addClassNames(FontSize.SMALL, "rootology-notification-card-details");
            card.add(line);
        }
        String status = status(n);
        if (!status.isEmpty()) {
            Span line = new Span(status);
            line.addClassNames(FontSize.XSMALL, TextColor.SECONDARY, "rootology-notification-card-status");
            card.add(line);
        }
        Span where = new Span(where(n));
        where.addClassNames(FontSize.XSMALL, TextColor.SECONDARY, "rootology-notification-card-where");
        card.add(where);

        Button open = openButton(n);
        open.addClassName("rootology-notification-card-open");
        card.add(open);
        return card;
    }

    private void refresh() {
        List<NotificationView> rows = notifications.of(currentUser.login(), 50);
        grid.setItems(rows);
        grid.setVisible(!rows.isEmpty());
        cards.removeAll();
        rows.forEach(n -> cards.add(card(n)));
        cards.setVisible(!rows.isEmpty());
        empty.setVisible(rows.isEmpty());
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notifications.title") + " · Rootology";
    }
}
