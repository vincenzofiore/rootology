package app.rootology.ui;

import app.rootology.access.AccountBlockService;
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
 * <p>A blocked account (#165) is sent from any route to the blocked page, where it reads why and how to ask
 * for a review, and may read the public pages. The account's state is read on every navigation, so a session
 * opened before an admin blocked it stops at its next page too, not only a principal that signed in blocked.
 *
 * <p>It runs before Vaadin's own navigation access control, which would otherwise answer a pending or blocked
 * person with "access denied" on an admin, curator or QA page: this listener is a scanned {@code @Component},
 * registered before the one the Vaadin Spring security auto-configuration adds.
 */
@org.springframework.stereotype.Component
class AccessRouting implements VaadinServiceInitListener {

    /**
     * The pages a pending person may see: the request one, the public pages everyone reads, and an invitation,
     * which they accept by signing in again with it (see InvitationView).
     */
    static final Set<Class<? extends Component>> OPEN_TO_PENDING = Set.of(AccessRequestView.class,
            SourcesView.class, PrivacyView.class, WelcomeView.class, LoggedOutView.class, InvitationView.class);

    /** The pages a blocked person may see (#165): their own, and the public pages everyone reads. */
    static final Set<Class<? extends Component>> OPEN_TO_BLOCKED = Set.of(BlockedView.class,
            SourcesView.class, PrivacyView.class, WelcomeView.class, LoggedOutView.class);

    private final AccountBlockService blocks;

    AccessRouting(AccountBlockService blocks) {
        this.blocks = blocks;
    }

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
        } else if (blockedNow()) {
            if (!OPEN_TO_BLOCKED.contains(target)) {
                event.rerouteTo(BlockedView.class);
            }
        } else if (PendingAccess.signedIn() && target == AccessRequestView.class) {
            event.rerouteTo("");
        }
    }

    /**
     * True for a principal that signed in blocked, and for an admitted person whose account an admin blocked
     * meanwhile: one cheap query on every navigation, on a request that is not anonymous.
     */
    private boolean blockedNow() {
        if (BlockedAccess.isBlocked()) {
            return true;
        }
        String login = BlockedAccess.login();
        return PendingAccess.signedIn() && login != null && blocks.isBlocked(login);
    }
}
