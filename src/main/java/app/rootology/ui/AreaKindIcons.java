package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.SvgIcon;
import app.rootology.garden.PlotKind;

/**
 * The drawing of each kind of area (#85), the same everywhere it is shown: one icon per kind, never a
 * different one for the same kind. The word stays the tooltip and what a screen reader says. Drawn for
 * Rootology in the hand-drawn line style of #87 (`icons/areas/`), in the text's colour.
 */
final class AreaKindIcons {

    /** The drawing's path under the static resources. */
    static String of(PlotKind kind) {
        return "icons/areas/" + switch (kind) {
            case GROUND -> "ground";
            case RAISED_BED -> "raised-bed";
            case PLANTER -> "planter";
            case POT -> "pot";
            case GREENHOUSE -> "greenhouse";
        } + ".svg";
    }

    /** The icon that says the kind on its own: the word is its tooltip and its aria label (#85). */
    static SvgIcon labelled(PlotKind kind, Component texts) {
        SvgIcon icon = new SvgIcon(of(kind));
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
