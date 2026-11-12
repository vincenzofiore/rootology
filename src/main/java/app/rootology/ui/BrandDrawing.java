package app.rootology.ui;

import com.vaadin.flow.component.Svg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A drawing of the brand (src/main/resources/brand, #87) inlined in the page, so it takes the theme's colours
 * from styles/rootology.css. Its words ({label} for screen readers, speech bubbles) are placeholders filled
 * with translations, so one drawing serves every language.
 */
final class BrandDrawing {

    private BrandDrawing() {
    }

    static Svg of(String resource, Map<String, String> words) {
        String svg = read(resource);
        for (Map.Entry<String, String> word : words.entrySet()) {
            svg = svg.replace("{" + word.getKey() + "}", escape(word.getValue()));
        }
        return new Svg(svg);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String read(String resource) {
        try (InputStream in = BrandDrawing.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Missing " + resource, e);
        }
    }
}
