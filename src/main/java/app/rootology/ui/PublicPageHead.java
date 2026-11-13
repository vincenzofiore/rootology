package app.rootology.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The head of the public pages, the only ones a search engine may index: welcome, sources and privacy. Each
 * states its own absolute address (#91), built from {@code rootology.base-url}, and its title, a description and
 * the Open Graph tags of a shared link's preview (#101), in the request's language (Italian when it sends none,
 * as a crawler does). They are written into the index HTML of the route, not added from the browser, so a crawler
 * reads them in the first response: the page's text is drawn later by JavaScript. Every other page gets none.
 */
@Component
class PublicPageHead implements VaadinServiceInitListener {

    /** Each public page's title and description keys. */
    private static final Map<String, List<String>> PUBLIC_PAGES = Map.of(
            WelcomeView.ROUTE, List.of("welcome.title", "seo.welcome.description"),
            SourcesView.ROUTE, List.of("sources.title", "seo.sources.description"),
            PrivacyView.ROUTE, List.of("privacy.title", "seo.privacy.description"));

    /** The sprout icon as the preview image: square, as the "summary" card wants. */
    static final String IMAGE = "/icons/icon-512.png";

    private final String baseUrl;
    private final Translations translations;

    PublicPageHead(@Value("${rootology.base-url}") String baseUrl, Translations translations) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.translations = translations;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addIndexHtmlRequestListener(this::addHead);
    }

    void addHead(IndexHtmlResponse response) {
        VaadinRequest request = response.getVaadinRequest();
        String page = page(request);
        List<String> keys = PUBLIC_PAGES.get(page);
        if (keys == null) {
            return;
        }
        Element head = response.getDocument().head();
        Locale language = language(request);
        String address = baseUrl + "/" + page;
        String title = title(page, translations.getTranslation(keys.get(0), language));
        String description = translations.getTranslation(keys.get(1), language);

        if (head.select("link[rel=canonical]").isEmpty()) {
            head.appendElement("link").attr("rel", "canonical").attr("href", address);
        }
        if (head.select("title").isEmpty()) {
            head.appendElement("title").text(title);
        }
        meta(head, "name", "description", description);
        meta(head, "property", "og:type", "website");
        meta(head, "property", "og:site_name", "Rootology");
        meta(head, "property", "og:title", title);
        meta(head, "property", "og:description", description);
        meta(head, "property", "og:url", address);
        meta(head, "property", "og:image", baseUrl + IMAGE);
        meta(head, "property", "og:locale", language == Locale.ENGLISH ? "en_GB" : "it_IT");
        meta(head, "name", "twitter:card", "summary");
    }

    /** The welcome page's title already names the app; the others add it, as their tab title does. */
    private static String title(String page, String text) {
        return page.equals(WelcomeView.ROUTE) ? text : text + " · Rootology";
    }

    private static void meta(Element head, String attribute, String name, String content) {
        if (head.select("meta[" + attribute + "=" + name.replace(":", "\\:") + "]").isEmpty()) {
            head.appendElement("meta").attr(attribute, name).attr("content", content);
        }
    }

    /** English for a request that prefers it over Italian, Italian otherwise, as {@link Translations} does. */
    static Locale language(VaadinRequest request) {
        String accepted = request.getHeader("Accept-Language");
        if (accepted == null || accepted.isBlank()) {
            return Translations.ITALIAN;
        }
        try {
            String tag = Locale.lookupTag(Locale.LanguageRange.parse(accepted), List.of("it", "en"));
            return "en".equals(tag) ? Locale.ENGLISH : Translations.ITALIAN;
        } catch (IllegalArgumentException e) {
            return Translations.ITALIAN;
        }
    }

    /** The route of the request, without the servlet context and its slashes ("welcome"). */
    private static String page(VaadinRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            return "";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
