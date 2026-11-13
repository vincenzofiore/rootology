package app.rootology.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;

import java.util.Map;

/**
 * The History page before #100: the history is now a column of Ask, so links already sent (and the old
 * {@code /history} of {@link LegacyRoutes}) land on the same context's Ask with the column open. Nothing
 * renders: the page forwards at once, and Ask checks the space and the garden.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/history", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/history", layout = MainLayout.class)
@PermitAll
public class HistoryView extends VerticalLayout implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String spaceId = event.getRouteParameters().get(Routed.SPACE).orElse("");
        String gardenId = event.getRouteParameters().get(Routed.GARDEN).orElse("");
        event.forwardTo(target(spaceId, gardenId), QueryParameters.simple(Map.of(AskView.HISTORY, "")));
    }

    /** Ask in the same garden, or at the space level; the forward adds {@code ?history} to open the column. */
    static String target(String spaceId, String gardenId) {
        return "spaces/" + spaceId + (gardenId.isEmpty() ? "" : "/gardens/" + gardenId) + "/ask";
    }
}
