package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.vaadin.flow.spring.security.UidlRedirectStrategy;
import com.vaadin.flow.spring.security.VaadinSavedRequestAwareAuthenticationSuccessHandler;
import io.github.vincenzofiore.rootology.access.AccessRequestService;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.RoleService;
import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every view needs a sign-in, except the welcome page, the logged-out page, invitation links and the public
 * pages. Who may sign in is decided by AllowListOAuth2UserService.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    static final String GITHUB_LOGIN_PAGE = "/oauth2/authorization/github";

    /** Google (OpenID Connect); the button is offered only when it is configured (#28). */
    static final String GOOGLE_LOGIN_PAGE = "/oauth2/authorization/google";

    /** Public page shown after logout (see LoggedOutView). */
    static final String LOGGED_OUT_PAGE = "logged-out";

    /** The OAuth2 page that starts signing in with a provider (also the link started in My account, #28). */
    static String loginPage(IdentityProvider provider) {
        return provider == IdentityProvider.GOOGLE ? GOOGLE_LOGIN_PAGE : GITHUB_LOGIN_PAGE;
    }

    /** True when a Google client id is set; the placeholder from application.yaml means it is not. */
    static boolean googleConfigured(String clientId) {
        return clientId != null && !clientId.isBlank() && !"not-set".equals(clientId);
    }

    /**
     * Vaadin needs inline scripts and styles and eval for its client engine; everything else comes from this
     * origin only (weather icons and styles included), and no page may be framed (#41).
     *
     * <p>The gardens map (#66) is the one exception: MapLibre GL JS, bundled from npm and served from this
     * origin, fetches the OpenFreeMap style, its vector tiles, its glyphs and its TileJSON over `connect-src`
     * and loads its raster tiles and sprites through `img-src`. Its web worker is emitted among our own assets
     * and loaded with `new Worker(url)`, which `worker-src` allows by falling back to `script-src 'self'`:
     * no `blob:` and no extra directive are needed (checked with the policy enforced, in development and in
     * production mode). Nothing else of the map lives outside this origin.
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: https://tiles.openfreemap.org; "
            + "font-src 'self' data:; connect-src 'self' ws: wss: https://tiles.openfreemap.org; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'";

    @Bean
    SignInSteps signInSteps(SpaceService spaces, IdentityService identities, InvitationService invitations,
                            RoleService roles, NotificationService notifications,
                            AccessRequestService accessRequests) {
        return new SignInSteps(spaces, identities, invitations, roles, notifications, accessRequests);
    }

    @Bean
    AllowListOAuth2UserService allowListOAuth2UserService(
            @Value("${rootology.security.allowed-github-users:}") String allowedUsers, SpaceService spaces,
            IdentityService identities, SignInSteps steps) {
        Set<String> allowed = Arrays.stream(allowedUsers.split(","))
                .map(SpaceService::normalizeLogin)
                .filter(u -> !u.isEmpty())
                .collect(Collectors.toSet());
        return new AllowListOAuth2UserService(allowed, spaces, identities, steps,
                new GitHubEmails("https://api.github.com"));
    }

    @Bean
    GoogleOidcUserService googleOidcUserService(
            @Value("${rootology.security.allowed-google-emails:}") String allowedEmails, SpaceService spaces,
            IdentityService identities, InvitationService invitations, SignInSteps steps) {
        return new GoogleOidcUserService(Arrays.stream(allowedEmails.split(",")).collect(Collectors.toSet()),
                steps, spaces, identities, invitations);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AllowListOAuth2UserService userService,
                                            GoogleOidcUserService googleService) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/" + LOGGED_OUT_PAGE, "/" + InvitationView.ROUTE + "/**", "/" + SourcesView.ROUTE,
                        "/" + PrivacyView.ROUTE, "/" + WelcomeView.ROUTE, "/weather-icons/**", "/styles/**", "/icons/**",
                        "/images/**", "/actuator/health", "/actuator/health/**")
                .permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN"));
        // GitHub is plain OAuth2, not OIDC, so the post-logout URL is set on the logout handler itself.
        SimpleUrlLogoutSuccessHandler loggedOut = new SimpleUrlLogoutSuccessHandler();
        loggedOut.setDefaultTargetUrl("/" + LOGGED_OUT_PAGE);
        loggedOut.setRedirectStrategy(new UidlRedirectStrategy()); // also redirects Vaadin's own requests
        // A visitor who is not signed in lands on the welcome page, not straight on GitHub (#47).
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                .oauth2LoginPage("/" + WelcomeView.ROUTE)
                .logoutSuccessHandler(loggedOut));
        http.oauth2Login(login -> {
            login.userInfoEndpoint(info -> info
                    .userService(userService)
                    .oidcUserService(googleService));
            // A link started in My account (#28) always leaves the person signed in: a refusal goes back there
            // with its code (see LinkFailureHandler). An ordinary refusal goes to the welcome page with one
            // generic message (#47).
            login.failureHandler(new LinkFailureHandler());
        });
        // Added after Vaadin's configurer, so its success handler exists and is the one wrapped (see below).
        http.with(new LinkSuccessConfiguration());
        // Security headers (#41) beyond Spring's defaults (nosniff, frame DENY, no-store).
        http.headers(headers -> headers
                // Written at the start of every response: in production mode Vaadin sends its bootstrap page
                // for public routes without passing through the late writer, and the headers went missing (#12).
                .withObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                    @Override
                    public <O extends HeaderWriterFilter> O postProcess(O filter) {
                        filter.setShouldWriteHeadersEagerly(true);
                        return filter;
                    }
                })
                .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy(
                        "geolocation=(self), camera=(), microphone=(), payment=(), usb=()")));
        return http.build();
    }

    /**
     * Wraps the success handler of sign-ins (#28). It cannot be done where the rest of the login is configured:
     * Vaadin's own configurer sets that handler later, when the chain is built, so this one runs after it.
     */
    private static final class LinkSuccessConfiguration
            extends AbstractHttpConfigurer<LinkSuccessConfiguration, HttpSecurity> {

        @Override
        public void init(HttpSecurity http) {
            VaadinSavedRequestAwareAuthenticationSuccessHandler signIn =
                    http.getSharedObject(VaadinSavedRequestAwareAuthenticationSuccessHandler.class);
            if (signIn != null) {
                http.oauth2Login(login -> login.successHandler(new LinkSuccessHandler(signIn)));
            }
        }
    }
}
