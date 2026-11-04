package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * The app shell: server push (WebSocket), so open pages update by themselves, for example the notification
 * counter (#26); and the Lumo theme, which in Vaadin 25 must be declared once an app shell exists.
 */
@Push
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
public class AppShell implements AppShellConfigurator {
}
