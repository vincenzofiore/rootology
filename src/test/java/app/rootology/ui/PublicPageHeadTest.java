package app.rootology.ui;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The canonical link (#91), the title, the description and the preview tags (#101) are written only for the pages
 * a search engine may index, in the request's language.
 */
class PublicPageHeadTest {

    private final PublicPageHead head = new PublicPageHead("https://rootology.app/", new Translations());

    @Test
    void aPublicPageStatesItsOwnAddress() {
        Document document = new Document("");

        head.addHead(response("/welcome", null, document));

        assertThat(document.head().select("link[rel=canonical]").attr("href"))
                .isEqualTo("https://rootology.app/welcome");
    }

    @Test
    void aCrawlerGetsTheTitleAndTheDescriptionInItalian() {
        Document document = new Document("");

        head.addHead(response("/welcome", null, document));

        assertThat(document.title()).isEqualTo("Rootology, l’assistente per il tuo orto");
        assertThat(document.head().select("meta[name=description]").attr("content"))
                .startsWith("Rootology risponde alle domande sul tuo orto");
        assertThat(document.head().select("meta[property=og:locale]").attr("content")).isEqualTo("it_IT");
    }

    @Test
    void anEnglishBrowserGetsThemInEnglish() {
        Document document = new Document("");

        head.addHead(response("/sources", "en-GB,en;q=0.9,it;q=0.5", document));

        assertThat(document.title()).isEqualTo("Sources and licences · Rootology");
        assertThat(document.head().select("meta[property=og:description]").attr("content"))
                .startsWith("Where Rootology’s facts come from");
    }

    @Test
    void aSharedLinkHasItsPreview() {
        Document document = new Document("");

        head.addHead(response("/privacy", "de-DE", document));

        assertThat(document.head().select("meta[property=og:title]").attr("content")).isEqualTo("Privacy · Rootology");
        assertThat(document.head().select("meta[property=og:url]").attr("content"))
                .isEqualTo("https://rootology.app/privacy");
        assertThat(document.head().select("meta[property=og:image]").attr("content"))
                .isEqualTo("https://rootology.app/icons/icon-512.png");
        assertThat(document.head().select("meta[name=twitter:card]").attr("content")).isEqualTo("summary");
    }

    @Test
    void aPageBehindTheSignInGetsNothing() {
        Document document = new Document("");

        head.addHead(response("/spaces/1/gardens", null, document));

        assertThat(document.head().children()).isEmpty();
    }

    @Test
    void whatIsAlreadyStatedIsNotRepeated() {
        Document document = new Document("");
        document.head().appendElement("link").attr("rel", "canonical").attr("href", "https://rootology.app/privacy");
        document.head().appendElement("title").text("Privacy");

        head.addHead(response("/privacy", null, document));

        assertThat(document.head().select("link[rel=canonical]")).hasSize(1);
        assertThat(document.head().select("title")).hasSize(1);
    }

    private static IndexHtmlResponse response(String path, String acceptLanguage, Document document) {
        VaadinRequest request = mock(VaadinRequest.class);
        when(request.getPathInfo()).thenReturn(path);
        when(request.getHeader("Accept-Language")).thenReturn(acceptLanguage);
        IndexHtmlResponse response = mock(IndexHtmlResponse.class);
        when(response.getVaadinRequest()).thenReturn(request);
        when(response.getDocument()).thenReturn(document);
        return response;
    }
}
