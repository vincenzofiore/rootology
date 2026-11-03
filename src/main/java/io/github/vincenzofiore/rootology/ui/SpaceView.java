package io.github.vincenzofiore.rootology.ui;

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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.InvitationSummary;
import io.github.vincenzofiore.rootology.spaces.Member;
import io.github.vincenzofiore.rootology.spaces.Role;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The current space: its members and, for owners, invitations, removals and roles.
 * Any member may leave a shared space; nobody leaves their own personal space.
 */
@Route(value = "space", layout = MainLayout.class)
@PageTitle("Space · Rootology")
@PermitAll
public class SpaceView extends VerticalLayout {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final SpaceService spaces;
    private final InvitationService invitations;
    private final CurrentUser currentUser;
    private final Grid<Member> members = new Grid<>();
    private final Grid<InvitationSummary> pending = new Grid<>();

    public SpaceView(SpaceService spaces, InvitationService invitations, CurrentUser currentUser) {
        this.spaces = spaces;
        this.invitations = invitations;
        this.currentUser = currentUser;
        SpaceSummary space = currentUser.space();
        boolean owner = space.role() == Role.OWNER;

        members.addColumn(Member::githubLogin).setHeader("GitHub login");
        members.addColumn(m -> m.role().name()).setHeader("Role").setAutoWidth(true);
        if (owner) {
            members.addComponentColumn(m -> new HorizontalLayout(
                    new Button(m.role() == Role.OWNER ? "Make member" : "Make owner", e -> act(() ->
                            spaces.changeRole(space.id(), currentUser.login(), m.githubLogin(),
                                    m.role() == Role.OWNER ? Role.MEMBER : Role.OWNER))),
                    new Button("Remove", e -> act(() ->
                            spaces.removeMember(space.id(), currentUser.login(), m.githubLogin())))))
                    .setHeader("Actions").setAutoWidth(true);
        }
        members.setAllRowsVisible(true);
        add(new H3(space.name()), new Paragraph("Your role here: " + space.role()), members);

        if (!space.personal()) {
            add(new Button("Leave this space", e -> {
                act(() -> spaces.removeMember(space.id(), currentUser.login(), currentUser.login()));
                getUI().ifPresent(ui -> ui.getPage().reload());
            }));
        }

        if (owner) {
            add(invitationSection(space));
        }
        refresh();
    }

    private VerticalLayout invitationSection(SpaceSummary space) {
        ComboBox<Role> role = new ComboBox<>("Role", Role.values());
        role.setValue(Role.MEMBER);
        TextField onlyFor = new TextField("Only for this GitHub login (optional)");
        TextField link = new TextField("Invitation link (shown once)");
        link.setReadOnly(true);
        link.setWidth("40rem");
        link.setVisible(false);
        Button create = new Button("Create invitation link", e -> act(() -> {
            String token = invitations.create(space.id(), currentUser.login(), role.getValue(), onlyFor.getValue());
            link.setValue(ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/" + InvitationView.ROUTE + "/" + token).toUriString());
            link.setVisible(true);
            onlyFor.clear();
        }));
        create.addThemeName("primary");
        HorizontalLayout form = new HorizontalLayout(role, onlyFor, create);
        form.setDefaultVerticalComponentAlignment(Alignment.END);
        form.setWrap(true);

        pending.addColumn(i -> i.role().name()).setHeader("Role").setAutoWidth(true);
        pending.addColumn(i -> i.invitedLogin() == null ? "anyone with the link" : i.invitedLogin()).setHeader("For");
        pending.addColumn(InvitationSummary::createdBy).setHeader("Created by");
        pending.addColumn(i -> DAY.format(i.expiresAt())).setHeader("Expires").setAutoWidth(true);
        pending.addComponentColumn(i -> new Button("Revoke", e -> act(() ->
                invitations.revoke(space.id(), currentUser.login(), i.id())))).setAutoWidth(true);
        pending.setAllRowsVisible(true);

        return new VerticalLayout(new H4("Invitations"),
                new Paragraph("Share the link yourself: it works once and expires in 7 days."),
                form, link, new H4("Pending invitations"), pending);
    }

    /** Runs an action, shows its error if any, and refreshes the lists. */
    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
        refresh();
    }

    private void refresh() {
        SpaceSummary space = currentUser.space();
        members.setItems(spaces.membersOf(space.id(), currentUser.login()));
        if (space.role() == Role.OWNER) {
            pending.setItems(invitations.pending(space.id(), currentUser.login()));
        }
    }
}
