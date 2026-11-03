package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.vaadin.flow.spring.security.UidlRedirectStrategy;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every view needs a GitHub login, except the logged-out page and invitation links. Who may sign
 * in is decided by AllowListOAuth2UserService.
 */
@Configuration
class SecurityConfiguration {

    static final String GITHUB_LOGIN_PAGE = "/oauth2/authorization/github";

    /** Public page shown after logout (see LoggedOutView). */
    static final String LOGGED_OUT_PAGE = "logged-out";

    @Bean
    AllowListOAuth2UserService allowListOAuth2UserService(
            @Value("${rootology.security.allowed-github-users:}") String allowedUsers, SpaceService spaces,
            InvitationService invitations) {
        Set<String> allowed = Arrays.stream(allowedUsers.split(","))
                .map(SpaceService::normalizeLogin)
                .filter(u -> !u.isEmpty())
                .collect(Collectors.toSet());
        return new AllowListOAuth2UserService(allowed, spaces, invitations);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AllowListOAuth2UserService userService) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/" + LOGGED_OUT_PAGE, "/" + InvitationView.ROUTE + "/**").permitAll());
        // GitHub is plain OAuth2, not OIDC, so the post-logout URL is set on the logout handler itself.
        SimpleUrlLogoutSuccessHandler loggedOut = new SimpleUrlLogoutSuccessHandler();
        loggedOut.setDefaultTargetUrl("/" + LOGGED_OUT_PAGE);
        loggedOut.setRedirectStrategy(new UidlRedirectStrategy()); // also redirects Vaadin's own requests
        http.with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin
                .oauth2LoginPage(GITHUB_LOGIN_PAGE)
                .logoutSuccessHandler(loggedOut));
        http.oauth2Login(login -> login.userInfoEndpoint(info -> info.userService(userService)));
        return http.build();
    }
}
