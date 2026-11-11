package app.rootology.ui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The actions of a row (open, edit, delete) as icon buttons, the same everywhere in the app (#66). The word
 * is the tooltip and what a screen reader says, so an icon never stands alone. A delete always asks first.
 */
final class RowActions {

    private RowActions() {
    }

    static Button open(String label, ComponentEventListener<ClickEvent<Button>> listener) {
        return icon(VaadinIcon.ARROW_RIGHT, label, listener);
    }

    static Button edit(String label, ComponentEventListener<ClickEvent<Button>> listener) {
        return icon(VaadinIcon.EDIT, label, listener);
    }

    /** Something that goes ahead (an approval, an invitation request): the check mark. */
    static Button approve(String label, ComponentEventListener<ClickEvent<Button>> listener) {
        return icon(VaadinIcon.CHECK, label, listener);
    }

    /** Something that is turned down: a cross, in the error colour, like a delete. */
    static Button reject(String label, ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = icon(VaadinIcon.CLOSE, label, listener);
        button.addThemeVariants(ButtonVariant.LUMO_ERROR);
        return button;
    }

    static Button delete(String label, ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = icon(VaadinIcon.TRASH, label, listener);
        button.addThemeVariants(ButtonVariant.LUMO_ERROR);
        return button;
    }

    private static Button icon(VaadinIcon icon, String label, ComponentEventListener<ClickEvent<Button>> listener) {
        Button button = new Button(icon.create(), listener);
        button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        button.setAriaLabel(label);
        button.setTooltipText(label);
        return button;
    }

    /** The question before a delete: what goes, that it cannot be undone, and a red button that says it. */
    static Dialog confirmDelete(String title, String text, String note, String confirmLabel, String cancelLabel,
                                Runnable delete) {
        Dialog dialog = new Dialog(title);
        Button confirm = new Button(confirmLabel, e -> {
            dialog.close();
            delete.run();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        dialog.add(new Paragraph(text));
        if (note != null) {
            dialog.add(new Paragraph(note));
        }
        dialog.getFooter().add(new Button(cancelLabel, e -> dialog.close()), confirm);
        dialog.open();
        return dialog;
    }
}
