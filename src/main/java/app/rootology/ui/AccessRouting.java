package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import java.util.Set;

/**
 * Where a person who signed in but is not admitted may go (#65): their request page, and the public pages
 * everyone reads. Any other route sends them back to the request page, in one place instead of in every view;
 * an admitted person who opens the request page is sent home. The services stay safe on their own, since a
 * pending person belongs to no space and is a member of nothing.
 *
 * <p>It runs before Vaadin's own navigation access control, which would otherwise answer a pending person with
 * "access denied" on an admin, curator or QA page: this listener is a scanned {@code @Component}, registered
 * before the one the Vaadin Spring security auto-configuration adds.
 */
@org.springframework.stereotype.Component
class AccessRouting implements VaadinServiceInitListener {

    /**
     * The pages a pending person may see: the request one, the public pages everyone reads, and an invitation,
     * which they accept by signing in again with it (see InvitationView).
     */
    static final Set<Class<? extends Component>> OPEN_TO_PENDING = Set.of(AccessRequestView.class,
            SourcesView.class, PrivacyView.class, WelcomeView.class, LoggedOutView.class, InvitationView.class);

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(ui -> ui.getUI().addBeforeEnterListener(this::beforeEnter));
    }

    void beforeEnter(BeforeEnterEvent event) {
        Class<? extends Component> target = event.getNavigationTarget();
        if (PendingAccess.isPending()) {
            if (!OPEN_TO_PENDING.contains(target)) {
                event.rerouteTo(AccessRequestView.class);
            }
        } else if (PendingAccess.signedIn() && target == AccessRequestView.class) {
            event.rerouteTo("");
        }
    }
}
