package io.github.vincenzofiore.rootology.ui;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubEmailsTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://github.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GitHubEmails emails = new GitHubEmails(builder.build());

    @Test
    void keepsOnlyVerifiedAddressesInLowerCase() {
        server.expect(requestTo("https://github.test/user/emails"))
                .andExpect(header("Authorization", "Bearer gho_test"))
                .andRespond(withSuccess("""
                        [{"email":"Ann@Example.org","verified":true,"primary":true,"visibility":"private"},
                         {"email":"old@example.org","verified":false,"primary":false,"visibility":null}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(emails.verified("gho_test")).containsExactly("ann@example.org");
    }

    @Test
    void githubDownMeansNoVerifiedAddress() {
        server.expect(requestTo("https://github.test/user/emails")).andRespond(withServerError());

        assertThat(emails.verified("gho_test")).isEmpty();
    }
}
