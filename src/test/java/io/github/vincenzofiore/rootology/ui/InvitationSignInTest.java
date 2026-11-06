package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** The ways in on the invitation page (#28): GitHub always, Google only once it is configured. */
class InvitationSignInTest {

    private static InvitationView view(String googleClientId) {
        return new InvitationView(mock(InvitationService.class), mock(AuthenticationContext.class), googleClientId);
    }

    @Test
    void googleIsOfferedOnlyWhenItIsConfigured() {
        assertThat(SecurityConfiguration.googleConfigured("not-set")).isFalse();
        assertThat(SecurityConfiguration.googleConfigured("")).isFalse();
        assertThat(SecurityConfiguration.googleConfigured("  ")).isFalse();
        assertThat(SecurityConfiguration.googleConfigured(null)).isFalse();
        assertThat(SecurityConfiguration.googleConfigured("1234.apps.googleusercontent.com")).isTrue();
    }

    @Test
    void withoutGoogleThePageOffersGitHubOnly() {
        assertThat(view("not-set").signInButtons().getChildren().map(Component::getElement).toList())
                .extracting(element -> element.getAttribute("href"))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE);
    }

    @Test
    void withGoogleThePageOffersBoth() {
        assertThat(view("1234.apps.googleusercontent.com").signInButtons().getChildren()
                .filter(Anchor.class::isInstance).map(Component::getElement).toList())
                .extracting(element -> element.getAttribute("href"))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE, SecurityConfiguration.GOOGLE_LOGIN_PAGE);
    }
}
