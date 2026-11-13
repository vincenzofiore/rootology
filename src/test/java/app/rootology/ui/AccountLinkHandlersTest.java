package app.rootology.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a link ends in the browser (#28): a refusal goes back to My account with its fixed code and leaves the
 * person signed in; an ordinary sign-in failure goes back to the welcome page with one generic message (#47).
 */
class AccountLinkHandlersTest {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void aRequest() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static OAuth2AuthenticationException refused(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), "Linking an account was refused");
    }

    @Test
    void aRefusedLinkGoesBackToMyAccountWithItsCode() throws Exception {
        Authentication signedIn = new TestingAuthenticationToken("ada", "an-oauth-principal", "ROLE_QA");
        SecurityContextHolder.getContext().setAuthentication(signedIn);
        PendingLink.start("ada", app.rootology.spaces.IdentityProvider.GOOGLE);

        new LinkFailureHandler().onAuthenticationFailure(request, response, refused("taken"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/account?link=taken");
        // The failure handler neither signs out nor changes who the person is: they stay as they were.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(signedIn);
        assertThat(PendingLink.current()).isNull();
    }

    @Test
    void everyRefusalCodeGoesInTheUrlAndNothingElse() throws Exception {
        for (PendingLink.Result result : PendingLink.Result.values()) {
            MockHttpServletResponse each = new MockHttpServletResponse();
            new LinkFailureHandler().onAuthenticationFailure(request, each, refused(result.code()));
            assertThat(each.getRedirectedUrl()).isEqualTo("/account?link=" + result.code());
        }
    }

    @Test
    void anOrdinarySignInFailureGoesToTheWelcomePage() throws Exception {
        new LinkFailureHandler().onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "not admitted"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/" + WelcomeView.ROUTE + "?error=denied");
    }

    @Test
    void aSuccessAfterALinkLandsInMyAccount() throws Exception {
        PendingLink.start("ada", app.rootology.spaces.IdentityProvider.GITHUB);
        PendingLink.clear();
        PendingLink.markLinked();

        new LinkSuccessHandler(unused()).onAuthenticationSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("/account?link=linked");
        assertThat(PendingLink.takeLinked()).isFalse();
    }

    @Test
    void everyOtherSignInIsLeftToTheHandlerItHad() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        AuthenticationSuccessHandler signIn = (req, res, authentication) -> called.set(true);

        new LinkSuccessHandler(signIn).onAuthenticationSuccess(request, response, null);

        assertThat(called).isTrue();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    private static AuthenticationSuccessHandler unused() {
        return (req, res, authentication) -> {
            throw new AssertionError("a link must not be handed to the plain sign-in handler");
        };
    }
}
