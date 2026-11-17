package app.rootology.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import app.rootology.garden.GardenService;
import jakarta.annotation.security.PermitAll;

import java.util.Map;
import java.util.Optional;

/**
 * The old waiting questions page (#86, #109), kept for its links (notifications, bookmarks): the waiting
 * questions are now on Questions (#132), so it forwards there with the waiting ones open and filtered.
 */
@Route(value = "spaces/:spaceId/gardens/:gardenId/escalations", layout = MainLayout.class)
@RouteAlias(value = "spaces/:spaceId/escalations", layout = MainLayout.class)
@PermitAll
public class EscalationView extends Div implements BeforeEnterObserver {

    private final GardenService gardens;
    private final CurrentUser currentUser;

    public EscalationView(GardenService gardens, CurrentUser currentUser) {
        this.gardens = gardens;
        this.currentUser = currentUser;
    }

    /** A space or garden the person cannot see is not found, as on every page (#56). */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Routed.Where> where = Routed.garden(event, currentUser, gardens);
        where.ifPresent(w -> event.forwardTo(
                HistoryView.target(String.valueOf(w.spaceId()), w.gardenId() > 0 ? String.valueOf(w.gardenId()) : ""),
                QueryParameters.simple(Map.of(AskView.WAITING, ""))));
    }
}
