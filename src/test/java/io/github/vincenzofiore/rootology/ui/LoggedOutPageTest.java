package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public pages, served without a login by the real Vaadin servlet (MockMvc has none): the page
 * logout lands on (if it were protected, the browser would go straight back through GitHub and
 * log the user in again), and invitation links (the invited person has no account yet).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoggedOutPageTest {

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void invitationLinksArePublic() throws Exception {
        // The invited person has no account yet; the page itself decides whether the token is valid.
        assertThat(get("/" + InvitationView.ROUTE + "/some-token").statusCode()).isEqualTo(200);
    }

    @Test
    void theLoggedOutPageIsPublic() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"
                        + SecurityConfiguration.LOGGED_OUT_PAGE)).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
