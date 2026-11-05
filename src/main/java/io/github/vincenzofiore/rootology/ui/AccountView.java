package io.github.vincenzofiore.rootology.ui;

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
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.privacy.AccountDeletion;
import io.github.vincenzofiore.rootology.privacy.DataExport;
import jakarta.annotation.security.PermitAll;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

/**
 * My account (#40): download one's data as JSON and delete the account, as the privacy page promises.
 * Deleting asks for the login to be typed, and is refused, with the reason, while the person is the only owner
 * of a space others use or the last admin.
 */
@Route(value = AccountView.ROUTE, layout = MainLayout.class)
@PermitAll
public class AccountView extends Div implements HasDynamicTitle {

    static final String ROUTE = "account";

    public AccountView(CurrentUser currentUser, DataExport export, AccountDeletion deletion,
                       AuthenticationContext authentication) {
        addClassName("rootology-prose");
        String login = currentUser.login();
        add(new H2(getTranslation("account.title")), new Paragraph(getTranslation("account.signedin", login)));

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

    private static UnorderedList names(java.util.List<String> names) {
        UnorderedList list = new UnorderedList();
        names.forEach(n -> list.add(new ListItem(n)));
        return list;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("account.title") + " · Rootology";
    }
}
