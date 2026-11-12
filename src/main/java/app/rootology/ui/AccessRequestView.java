package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import app.rootology.access.AccessRequestService;
import app.rootology.access.AccessState;
import jakarta.annotation.security.PermitAll;

import java.time.Instant;

/**
 * The page of someone who signed in but is not admitted (#65): ask for an invitation with a short motivation,
 * see where the request stands, withdraw it, or sign out after an approval to come back in. It also says what
 * is kept about them, and is their export: they have nothing else in the app.
 */
@Route(value = AccessRequestView.ROUTE, layout = PublicLayout.class)
@PermitAll
public class AccessRequestView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    static final String ROUTE = "request-access";

    private final AccessRequestService requests;
    private final AuthenticationContext authentication;
    /** The part that depends on the state of the request, redrawn after every change. */
    private final VerticalLayout state = new VerticalLayout();

    public AccessRequestView(AccessRequestService requests, AuthenticationContext authentication) {
        this.requests = requests;
        this.authentication = authentication;
        addClassName("rootology-prose");
        add(new H2(getTranslation("access.title")), state);
        refresh();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (PendingAccess.current().isEmpty()) {
            event.rerouteTo("");
        }
    }

    private void refresh() {
        state.removeAll();
        PendingAccess.current().ifPresent(identity -> {
            AccessState request = requests.state(identity.provider(), identity.subject());
            String who = getTranslation("provider." + identity.provider().id());
            if (request.pending()) {
                state.add(new Paragraph(getTranslation("access.pending", DateText.dateTime(request.createdAt(), this))),
                        new Paragraph(getTranslation("access.pending.text")), withdraw(identity));
            } else if (request.approved()) {
                state.add(new Paragraph(getTranslation("access.approved")), signOut());
            } else if (request.rejected() && request.retryAfter().isAfter(Instant.now())) {
                state.add(new Paragraph(getTranslation("access.rejected", DateText.dateTime(request.decidedAt(), this))),
                        new Paragraph(getTranslation("access.rejected.retry",
                                DateText.date(request.retryAfter().atZone(DateText.ITALY).toLocalDate(), this))));
            } else if (request.capReached()) {
                state.add(new Paragraph(getTranslation("access.toomany")));
            } else {
                state.add(form(identity));
            }
            String email = identity.emails().stream().findFirst().orElse(getTranslation("access.keep.noemail"));
            state.add(new H3(getTranslation("access.keep.title")),
                    new Paragraph(getTranslation("access.keep.text", who, identity.login(), email)));
            Anchor privacy = new Anchor("/" + PrivacyView.ROUTE, getTranslation("access.keep.privacy"));
            privacy.setRouterIgnore(true);
            state.add(privacy);
        });
    }

    /** The motivation: required, at most 500 characters, shown as plain text to the admins. */
    private VerticalLayout form(PendingAccess.Identity identity) {
        TextArea motivation = new TextArea(getTranslation("access.motivation"));
        motivation.setMaxLength(AccessRequestService.MAX_MOTIVATION);
        motivation.setWidthFull();
        motivation.setPlaceholder(getTranslation("access.motivation.placeholder"));
        Button send = new Button(getTranslation("access.send"), e -> {
            if (motivation.getValue() == null || motivation.getValue().isBlank()) {
                Notification.show(getTranslation("access.motivation.required"));
                return;
            }
            try {
                requests.request(identity.provider(), identity.subject(), identity.login(),
                        identity.emails().stream().findFirst().orElse(null), motivation.getValue());
            } catch (RuntimeException ex) {
                Notification.show(getTranslation("access.notsent"));
            }
            refresh();
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return new VerticalLayout(new Paragraph(getTranslation("access.intro")), motivation, send);
    }

    private Button withdraw(PendingAccess.Identity identity) {
        return new Button(getTranslation("access.withdraw"), e -> {
            Dialog dialog = new Dialog(getTranslation("access.withdraw.title"));
            dialog.add(new Paragraph(getTranslation("access.withdraw.text")));
            Button confirm = new Button(getTranslation("access.withdraw.confirm"), click -> {
                dialog.close();
                requests.withdraw(identity.provider(), identity.subject());
                refresh();
            });
            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
            dialog.getFooter().add(new Button(getTranslation("common.cancel"), click -> dialog.close()), confirm);
            dialog.open();
        });
    }

    private Button signOut() {
        return new Button(getTranslation("nav.logout"), e -> authentication.logout());
    }

    @Override
    public String getPageTitle() {
        return getTranslation("access.title") + " · Rootology";
    }
}
