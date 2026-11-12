package app.rootology.ui;

import app.rootology.TestcontainersConfiguration;
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
 * Public pages, served without a login by the real Vaadin servlet (MockMvc has none): the welcome page (#47),
 * the page logout lands on (if it were protected, the browser would go straight back through the provider and
 * log the user in again) and invitation links (the invited person has no account yet).
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
    void theWelcomePageIsPublic() throws Exception {
        assertThat(get("/" + WelcomeView.ROUTE).statusCode()).isEqualTo(200);
    }

    @Test
    void invitationLinksArePublic() throws Exception {
        // The invited person has no account yet; the page itself decides whether the token is valid.
        assertThat(get("/" + InvitationView.ROUTE + "/some-token").statusCode()).isEqualTo(200);
    }

    @Test
    void theSourcesPageIsPublic() throws Exception {
        assertThat(get("/" + SourcesView.ROUTE).statusCode()).isEqualTo(200);
    }

    @Test
    void thePrivacyPageIsPublic() throws Exception {
        assertThat(get("/" + PrivacyView.ROUTE).statusCode()).isEqualTo(200);
    }

    @Test
    void metricsEndpointRequiresAuthentication() throws Exception {
        var response = get("/actuator/metrics");
        assertThat(response.statusCode()).isIn(302, 401, 403);
    }

    @Test
    void readinessIsPublicButDoesNotRevealComponentDetails() throws Exception {
        var response = get("/actuator/health/readiness");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\"").doesNotContain("components");
    }

    @Test
    void theHealthCheckIsPublicAndGivesNoDetails() throws Exception {
        // For Caddy and an uptime monitor (#12): the status only, no components.
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    }

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        var headers = get("/" + PrivacyView.ROUTE).headers();
        assertThat(headers.firstValue("Content-Security-Policy")).hasValue(SecurityConfiguration.CONTENT_SECURITY_POLICY);
        assertThat(headers.firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
        assertThat(headers.firstValue("Permissions-Policy")).hasValueSatisfying(p -> assertThat(p).contains("camera=()"));
        assertThat(headers.firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(headers.firstValue("X-Content-Type-Options")).hasValue("nosniff");
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
