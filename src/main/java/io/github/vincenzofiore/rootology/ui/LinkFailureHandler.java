package io.github.vincenzofiore.rootology.ui;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * What happens when linking an account from My account fails (#28). The person must stay signed in as before:
 * instead of Spring's default failure, they go back to My account with the reason in the URL — one of the
 * fixed codes of {@link PendingLink.Result}, never an email, a login or a provider's message.
 *
 * <p>A failure that is not a refused link (an ordinary sign-in gone wrong) goes back to the welcome page with one
 * generic message (#47), never the provider's. The marker itself is already forgotten by the link rules when this
 * runs, so the exception's code is what tells the two apart: those codes come from nothing else.
 */
class LinkFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final RedirectStrategy redirects = new DefaultRedirectStrategy();

    LinkFailureHandler() {
        // An ordinary sign-in failure (not a refused link) goes back to the welcome page with one generic
        // message (#47); a refused link from My account is handled below, before this default.
        setDefaultFailureUrl("/" + WelcomeView.ROUTE + "?error=denied");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        PendingLink.Result result = PendingLink.Result.of(exception);
        if (result == null) {
            super.onAuthenticationFailure(request, response, exception);
            return;
        }
        PendingLink.clear();
        redirects.sendRedirect(request, response, "/" + AccountView.ROUTE + "?link=" + result.code());
    }
}
