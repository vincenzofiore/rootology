package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without a login, the UI sends the browser to the welcome page (#47). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FilterChainProxy securityFilterChain;

    @Test
    void anonymousVisitorsAreSentToTheWelcomePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/" + WelcomeView.ROUTE));
    }

    @Test
    void anonymousVisitorsOnAProtectedRouteAreSentToTheWelcomePage() throws Exception {
        mockMvc.perform(get("/ask"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/" + WelcomeView.ROUTE));
    }

    @Test
    void logoutLandsOnTheLoggedOutPage() throws Exception {
        mockMvc.perform(post("/logout").with(oauth2Login()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/" + SecurityConfiguration.LOGGED_OUT_PAGE));
    }

    @Test
    void theGitHubLoginStartsTheOAuthFlow() throws Exception {
        mockMvc.perform(get(SecurityConfiguration.GITHUB_LOGIN_PAGE))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://github.com/login/oauth/authorize?")));
    }

    @Test
    void aLinkIsAnsweredInMyAccountWhileAPlainSignInKeepsItsHandlers() {
        OAuth2LoginAuthenticationFilter oauth2 = securityFilterChain.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .findFirst().orElseThrow();

        assertThat(handler(oauth2, "failureHandler")).isInstanceOf(LinkFailureHandler.class);
        assertThat(handler(oauth2, "successHandler")).isInstanceOf(LinkSuccessHandler.class);
    }

    /** The handlers are protected in Spring's filter; the wiring is what this test is about. */
    private static Object handler(OAuth2LoginAuthenticationFilter filter, String field) {
        try {
            java.lang.reflect.Field declared = org.springframework.security.web.authentication
                    .AbstractAuthenticationProcessingFilter.class.getDeclaredField(field);
            declared.setAccessible(true);
            return declared.get(filter);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
