package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;

/**
 * The top of a page (#87): its title on the left and its main action on the right, the same on every page, so
 * the action is always found in the same place. On a narrow screen the action stays on the title's line.
 */
class PageHeader extends Div {

    private final H3 title = new H3();
    private final Div actions = new Div();

    PageHeader(String text, Component... mainActions) {
        title.setText(text);
        title.addClassName("rootology-page-title");
        actions.addClassName("rootology-page-actions");
        actions.add(mainActions);
        add(title, actions);
        addClassName("rootology-page-header");
    }

    void setHeading(String text) {
        title.setText(text);
    }

    String heading() {
        return title.getText();
    }
}
