package app.rootology.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import app.rootology.notifications.NotificationPreferences;
import app.rootology.notifications.NotificationService;
import app.rootology.notifications.TelegramLinks;
import jakarta.annotation.security.PermitAll;

import java.util.Optional;

/**
 * The notification preferences (#88), one page of their own instead of below the list: the email address and
 * what it carries, the quiet hours, and the Telegram link (#38). The list of what happened stays in
 * Notifications, which leads here.
 */
@Route(value = NotificationSettingsView.ROUTE, layout = MainLayout.class)
@PermitAll
public class NotificationSettingsView extends VerticalLayout implements HasDynamicTitle {

    static final String ROUTE = "account/notifications";

    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final TelegramLinks telegram;
    /** The Telegram part of the preferences, redrawn when its state changes (#38). */
    private final VerticalLayout telegramBox = new VerticalLayout();

    public NotificationSettingsView(NotificationService notifications, CurrentUser currentUser,
                                    TelegramLinks telegram) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.telegram = telegram;

        Button back = new Button(getTranslation("notifications.back"), e -> UI.getCurrent().navigate(NotificationsView.class));
        back.addThemeName("tertiary");
        add(new PageHeader(getTranslation("notifications.preferences"), back), preferences(), telegramBox);
        refreshTelegram();
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
                Toasts.done(getTranslation("common.saved"));
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        });
        HorizontalLayout quiet = new HorizontalLayout(quietFrom, quietTo);
        return new VerticalLayout(new Paragraph(getTranslation("notifications.preferences.intro")),
                email, escalations, weather,
                new Paragraph(getTranslation("notifications.telegram.preferences")),
                telegramEscalations, telegramWeather, quiet, save);
    }

    /**
     * The Telegram link (#38): absent without a bot token, a button and a one-time code without a link, the
     * date once linked, a warning and "link again" when Telegram stopped delivering. Nothing here touches
     * Telegram except the code request; everything else is read from the database.
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
                    DateText.dateTime(working.linkedAt(), this))));
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
            Toasts.done(getTranslation("notifications.telegram.unlinked"));
            refreshTelegram();
        });
    }

    @Override
    public String getPageTitle() {
        return getTranslation("notifications.preferences") + " · Rootology";
    }
}
