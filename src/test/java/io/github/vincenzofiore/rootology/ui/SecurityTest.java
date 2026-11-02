package io.github.vincenzofiore.rootology.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without a login, the UI sends the browser to GitHub's OAuth flow. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousVisitorsAreSentToGitHubLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(SecurityConfiguration.GITHUB_LOGIN_PAGE));
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
}
