package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RowActionsTest {

    @Test
    void againUsesTheUpArrowAndShowsTheWordOnWideScreens() {
        Button again = RowActions.again("Riusa", e -> { });

        assertThat(again.getAriaLabel()).contains("Riusa");
        assertThat(again.getTooltip().getText()).isEqualTo("Riusa");

        Component icon = again.getIcon();
        assertThat(icon.getElement().getAttribute("icon")).isEqualTo("vaadin:arrow-up");

        Component suffix = again.getSuffixComponent();
        assertThat(suffix).isInstanceOf(Span.class);
        assertThat(suffix.getElement().getText()).isEqualTo("Riusa");
        assertThat(suffix.getElement().getClassList()).contains("rootology-wide-only");
    }
}
