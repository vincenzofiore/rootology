package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public welcome page (#47): someone already signed in goes Home, a refused sign-in shows one generic
 * message, and the ways in follow the same rule as the other public pages.
 */
class WelcomeViewTest {

    private static WelcomeView view(AuthenticationContext authentication, String googleClientId) {
        return new WelcomeView(authentication, googleClientId);
    }

    @Test
    void signedInPeopleAreSentToTheirGardens() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view(authentication, "not-set").beforeEnter(event);

        verify(event).rerouteTo(StartView.class);
    }

    @Test
    void aRefusedSignInShowsTheGenericMessage() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location("welcome", QueryParameters.of("error", "denied")));

        WelcomeView welcome = view(authentication, "not-set");
        welcome.beforeEnter(event);

        Paragraph denied = welcome.getChildren().filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .filter(p -> p.getElement().getClassList().contains("rootology-welcome-denied"))
                .findFirst().orElseThrow();
        assertThat(denied.getText()).isEqualTo(welcome.getTranslation("welcome.denied"));
        assertThat(denied.isVisible()).isTrue();
    }

    @Test
    void withoutGoogleThePageOffersGitHubOnly() {
        assertThat(hrefs(view(mock(AuthenticationContext.class), "not-set")))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE);
    }

    @Test
    void withGoogleThePageOffersBoth() {
        assertThat(hrefs(view(mock(AuthenticationContext.class), "1234.apps.googleusercontent.com")))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE, SecurityConfiguration.GOOGLE_LOGIN_PAGE);
    }

    private static List<String> hrefs(Component view) {
        SignInButtons buttons = view.getChildren().filter(SignInButtons.class::isInstance)
                .map(SignInButtons.class::cast).findFirst().orElseThrow();
        return buttons.getChildren().filter(Anchor.class::isInstance).map(Component::getElement)
                .map(element -> element.getAttribute("href")).toList();
    }
}
