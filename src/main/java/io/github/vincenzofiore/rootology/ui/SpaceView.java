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
import com.vaadin.flow.router.HasDynamicTitle;
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
@PermitAll
public class SpaceView extends VerticalLayout implements HasDynamicTitle {

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

        members.addColumn(Member::githubLogin).setHeader(getTranslation("space.login"));
        members.addColumn(m -> getTranslation("role." + m.role().name())).setHeader(getTranslation("space.role"))
                .setAutoWidth(true);
        if (owner) {
            members.addComponentColumn(m -> {
                        ComboBox<Role> roleBox = new ComboBox<>(null, Role.values());
                        roleBox.setItemLabelGenerator(r -> getTranslation("role." + r.name()));
                        roleBox.setValue(m.role());
                        roleBox.setWidth("9rem");
                        roleBox.addValueChangeListener(e -> {
                            if (e.isFromClient() && e.getValue() != null && e.getValue() != m.role()) {
                                act(() -> spaces.changeRole(space.id(), currentUser.login(), m.githubLogin(), e.getValue()));
                            }
                        });
                        return new HorizontalLayout(roleBox,
                                new Button(getTranslation("space.remove"), e -> act(() ->
                                        spaces.removeMember(space.id(), currentUser.login(), m.githubLogin()))));
                    })
                    .setHeader(getTranslation("space.actions")).setAutoWidth(true);
        }
        members.setAllRowsVisible(true);
        add(new H3(space.name()), new Paragraph(getTranslation("space.yourrole", getTranslation("role." + space.role().name()))),
                members);

        if (!space.personal()) {
            add(new Button(getTranslation("space.leave"), e -> {
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
        ComboBox<Role> role = new ComboBox<>(getTranslation("space.role"), Role.values());
        role.setItemLabelGenerator(r -> getTranslation("role." + r.name()));
        role.setValue(Role.MEMBER);
        TextField email = new TextField(getTranslation("space.invite.email"));
        email.setWidth("18rem");
        TextField onlyFor = new TextField(getTranslation("space.invite.login"));
        TextField link = new TextField(getTranslation("space.invite.link"));
        link.setReadOnly(true);
        link.setWidth("40rem");
        link.setVisible(false);
        Button create = new Button(getTranslation("space.invite.create"), e -> act(() -> {
            String token = invitations.create(space.id(), currentUser.login(), role.getValue(), onlyFor.getValue(),
                    email.getValue());
            if (!email.getValue().isBlank()) {
                Notification.show(getTranslation("space.invite.sent", email.getValue().trim()));
            }
            email.clear();
            link.setValue(ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/" + InvitationView.ROUTE + "/" + token).toUriString());
            link.setVisible(true);
            onlyFor.clear();
        }));
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
        pending.addColumn(i -> DAY.format(i.expiresAt())).setHeader(getTranslation("space.invite.expires")).setAutoWidth(true);
        pending.addComponentColumn(i -> new Button(getTranslation("space.invite.revoke"), e -> act(() ->
                invitations.revoke(space.id(), currentUser.login(), i.id())))).setAutoWidth(true);
        pending.setAllRowsVisible(true);

        return new VerticalLayout(new H4(getTranslation("space.invitations")),
                new Paragraph(getTranslation("space.invitations.intro")),
                form, link, new H4(getTranslation("space.invitations.pending")), pending);
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

    @Override
    public String getPageTitle() {
        return getTranslation("space.title") + " · Rootology";
    }
}
