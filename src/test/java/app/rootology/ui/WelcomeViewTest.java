package app.rootology.ui;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public welcome page (#47): someone already signed in goes Home, a refused sign-in shows one generic
 * message, a logout says so, and the ways in are in the bar.
 */
class WelcomeViewTest {

    private static WelcomeView view(AuthenticationContext authentication) {
        return new WelcomeView(authentication);
    }

    @Test
    void signedInPeopleAreSentToTheirGardens() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view(authentication).beforeEnter(event);

        verify(event).rerouteTo(StartView.class);
    }

    @Test
    void aRefusedSignInShowsTheGenericMessage() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location("welcome", QueryParameters.of("error", "denied")));

        WelcomeView welcome = view(authentication);
        welcome.beforeEnter(event);

        Paragraph denied = welcome.getChildren().filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .filter(p -> p.getElement().getClassList().contains("rootology-welcome-denied"))
                .findFirst().orElseThrow();
        assertThat(denied.getText()).isEqualTo(welcome.getTranslation("welcome.denied"));
        assertThat(denied.isVisible()).isTrue();
    }

    @Test
    void aLogoutIsSaidAtTheTop() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        when(event.getLocation()).thenReturn(new Location("welcome", QueryParameters.of(WelcomeView.SIGNED_OUT, "")));

        WelcomeView welcome = view(authentication);
        welcome.beforeEnter(event);

        Paragraph left = welcome.getChildren().filter(Paragraph.class::isInstance).map(Paragraph.class::cast)
                .filter(p -> p.getElement().getClassList().contains("rootology-welcome-signed-out"))
                .findFirst().orElseThrow();
        assertThat(left.isVisible()).isTrue();
        assertThat(left.getText()).startsWith(welcome.getTranslation("loggedout.title"));
    }

    @Test
    void theWaysInAreInTheBarNotOnThePage() {
        // #87: Sign in at the top right (SignInMenu) also leads to the invitation request.
        assertThat(view(mock(AuthenticationContext.class)).getChildren()).noneMatch(SignInButtons.class::isInstance);
    }
}
