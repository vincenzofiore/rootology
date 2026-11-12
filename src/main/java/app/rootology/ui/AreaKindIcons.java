package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import app.rootology.garden.PlotKind;

/**
 * The drawing of each kind of area (#85), the same everywhere it is shown: one icon per kind, never a
 * different one for the same kind. The word stays the tooltip and what a screen reader says.
 */
final class AreaKindIcons {

    static VaadinIcon of(PlotKind kind) {
        return switch (kind) {
            case GROUND -> VaadinIcon.GRID;
            case RAISED_BED -> VaadinIcon.BUILDING;
            case PLANTER -> VaadinIcon.CUBE;
            case POT -> VaadinIcon.COFFEE;
            case GREENHOUSE -> VaadinIcon.HOME_O;
        };
    }

    /** The icon alone, hidden from a screen reader that already reads the word beside it. */
    static Icon drawing(PlotKind kind) {
        Icon icon = of(kind).create();
        icon.addClassName("rootology-kind-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        return icon;
    }

    /** The icon that says the kind on its own: the word is its tooltip and its aria label (#85). */
    static Icon labelled(PlotKind kind, Component texts) {
        Icon icon = of(kind).create();
        icon.addClassName("rootology-kind-icon");
        String word = word(kind, texts);
        icon.setTooltipText(word);
        icon.getElement().setAttribute("aria-label", word);
        icon.getElement().setAttribute("role", "img");
        return icon;
    }

    static String word(PlotKind kind, Component texts) {
        return texts.getTranslation("plotkind." + kind.name());
    }

    private AreaKindIcons() {
    }
}
