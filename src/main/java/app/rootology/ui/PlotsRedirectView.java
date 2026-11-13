package app.rootology.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * The plots page before #85: links already sent by email land on the same garden's areas page. Nothing
 * renders: the page forwards at once, and the browser's address follows.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/plots", layout = MainLayout.class)
@PermitAll
public class PlotsRedirectView extends VerticalLayout implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String spaceId = event.getRouteParameters().get(Routed.SPACE).orElse("");
        String gardenId = event.getRouteParameters().get(Routed.GARDEN).orElse("");
        event.forwardTo(target(spaceId, gardenId));
    }

    /** The areas page of the same garden, with the query parameters of the old link kept by the forward. */
    static String target(String spaceId, String gardenId) {
        return "/spaces/" + spaceId + "/gardens/" + gardenId + "/areas";
    }
}
