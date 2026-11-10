package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.notifications.NotificationPreferences;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.notifications.NotificationView;
import io.github.vincenzofiore.rootology.notifications.TelegramLinks;
import jakarta.annotation.security.PermitAll;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.vaadin.flow.component.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The signed-in person's notifications (#26), a way to the waiting question, and their preferences. They are
 * never filtered (#56): one of another space or garden asks first, naming both, and then goes there.
 */
@Route(value = "notifications", layout = MainLayout.class)
@PermitAll
public class NotificationsView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    /** The context the person came from, as a hint for the confirmation before opening one (#56). */
    static final String SPACE = "space";
    static final String GARDEN = "garden";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Rome"));

    private final NotificationService notifications;
    private final GardenService gardens;
    private final CurrentUser currentUser;
    private final TelegramLinks telegram;
    /** The Telegram part of the preferences, redrawn when its state changes (#38). */
    private final VerticalLayout telegramBox = new VerticalLayout();
    private final Grid<NotificationView> grid = new Grid<>();
    /** The space and garden the bell was used from; only a hint, never used to read data (#56). */
    private long contextSpace;
    private long contextGarden;

    public NotificationsView(NotificationService notifications, GardenService gardens, CurrentUser currentUser,
                             TelegramLinks telegram) {
        this.notifications = notifications;
        this.gardens = gardens;
        this.currentUser = currentUser;
        this.telegram = telegram;

        grid.addColumn(n -> WHEN.format(n.createdAt())).setHeader(getTranslation("notifications.when")).setAutoWidth(true);
        grid.addColumn(n -> (n.unread() ? "● " : "") + (n.weather() ? n.title()
                : n.answered() ? getTranslation("notifications.answered") : getTranslation("notifications.waiting")))
                .setHeader(getTranslation("notifications.what")).setAutoWidth(true);
        grid.addColumn(this::where).setHeader(getTranslation("notifications.where")).setAutoWidth(true);
        grid.addColumn(NotificationView::body).setHeader(getTranslation("notifications.details")).setFlexGrow(3);
        grid.addColumn(n -> n.weather() || n.answered() ? ""
                        : n.resolvedAt() == null ? getTranslation("notifications.status.waiting")
                        : getTranslation("notifications.status.answered", n.resolvedBy()))
                .setHeader(getTranslation("notifications.status")).setAutoWidth(true);
        grid.addComponentColumn(n -> {
            Button open = new Button(getTranslation("notifications.open"), e -> open(n));
            open.setVisible(n.resolvedAt() == null);
            return open;
        }).setAutoWidth(true);
        grid.setAllRowsVisible(true);
        Button allRead = new Button(getTranslation("notifications.allread"), e -> {
            notifications.markRead(currentUser.login(), null);
            refresh();
        });

        add(new H3(getTranslation("notifications.title")), allRead, grid, preferences(), telegramBox);
        refreshTelegram();
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
        if (!currentUser.memberOf(n.spaceId())) {
            Notification.show(getTranslation("notifications.gone"));
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
        return contextSpace != n.spaceId() || contextGarden != n.gardenId();
    }

    /**
     * The page a notification opens, with its context (#56): the waiting question, the answer in the
     * history, or the garden of a weather alert. An alert without a garden (stored before #56, or of a
     * deleted one) belongs to the space's gardens, so it opens the overview, as its email link does.
     */
    record Target(Class<? extends Component> view, com.vaadin.flow.router.RouteParameters route) {
    }

    Target target(NotificationView n) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(Routed.SPACE, String.valueOf(n.spaceId()));
        if (n.gardenId() > 0) {
            parameters.put(Routed.GARDEN, String.valueOf(n.gardenId()));
        }
        com.vaadin.flow.router.RouteParameters route = new com.vaadin.flow.router.RouteParameters(parameters);
        if (n.weather()) {
            return new Target(n.gardenId() > 0 ? GardenHomeView.class : GardensView.class, route);
        }
        return new Target(n.answered() ? HistoryView.class : EscalationView.class, route);
    }

    private void go(NotificationView n) {
        Target target = target(n);
        getUI().ifPresent(ui -> ui.navigate(target.view(), target.route()));
    }

    /** Its space, and its garden when it has one: a garden alone is ambiguous across spaces (#56). */
    private String where(NotificationView n) {
        String space = currentUser.spaces().stream().filter(s -> s.id() == n.spaceId()).map(s -> s.name())
                .findFirst().orElse("");
        if (n.gardenId() <= 0) {
            return space;
        }
        String garden = gardens.garden(n.spaceId(), currentUser.login(), n.gardenId()).map(GardenSummary::name)
                .orElse("");
        return space + " · " + garden;
    }

    private VerticalLayout preferences() {
        NotificationPreferences p = notifications.preferences(currentUser.login());
        TextField email = new TextField(getTranslation("notifications.email"));
        email.setWidth("20rem");
        email.setValue(p.email() == null ? "" : p.email());
        Checkbox escalations = new Checkbox(getTranslation("notifications.email.escalations"), p.emailEscalations());
        Checkbox weather = new Checkbox(getTranslation("notifications.email.weather"), p.emailWeather());
        Checkbox telegramEscalations = new Checkbox(getTranslation("notifications.telegram.escalations"),
                p.telegramEscalations());
        Checkbox telegramWeather = new Checkbox(getTranslation("notifications.telegram.weather"), p.telegramWeather());
        TimePicker quietFrom = new TimePicker(getTranslation("notifications.quiet.from"), p.quietFrom());
        TimePicker quietTo = new TimePicker(getTranslation("notifications.quiet.to"), p.quietTo());
        Button save = new Button(getTranslation("common.save"), e -> {
            try {
                notifications.save(currentUser.login(), new NotificationPreferences(email.getValue(),
                        escalations.getValue(), weather.getValue(), telegramEscalations.getValue(),
                        telegramWeather.getValue(), quietFrom.getValue(), quietTo.getValue()));
                Notification.show(getTranslation("common.saved"));
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage());
            }
        });
        HorizontalLayout quiet = new HorizontalLayout(quietFrom, quietTo);
        return new VerticalLayout(new H4(getTranslation("notifications.preferences")),
                new Paragraph(getTranslation("notifications.preferences.intro")),
                email, escalations, weather,
                new Paragraph(getTranslation("notifications.telegram.preferences")),
                telegramEscalations, telegramWeather, quiet, save);
    }

    /**
     * The Telegram link (#38): absent without a bot token, a button and a one-time code without a link, the
     * date once linked, a warning and "collega di nuovo" when Telegram stopped delivering. Nothing here
     * touches Telegram except the code request; everything else is read from the database.
     */
    private void refreshTelegram() {
        telegramBox.removeAll();
        telegramBox.setVisible(telegram.configured());
        if (!telegram.configured()) {
            return;
        }
        telegramBox.add(new H4(getTranslation("notifications.telegram")),
                new Paragraph(getTranslation("notifications.telegram.intro")));
        Optional<TelegramLinks.Link> link = telegram.of(currentUser.login());
        link.filter(l -> l.working()).ifPresentOrElse(working -> {
            telegramBox.add(new Paragraph(getTranslation("notifications.telegram.linked",
                    WHEN.format(working.linkedAt()))));
            telegramBox.add(unlink());
        }, () -> {
            if (link.isPresent()) {
                telegramBox.add(new Paragraph(getTranslation("notifications.telegram.failed")));
            }
            telegramBox.add(new Button(getTranslation("notifications.telegram.connect"), e -> {
                refreshTelegram();
                showCode();
            }));
            if (link.isPresent()) {
                telegramBox.add(unlink());
            }
        });
    }

    /** The one-time code: a link that opens the bot with it, and the code itself for a desktop browser. */
    private void showCode() {
        TelegramLinks.Start start = telegram.start(currentUser.login());
        telegramBox.add(new Paragraph(getTranslation("notifications.telegram.manual", start.code())));
        if (start.url() != null) {
            Anchor open = new Anchor(start.url(), getTranslation("notifications.telegram.open"));
            open.setTarget("_blank");
            telegramBox.add(open);
        }
    }

    private Button unlink() {
        return new Button(getTranslation("notifications.telegram.unlink"), e -> {
            telegram.unlink(currentUser.login());
            Notification.show(getTranslation("notifications.telegram.unlinked"));
            refreshTelegram();
        });
    }

    private void refresh() {
        grid.setItems(notifications.of(currentUser.login(), 50));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notifications.title") + " · Rootology";
    }
}
