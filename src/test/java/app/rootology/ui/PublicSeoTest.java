package app.rootology.ui;

import app.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a crawler sees without signing in (#91): robots.txt and sitemap.xml are public static files with the
 * right content type, the public pages state their canonical address, and the app still needs a login.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicSeoTest {

    @LocalServerPort
    int port;

    /** The address the canonical links are built from, set by the environment or the default. */
    @Value("${rootology.base-url}")
    String baseUrl;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void robotsTxtIsPublic() throws Exception {
        HttpResponse<String> response = get("/robots.txt");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/plain");
        assertThat(response.body())
                .contains("User-agent: *", "Allow: /welcome", "Allow: /sources", "Allow: /privacy",
                        "Disallow: /", "Sitemap: https://rootology.app/sitemap.xml");
    }

    @Test
    void sitemapIsPublic() throws Exception {
        HttpResponse<String> response = get("/sitemap.xml");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/xml");
        assertThat(response.body())
                .contains("https://rootology.app/welcome", "https://rootology.app/sources",
                        "https://rootology.app/privacy");
    }

    @Test
    void theManifestIsPublicAndInstallsTheApp() throws Exception {
        HttpResponse<String> response = get("/manifest.json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.body())
                .contains("\"name\": \"Rootology\"", "\"short_name\": \"Rootology\"",
                        "\"start_url\": \"/\"", "\"display\": \"standalone\"",
                        "\"icons/icon-192.png\"", "\"icons/icon-512.png\"");
    }

    @Test
    void theIconsSafariAsksForAtTheRootArePublicImages() throws Exception {
        for (String path : java.util.List.of("/apple-touch-icon.png", "/apple-touch-icon-precomposed.png")) {
            HttpResponse<String> response = get(path);

            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).as(path).startsWith("image/png");
        }
    }

    /**
     * A Vaadin page once its dev server is ready: until then, in development mode, Vaadin answers with a
     * waiting page. With shared test containers (#98) the context starts faster than the dev server.
     */
    private HttpResponse<String> page(String path) throws Exception {
        HttpResponse<String> response = get(path);
        for (int i = 0; i < 120 && response.body().contains("devServerIsNotLoaded"); i++) {
            Thread.sleep(1000);
            response = get(path);
        }
        return response;
    }

    @Test
    void theWelcomePageStatesItsCanonicalAddress() throws Exception {
        HttpResponse<String> response = page("/" + WelcomeView.ROUTE);

        assertThat(response.statusCode()).isEqualTo(200);
        String canonical = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        assertThat(response.body()).contains("rel=\"canonical\"").contains("href=\"" + canonical + "/welcome\"");
    }

    @Test
    void theAppStillNeedsASignIn() throws Exception {
        HttpResponse<String> response = get("/spaces/1/gardens");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/" + WelcomeView.ROUTE);
    }
}
