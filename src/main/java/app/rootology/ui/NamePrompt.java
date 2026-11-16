package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;

/**
 * Asked once, at the first sign-in after #106 and for every new person: the name other people see,
 * prefilled with the login. "Later" and closing keep the login; the name is always in My account.
 */
class NamePrompt extends Dialog {

    NamePrompt(CurrentUser currentUser, Runnable named) {
        setHeaderTitle(getTranslation("name.prompt.title"));
        addClassName("rootology-name-prompt");
        DisplayNameField field = new DisplayNameField(currentUser);
        add(new Paragraph(getTranslation("name.prompt.text")), field);
        Button save = new Button(getTranslation("name.save"), e -> {
            if (field.save()) {
                close();
                Toasts.done(getTranslation("name.saved", field.getValue()));
                named.run();
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        Button later = new Button(getTranslation("name.prompt.later"), e -> close());
        later.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        getFooter().add(later, save);
        // Whatever closes it, the person was asked: it does not come back at every page.
        addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                currentUser.skipName();
            }
        });
        field.focus();
    }
}
