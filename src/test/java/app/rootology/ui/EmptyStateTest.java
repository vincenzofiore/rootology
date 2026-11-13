package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared empty state (#81): a sentence alone, or a sentence and one primary action. */
class EmptyStateTest {

    @Test
    void aSentenceAloneHasNoButton() {
        EmptyState empty = new EmptyState("Nothing here yet.");

        assertThat(empty.getChildren().filter(Button.class::isInstance)).isEmpty();
    }

    @Test
    void theActionIsOnePrimaryButton() {
        AtomicBoolean clicked = new AtomicBoolean();
        EmptyState empty = new EmptyState("Nothing here yet.", "Add the first one", () -> clicked.set(true));

        Button button = empty.getChildren().filter(Button.class::isInstance).map(Button.class::cast)
                .findFirst().orElseThrow();
        assertThat(button.getText()).isEqualTo("Add the first one");
        assertThat(button.getThemeNames()).contains(ButtonVariant.LUMO_PRIMARY.getVariantName());
        button.click();
        assertThat(clicked).isTrue();
    }

    @Test
    void aGuestSeesTheSentenceWithoutTheAction() {
        EmptyState empty = new EmptyState("Nothing here yet.", "Add the first one", () -> { });

        empty.actionVisible(false);

        assertThat(empty.getChildren().filter(Button.class::isInstance).map(Button.class::cast)
                .findFirst().orElseThrow().isVisible()).isFalse();
    }

    @Test
    void theSproutUnlessThePageSaysOtherwise() {
        assertThat(new EmptyState("Nothing here yet.").drawingSrc()).isEqualTo("icons/empty/sprout.svg");
        assertThat(new EmptyState("No notifications.").drawing(EmptyState.Drawing.BELL).drawingSrc())
                .isEqualTo("icons/empty/bell.svg");
        for (EmptyState.Drawing drawing : EmptyState.Drawing.values()) {
            assertThat(getClass().getResource("/META-INF/resources/" + drawing.src())).as(drawing.name()).isNotNull();
        }
    }
}
