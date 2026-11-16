package app.rootology.ui;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The root of the site, for a visitor who is not signed in, is the welcome page for good: a permanent redirect
 * (301), so a search engine indexes /welcome as the page itself and not as a duplicate of "/" (Search Console:
 * "Google chose a different canonical", #124). Spring Security's own redirect to the login page is temporary
 * (302), which keeps "/" as the canonical URL. Only a plain GET or HEAD of "/" with no query: Vaadin's client
 * sends its requests to "/?v-r=..." and they go on as before, and a signed-in person's "/" is the app's start page.
 * The response is never stored, so a browser that signs in later is not sent back to the welcome page.
 */
class WelcomeRedirectFilter extends OncePerRequestFilter {

    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !trust.isAnonymous(authentication);
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if (!signedIn && read && request.getQueryString() == null
                && "/".equals(request.getRequestURI().substring(request.getContextPath().length()))) {
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", request.getContextPath() + "/" + WelcomeView.ROUTE);
            response.setHeader("Cache-Control", "no-store");
            return;
        }
        chain.doFilter(request, response);
    }
}
