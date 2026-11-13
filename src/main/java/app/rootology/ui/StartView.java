package app.rootology.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import jakarta.annotation.security.PermitAll;

import java.util.List;

/**
 * Where a person lands after signing in (#56): the current space's garden when it has exactly one, so
 * working starts at once, otherwise the gardens overview; the overview is always one click away.
 */
@Route(value = "", layout = MainLayout.class)
@PermitAll
public class StartView extends VerticalLayout implements BeforeEnterObserver {

    private final GardenService gardens;
    private final CurrentUser currentUser;

    public StartView(GardenService gardens, CurrentUser currentUser) {
        this.gardens = gardens;
        this.currentUser = currentUser;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        long spaceId = currentUser.space().id();
        List<GardenSummary> all = gardens.gardens(spaceId, currentUser.login());
        event.forwardTo(all.size() == 1
                ? "/spaces/" + spaceId + "/gardens/" + all.getFirst().id()
                : "/spaces/" + spaceId + "/gardens");
    }
}
