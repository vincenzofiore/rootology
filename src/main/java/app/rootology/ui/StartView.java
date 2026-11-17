package app.rootology.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * Where a person lands after signing in (#56): the current space's gardens, also with one garden (#132), so
 * the app always starts from the same place; choosing a garden opens its home.
 */
@Route(value = "", layout = MainLayout.class)
@PermitAll
public class StartView extends VerticalLayout implements BeforeEnterObserver {

    private final CurrentUser currentUser;

    public StartView(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        long spaceId = currentUser.space().id();
        event.forwardTo("/spaces/" + spaceId + "/gardens");
    }
}
