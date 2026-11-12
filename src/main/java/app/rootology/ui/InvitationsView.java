package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.InvitationSummary;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;


/**
 * The invitations of one space (#23, #56): an owner creates single-use links, by email or for one login,
 * and revokes the pending ones. Nobody else sees the page.
 */
@Route(value = "spaces/:spaceId/invitations", layout = MainLayout.class)
@PermitAll
public class InvitationsView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final InvitationService invitations;
    private final CurrentUser currentUser;
    private final Grid<InvitationSummary> pending = new Grid<>();
    private final ComboBox<Role> role = new ComboBox<>(getTranslation("space.role"), Role.values());
    private final TextField email = new TextField(getTranslation("space.invite.email"));
    private final TextField onlyFor = new TextField(getTranslation("space.invite.login"));
    private final TextField link = new TextField(getTranslation("space.invite.link"));
    /** The page's own space (#56), read from its route. */
    private long spaceId;

    public InvitationsView(InvitationService invitations, CurrentUser currentUser) {
        this.invitations = invitations;
        this.currentUser = currentUser;
        role.setItemLabelGenerator(r -> getTranslation("role." + r.name()));
        role.setValue(Role.MEMBER);
        email.setWidth("18rem");
        link.setReadOnly(true);
        link.setWidth("40rem");
        link.setVisible(false);
        Button create = new Button(getTranslation("space.invite.create"), e -> create());
        create.addThemeName("primary");
        HorizontalLayout form = new HorizontalLayout(role, email, onlyFor, create);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        pending.addColumn(i -> getTranslation("role." + i.role().name())).setHeader(getTranslation("space.role"))
                .setAutoWidth(true);
        pending.addColumn(i -> i.invitedEmail() != null ? i.invitedEmail()
                : i.invitedLogin() != null ? i.invitedLogin() : getTranslation("space.invite.anyone"))
                .setHeader(getTranslation("space.invite.for"));
        pending.addColumn(InvitationSummary::createdBy).setHeader(getTranslation("space.invite.by"));
        pending.addComponentColumn(i -> DateText.dateSpan(i.expiresAt().atZone(DateText.ITALY).toLocalDate(), this))
                .setHeader(getTranslation("space.invite.expires")).setAutoWidth(true);
        pending.addComponentColumn(i -> new Button(getTranslation("space.invite.revoke"), e -> act(() ->
                invitations.revoke(spaceId, currentUser.login(), i.id())))).setAutoWidth(true);
        pending.setAllRowsVisible(true);

        add(new H3(getTranslation("space.invitations")), new Paragraph(getTranslation("space.invitations.intro")),
                form, link, new H4(getTranslation("space.invitations.pending")), pending);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.space(event, currentUser);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        SpaceSummary space = currentUser.spaces().stream().filter(sp -> sp.id() == spaceId).findFirst().orElseThrow();
        if (space.role() != Role.OWNER) {
            // Invitations are an owner's business: for anyone else the page does not exist (#56).
            event.rerouteToError(NotFoundException.class);
            return;
        }
        refresh();
    }

    private void create() {
        act(() -> {
            String token = invitations.create(spaceId, currentUser.login(), role.getValue(), onlyFor.getValue(),
                    email.getValue());
            if (!email.getValue().isBlank()) {
                Notification.show(getTranslation("space.invite.sent", email.getValue().trim()));
            }
            email.clear();
            link.setValue(ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/" + InvitationView.ROUTE + "/" + token).toUriString());
            link.setVisible(true);
            onlyFor.clear();
        });
    }

    /** Runs an action, shows its error if any, and refreshes the list. */
    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
        refresh();
    }

    private void refresh() {
        pending.setItems(spaceId == 0 ? java.util.List.of() : invitations.pending(spaceId, currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("space.invitations") + " · Rootology";
    }
}
