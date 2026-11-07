package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * The bar of the public pages (#50): the welcome page shows no link back to itself for a visitor, while the
 * other public pages keep it.
 */
class PublicLayoutTest {

    private static Anchor backLink(PublicLayout layout) {
        return layout.getChildren().filter(Header.class::isInstance).map(Header.class::cast)
                .flatMap(Header::getChildren).filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .filter(a -> a.getElement().getClassList().contains("rootology-public-back"))
                .findFirst().orElseThrow();
    }

    private static PublicLayout navigatedTo(Class<? extends com.vaadin.flow.component.Component> target) {
        PublicLayout layout = new PublicLayout();
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        doReturn(target).when(event).getNavigationTarget();
        layout.beforeEnter(event);
        return layout;
    }

    @Test
    void theWelcomePageHasNoBackLink() {
        assertThat(backLink(navigatedTo(WelcomeView.class)).isVisible()).isFalse();
    }

    @Test
    void thePrivacyAndSourcesPagesKeepTheBackLink() {
        assertThat(backLink(navigatedTo(PrivacyView.class)).isVisible()).isTrue();
        assertThat(backLink(navigatedTo(SourcesView.class)).isVisible()).isTrue();
    }
}
