package app.rootology.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;

import java.util.Map;

/**
 * The guided first steps of a garden (#81): a compact card with four steps, each a link, done or to do.
 * Self-contained, since #84 reworks the garden home as cards.
 */
public class FirstStepsCard extends Div {

    public FirstStepsCard(FirstSteps steps, long spaceId, long gardenId) {
        addClassName("rootology-firststeps");
        add(new H4(getTranslation("firststeps.title")));
        Div list = new Div();
        list.addClassName("rootology-firststeps-list");
        list.add(step(steps.located(), getTranslation("firststeps.location"), GardensView.class,
                Map.of(Routed.SPACE, String.valueOf(spaceId))));
        list.add(step(steps.hasPlot(), getTranslation("firststeps.plot"), AreasView.class, garden(spaceId, gardenId)));
        list.add(step(steps.hasPlanting(), getTranslation("firststeps.planting"), AreasView.class,
                garden(spaceId, gardenId)));
        list.add(step(steps.hasQuestion(), getTranslation("firststeps.question"), AskView.class,
                garden(spaceId, gardenId)));
        add(list);
    }

    private static Map<String, String> garden(long spaceId, long gardenId) {
        return Map.of(Routed.SPACE, String.valueOf(spaceId), Routed.GARDEN, String.valueOf(gardenId));
    }

    private RouterLink step(boolean done, String text, Class<? extends com.vaadin.flow.component.Component> view,
                            Map<String, String> parameters) {
        Icon icon = (done ? VaadinIcon.CHECK_CIRCLE_O : VaadinIcon.CIRCLE_THIN).create();
        icon.addClassName("rootology-firststeps-icon");
        String state = getTranslation(done ? "firststeps.done" : "firststeps.todo");
        icon.getElement().setAttribute("aria-label", state);
        icon.setTooltipText(state);
        RouterLink link = new RouterLink("", view, new RouteParameters(parameters));
        link.addClassName("rootology-firststeps-step");
        if (done) {
            link.addClassName("rootology-firststeps-done");
        }
        link.add(icon, new Span(text));
        return link;
    }
}
