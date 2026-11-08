package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import jakarta.annotation.security.PermitAll;

/**
 * The pages before #56 (#56): links already sent by email land on the right garden, or on the space's
 * gardens overview. Nothing renders: the page forwards at once, and the browser's address follows.
 */
@Route(value = LegacyRoutes.ASK)
@RouteAlias(LegacyRoutes.GARDEN)
@RouteAlias(LegacyRoutes.ESCALATIONS)
@RouteAlias(LegacyRoutes.HISTORY)
@RouteAlias(LegacyRoutes.KNOWLEDGE)
@RouteAlias(LegacyRoutes.SPACE)
@PermitAll
public class LegacyRouteView extends VerticalLayout implements BeforeEnterObserver {

    private final GardenService gardens;
    private final RunHistory history;
    private final GardenPipeline pipeline;
    private final CurrentUser currentUser;

    public LegacyRouteView(GardenService gardens, RunHistory history, GardenPipeline pipeline, CurrentUser currentUser) {
        this.gardens = gardens;
        this.history = history;
        this.pipeline = pipeline;
        this.currentUser = currentUser;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String path = event.getLocation().getPath();
        String first = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
        event.forwardTo(LegacyRoutes.target(first, event.getLocation().getQueryParameters(), gardens, history,
                pipeline, currentUser));
    }
}
