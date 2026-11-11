package app.rootology.ui;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Where a sign-in that linked an account lands (#28): back in My account with the confirmation, rather than
 * on the page a plain sign-in would open. Every other sign-in is left to the handler it had.
 */
class LinkSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationSuccessHandler signIn;
    private final RedirectStrategy redirects = new DefaultRedirectStrategy();

    LinkSuccessHandler(AuthenticationSuccessHandler signIn) {
        this.signIn = signIn;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!PendingLink.takeLinked()) {
            signIn.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        redirects.sendRedirect(request, response,
                "/" + AccountView.ROUTE + "?link=" + PendingLink.Result.LINKED.code());
    }
}
