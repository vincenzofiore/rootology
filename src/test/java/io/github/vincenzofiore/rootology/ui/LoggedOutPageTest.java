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
 * The page logout lands on is served without a login, by the real Vaadin servlet (MockMvc has none).
 * If it were protected, the browser would go straight back through GitHub and log the user in again.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoggedOutPageTest {

    @LocalServerPort
    int port;

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
