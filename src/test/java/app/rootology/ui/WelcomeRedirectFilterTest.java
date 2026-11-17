package app.rootology.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** "/" is the welcome page for good only for a visitor's plain GET (#124). */
class WelcomeRedirectFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAnonymousVisitorsRootIsAPermanentRedirectNeverStored() throws Exception {
        anonymous();
        MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/"));

        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeader("Location")).isEqualTo("/welcome");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(run(new MockHttpServletRequest("HEAD", "/")).getStatus()).as("a HEAD too").isEqualTo(301);
    }

    @Test
    void vaadinsRequestsOtherPagesAndSignedInPeopleGoOn() throws Exception {
        anonymous();
        MockHttpServletRequest vaadin = new MockHttpServletRequest("GET", "/");
        vaadin.setQueryString("v-r=init");
        assertThat(passes(vaadin)).as("Vaadin's client").isTrue();
        assertThat(passes(new MockHttpServletRequest("POST", "/"))).as("a POST").isTrue();
        assertThat(passes(new MockHttpServletRequest("GET", "/privacy"))).as("another page").isTrue();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("ada", null, "ROLE_USER"));
        assertThat(passes(new MockHttpServletRequest("GET", "/"))).as("a signed-in person's start page").isTrue();
    }

    private static void anonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    private static boolean passes(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        new WelcomeRedirectFilter().doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    private static MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new WelcomeRedirectFilter().doFilter(request, response, new MockFilterChain());
        return response;
    }
}
