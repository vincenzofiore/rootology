package app.rootology.ui;

import com.vaadin.flow.component.textfield.TextField;
import app.rootology.spaces.DisplayNameTakenException;
import app.rootology.spaces.SpaceService;

/**
 * The field for the name other people see (#106), in My account and in the first-sign-in prompt: prefilled
 * with the chosen name, otherwise the login, and saved through {@link CurrentUser#chooseName}, which says
 * when someone else already shows it.
 */
class DisplayNameField extends TextField {

    private final CurrentUser currentUser;

    DisplayNameField(CurrentUser currentUser) {
        super();
        this.currentUser = currentUser;
        setLabel(getTranslation("name.label"));
        setHelperText(getTranslation("name.helper", SpaceService.MAX_DISPLAY_NAME));
        setMaxLength(SpaceService.MAX_DISPLAY_NAME);
        setRequired(true);
        setWidthFull();
        setValue(currentUser.chosenName().orElse(currentUser.login()));
        addValueChangeListener(e -> setInvalid(false));
    }

    /** Saves the name; false, with the reason under the field, when it cannot be used. */
    boolean save() {
        try {
            setValue(currentUser.chooseName(getValue()));
            return true;
        } catch (DisplayNameTakenException e) {
            setErrorMessage(getTranslation("name.taken"));
        } catch (IllegalArgumentException e) {
            setErrorMessage(getTranslation("name.length", SpaceService.MAX_DISPLAY_NAME));
        }
        setInvalid(true);
        return false;
    }
}
