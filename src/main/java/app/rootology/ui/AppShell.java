package app.rootology.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * The app shell: server push (WebSocket), so open pages update by themselves, for example the notification
 * counter (#26); the Lumo theme, which in Vaadin 25 must be declared once an app shell exists; and a light or
 * dark look that follows the device by default, which each person may override (#31, see MainLayout).
 */
@Push
@ColorScheme(ColorScheme.Value.LIGHT_DARK)
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("styles/rootology.css")
public class AppShell implements AppShellConfigurator {

    /**
     * Raised whenever the icons are redrawn: browsers keep a favicon for a long time and ask again only for a new
     * address (#87). The manifest's icons carry the same value.
     */
    static final String ICONS_VERSION = "?v=2";

    /** The sprout icon (#12, docs/brand): SVG for browsers, PNG for older ones and the phone's home screen. */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "icons/rootology.svg" + ICONS_VERSION, "any");
        settings.addFavIcon("icon", "icons/favicon-32.png" + ICONS_VERSION, "32x32");
        settings.addFavIcon("apple-touch-icon", "icons/apple-touch-icon.png" + ICONS_VERSION, "180x180");
        // Add to Home screen (#88): a full-screen start, never offline (ADR 0001).
        settings.addLink("manifest", "manifest.json");
    }
}
