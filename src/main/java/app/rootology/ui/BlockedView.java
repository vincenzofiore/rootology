package app.rootology.ui;

import app.rootology.access.AccountBlockService;
import app.rootology.privacy.PrivacyContact;
import app.rootology.spaces.AccountBlock;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

/**
 * What a person whose account an admin blocked sees (#165): the account is blocked, the reason when the admin
 * wrote one, and how to ask for a review (the controller's contact, or the email they received when the
 * deployment names none). The public bar's link signs them out, since nothing else of the app is theirs.
 */
@Route(value = BlockedView.ROUTE, layout = PublicLayout.class)
@PermitAll
public class BlockedView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    static final String ROUTE = "blocked";

    private final AccountBlockService blocks;
    private final PrivacyContact contact;

    public BlockedView(AccountBlockService blocks, PrivacyContact contact) {
        this.blocks = blocks;
        this.contact = contact;
        addClassName("rootology-prose");
        add(new H2(getTranslation("blocked.title")));
        add(new Paragraph(getTranslation("blocked.text")));
        blocks.blockOf(BlockedAccess.login()).map(AccountBlock::reason).ifPresent(reason ->
                add(new Paragraph(getTranslation("blocked.reason", reason))));
        add(new Paragraph(contact.configured()
                ? getTranslation("blocked.review", contact.contact())
                : getTranslation("blocked.review.nocontact")));
    }

    /** Only a blocked person has anything to read here; everyone else goes back into the app. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!BlockedAccess.isBlocked() && blocks.blockOf(BlockedAccess.login()).isEmpty()) {
            event.rerouteTo("");
        }
    }

    @Override
    public String getPageTitle() {
        return getTranslation("blocked.title") + " · Rootology";
    }
}
