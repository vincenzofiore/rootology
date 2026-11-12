package app.rootology.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The canonical link of the public pages (#91): the welcome, sources and privacy pages are the only ones a
 * search engine may index, and each says its own absolute address, built from {@code rootology.base-url}.
 * It is written into the index HTML of the route (not added from the browser), so a crawler reads it in the
 * first response. Every other page gets no canonical link.
 */
@Component
class CanonicalLinks implements VaadinServiceInitListener {

    private static final Set<String> PUBLIC_PAGES =
            Set.of(WelcomeView.ROUTE, SourcesView.ROUTE, PrivacyView.ROUTE);

    private final String baseUrl;

    CanonicalLinks(@Value("${rootology.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addIndexHtmlRequestListener(this::addCanonicalLink);
    }

    void addCanonicalLink(IndexHtmlResponse response) {
        String page = page(response.getVaadinRequest());
        if (!PUBLIC_PAGES.contains(page)) {
            return;
        }
        Document document = response.getDocument();
        if (!document.head().select("link[rel=canonical]").isEmpty()) {
            return;
        }
        document.head().appendElement("link")
                .attr("rel", "canonical")
                .attr("href", baseUrl + "/" + page);
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
