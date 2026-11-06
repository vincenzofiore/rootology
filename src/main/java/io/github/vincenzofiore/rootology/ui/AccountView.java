package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.privacy.AccountDeletion;
import io.github.vincenzofiore.rootology.privacy.DataExport;
import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.LinkedIdentity;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * My account (#40): the sign-in accounts of the person and how to link or unlink one (#28), downloading one's
 * data as JSON and deleting the account, as the privacy page promises. Deleting asks for the login to be typed,
 * and is refused, with the reason, while the person is the only owner of a space others use or the last admin.
 */
@Route(value = AccountView.ROUTE, layout = MainLayout.class)
@PermitAll
public class AccountView extends Div implements HasDynamicTitle, BeforeEnterObserver {

    static final String ROUTE = "account";

    /** The query parameter carrying how a link ended (#28): a fixed code, never an address or a login. */
    static final String LINK_PARAMETER = "link";

    private final String googleClientId;

    public AccountView(CurrentUser currentUser, DataExport export, AccountDeletion deletion,
                       AuthenticationContext authentication, IdentityService identities,
                       @Value("${spring.security.oauth2.client.registration.google.client-id:not-set}")
                       String googleClientId) {
        this.googleClientId = googleClientId;
        addClassName("rootology-prose");
        String login = currentUser.login();
        add(new H2(getTranslation("account.title")), new Paragraph(getTranslation("account.signedin", login)));

        add(new H3(getTranslation("account.signin.title")), new Paragraph(getTranslation("account.signin.text")));
        add(signInAccounts(login, identities));

        add(new H3(getTranslation("account.export.title")), new Paragraph(getTranslation("account.export.text")));
        String fileName = "rootology-" + login + "-" + LocalDate.now() + ".json";
        DownloadHandler handler = DownloadHandler.fromInputStream(event -> {
            byte[] json = export.export(login);
            return new DownloadResponse(new ByteArrayInputStream(json), fileName, "application/json", json.length);
        });
        Anchor download = new Anchor();
        // The handler is registered with the UI, so only once the view is attached to one.
        download.addAttachListener(e -> download.setHref(handler, AttachmentType.DOWNLOAD));
        Button downloadButton = new Button(getTranslation("account.export.button"), VaadinIcon.DOWNLOAD.create());
        download.add(downloadButton);
        add(download);

        add(new H3(getTranslation("account.delete.title")), new Paragraph(getTranslation("account.delete.text")));
        AccountDeletion.Preview preview = deletion.preview(login);
        if (!preview.spacesDeleted().isEmpty()) {
            add(new Paragraph(getTranslation("account.delete.spaces")), names(preview.spacesDeleted()));
        }
        Button delete = new Button(getTranslation("account.delete.button"), VaadinIcon.TRASH.create());
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        if (!preview.blockingSpaces().isEmpty()) {
            add(new Paragraph(getTranslation("account.delete.blocked.owner")), names(preview.blockingSpaces()));
        }
        if (preview.lastAdmin()) {
            add(new Paragraph(getTranslation("account.delete.blocked.admin")));
        }
        delete.setEnabled(preview.allowed());
        delete.addClickListener(e -> confirm(login, deletion, authentication));
        add(delete);
    }

    /**
     * The person's accounts, and the way to link one of the providers they are missing (#28). Unlinking is
     * offered only with two accounts, since nobody may be left without a way in.
     */
    private Component signInAccounts(String login, IdentityService identities) {
        VerticalLayout section = new VerticalLayout();
        section.addClassName("rootology-signin-accounts");
        section.setPadding(false);
        List<LinkedIdentity> linked = identities.linkedTo(login);
        for (LinkedIdentity identity : linked) {
            section.add(identityRow(login, identity, linked.size() > 1, identities));
        }
        for (IdentityProvider provider : IdentityProvider.values()) {
            boolean linkedAlready = linked.stream().anyMatch(identity -> identity.provider() == provider);
            if (!linkedAlready && (provider != IdentityProvider.GOOGLE
                    || SecurityConfiguration.googleConfigured(googleClientId))) {
                section.add(linkButton(login, provider));
            }
        }
        return section;
    }

    private HorizontalLayout identityRow(String login, LinkedIdentity identity, boolean canUnlink,
                                         IdentityService identities) {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("rootology-signin-account");
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        Span provider = new Span(getTranslation("provider." + identity.provider().id()));
        provider.addClassName("rootology-signin-provider");
        Span email = new Span(identity.email() == null
                ? getTranslation("account.signin.noemail") : identity.email());
        Span linkedOn = new Span(getTranslation("account.signin.linked_on", date(identity)));
        row.add(provider, email, linkedOn);
        if (canUnlink) {
            Button unlink = new Button(getTranslation("account.signin.unlink"));
            unlink.setId("unlink-" + identity.provider().id());
            unlink.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            unlink.addClickListener(e -> {
                try {
                    identities.unlink(login, identity.provider());
                    getUI().ifPresent(ui -> ui.getPage().reload());
                } catch (SpaceAccessException refused) {
                    Notification.show(getTranslation("account.signin.unlink.last"), 6000,
                            Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            row.add(unlink);
        }
        return row;
    }

    /**
     * Starts linking a provider: the marker goes in the session first, so the returning sign-in knows who asked
     * and links instead of signing in.
     */
    private Button linkButton(String login, IdentityProvider provider) {
        Button link = new Button(getTranslation("account.signin.link." + provider.id()));
        link.setId("link-" + provider.id());
        link.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        link.addClickListener(e -> {
            PendingLink.start(login, provider);
            getUI().ifPresent(ui -> ui.getPage().setLocation(SecurityConfiguration.loginPage(provider)));
        });
        return link;
    }

    private String date(LinkedIdentity identity) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(getLocale() == null
                        ? Locale.ITALIAN : getLocale())
                .format(identity.linkedAt().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    /** How a link ended, told once the person is back in My account (#28). */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String code = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault(LINK_PARAMETER, List.of()).stream().findFirst().orElse(null);
        PendingLink.Result result = PendingLink.Result.byCode(code);
        if (result == null) {
            return;
        }
        Notification notification = Notification.show(getTranslation("account.signin." + result.code()),
                6000, Notification.Position.MIDDLE);
        notification.addThemeVariants(result == PendingLink.Result.LINKED
                ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_ERROR);
    }

    private void confirm(String login, AccountDeletion deletion, AuthenticationContext authentication) {
        Dialog dialog = new Dialog(getTranslation("account.delete.confirm.title"));
        TextField typed = new TextField(getTranslation("account.delete.confirm.label", login));
        typed.setWidthFull();
        Button confirm = new Button(getTranslation("account.delete.confirm.button"), e -> {
            try {
                deletion.delete(login);
                dialog.close();
                authentication.logout();
            } catch (IllegalStateException refused) {
                Notification.show(getTranslation("account.delete.refused"), 6000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        confirm.setEnabled(false);
        typed.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.EAGER);
        typed.addValueChangeListener(e -> confirm.setEnabled(login.equalsIgnoreCase(e.getValue().trim())));
        dialog.add(new Paragraph(getTranslation("account.delete.confirm.text")), typed);
        dialog.getFooter().add(new Button(getTranslation("account.delete.confirm.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    private static UnorderedList names(List<String> names) {
        UnorderedList list = new UnorderedList();
        names.forEach(n -> list.add(new ListItem(n)));
        return list;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("account.title") + " · Rootology";
    }
}
