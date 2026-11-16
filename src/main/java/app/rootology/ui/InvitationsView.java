package app.rootology.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * The invitations page before #105: invitations are now a section of the members page, with "Invite" as its
 * main action. Old links forward there; nothing renders.
 */
@Route(value = "spaces/:spaceId/invitations", layout = MainLayout.class)
@PermitAll
public class InvitationsView extends VerticalLayout implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.forwardTo("/spaces/" + event.getRouteParameters().get(Routed.SPACE).orElse("") + "/members");
    }
}
