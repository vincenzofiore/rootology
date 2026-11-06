package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.vaadin.flow.spring.security.UidlRedirectStrategy;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.RoleService;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every view needs a GitHub login, except the logged-out page, invitation links and the sources page. Who may sign
 * in is decided by AllowListOAuth2UserService.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

    static final String GITHUB_LOGIN_PAGE = "/oauth2/authorization/github";

    /** Public page shown after logout (see LoggedOutView). */
    static final String LOGGED_OUT_PAGE = "logged-out";

    /**
     * Vaadin needs inline scripts and styles and eval for its client engine; everything else comes from this
     * origin only (weather icons and styles included), and no page may be framed (#41).
     */
    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self' ws: wss:; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'";

    @Bean
    AllowListOAuth2UserService allowListOAuth2UserService(
            @Value("${rootology.security.allowed-github-users:}") String allowedUsers, SpaceService spaces,
            InvitationService invitations, RoleService roles, NotificationService notifications) {
        Set<String> allowed = Arrays.stream(allowedUsers.split(","))
                .map(SpaceService::normalizeLogin)
                .filter(u -> !u.isEmpty())
                .collect(Collectors.toSet());
        return new AllowListOAuth2UserService(allowed, spaces, invitations, roles,
                new GitHubEmails("https://api.github.com"), notifications);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AllowListOAuth2UserService userService) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/" + LOGGED_OUT_PAGE, "/" + InvitationView.ROUTE + "/**", "/" + SourcesView.ROUTE,
                        "/" + PrivacyView.ROUTE, "/weather-icons/**", "/styles/**", "/icons/**", "/actuator/health",
                        "/actuator/health/**")
                .permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN"));
        // GitHub is plain OAuth2, not OIDC, so the post-logout URL is set on the logout handler itself.
        SimpleUrlLogoutSuccessHandler loggedOut = new SimpleUrlLogoutSuccessHandler();
        loggedOut.setDefaultTargetUrl("/" + LOGGED_OUT_PAGE);
        loggedOut.setRedirectStrategy(new UidlRedirectStrategy()); // also redirects Vaadin's own requests
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                .oauth2LoginPage(GITHUB_LOGIN_PAGE)
                .logoutSuccessHandler(loggedOut));
        http.oauth2Login(login -> login.userInfoEndpoint(info -> info.userService(userService)));
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
}
