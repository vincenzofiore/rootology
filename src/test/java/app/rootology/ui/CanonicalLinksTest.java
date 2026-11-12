package app.rootology.ui;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The canonical link is written only for the pages a search engine may index (#91). */
class CanonicalLinksTest {

    private final CanonicalLinks links = new CanonicalLinks("https://rootology.app/");

    @Test
    void aPublicPageStatesItsOwnAddress() {
        Document document = new Document("");

        links.addCanonicalLink(response("/welcome", document));

        assertThat(document.head().select("link[rel=canonical]").attr("href"))
                .isEqualTo("https://rootology.app/welcome");
    }

    @Test
    void aPageBehindTheSignInGetsNoCanonicalLink() {
        Document document = new Document("");

        links.addCanonicalLink(response("/spaces/1/gardens", document));

        assertThat(document.head().select("link[rel=canonical]")).isEmpty();
    }

    @Test
    void anAddressAlreadyStatedIsNotRepeated() {
        Document document = new Document("");
        document.head().appendElement("link").attr("rel", "canonical").attr("href", "https://rootology.app/privacy");

        links.addCanonicalLink(response("/privacy", document));

        assertThat(document.head().select("link[rel=canonical]")).hasSize(1);
    }

    private static IndexHtmlResponse response(String path, Document document) {
        VaadinRequest request = mock(VaadinRequest.class);
        when(request.getPathInfo()).thenReturn(path);
        IndexHtmlResponse response = mock(IndexHtmlResponse.class);
        when(response.getVaadinRequest()).thenReturn(request);
        when(response.getDocument()).thenReturn(document);
        return response;
    }
}
