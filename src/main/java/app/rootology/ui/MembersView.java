package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.InvitationSummary;
import app.rootology.spaces.Member;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.function.Consumer;

/**
 * The people of one space (#15, #56, #105): members as cards with their role, and for owners changing a role,
 * blocking and unblocking, inviting (the page header's main action) and the pending invitations, which used to
 * be a page of their own. Any member may leave a shared space; nobody leaves their own personal space. A change
 * of role, a block and a leave can carry a message, which reaches the other person as a notification.
 */
@Route(value = "spaces/:spaceId/members", layout = MainLayout.class)
@PermitAll
public class MembersView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final SpaceService spaces;
    private final InvitationService invitations;
    private final CurrentUser currentUser;
    private final Button invite = new Button(getTranslation("members.invite"), VaadinIcon.PLUS.create());
    private final PageHeader title = new PageHeader("", invite);
    private final Div members = new Div();
    private final H3 pendingTitle = new H3(getTranslation("space.invitations.pending"));
    private final Div pending = new Div();
    private final Paragraph noPending = new Paragraph(getTranslation("members.invitations.none"));
    private final Button leave = new Button(getTranslation("space.leave"));
    /** An owner renames the space here (#79): the personal space starts with the login alone. */
    private final TextField name = new TextField(getTranslation("space.name"));
    private final Button rename = new Button(getTranslation("space.rename"));
    private final HorizontalLayout renameRow = new HorizontalLayout();
    /** The page's own space (#56), read from its route. */
    private long spaceId;
    private boolean owner;

    public MembersView(SpaceService spaces, InvitationService invitations, CurrentUser currentUser) {
        this.spaces = spaces;
        this.invitations = invitations;
        this.currentUser = currentUser;
        invite.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        invite.addClickListener(e -> inviteDialog());
        invite.setVisible(false);
        members.addClassName("rootology-member-cards");
        pending.addClassName("rootology-member-cards");
        noPending.addClassName(LumoUtility.TextColor.SECONDARY);
        leave.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        leave.addClickListener(e -> leaveDialog());
        leave.setVisible(false);
        name.setMaxLength(SpaceService.MAX_NAME);
        rename.addClickListener(e -> rename());
        renameRow.add(name, rename);
        renameRow.setDefaultVerticalComponentAlignment(Alignment.END);
        renameRow.setWrap(true);
        renameRow.setVisible(false);
        add(title, members, leave, pendingTitle, pending, noPending, renameRow);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.space(event, currentUser);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        SpaceSummary space = currentUser.spaces().stream().filter(sp -> sp.id() == spaceId).findFirst().orElseThrow();
        title.setHeading(space.name());
        leave.setVisible(!space.personal());
        owner = space.role() == Role.OWNER;
        invite.setVisible(owner);
        renameRow.setVisible(owner);
        name.setValue(space.name());
        refresh();
    }

    /** One member: avatar, login, role, blocked, and the owner's actions on someone else. */
    private Component card(Member member) {
        boolean self = member.githubLogin().equals(currentUser.login());
        Div card = new Div();
        card.addClassName("rootology-member-card");
        if (member.blocked()) {
            card.addClassName("rootology-member-card-blocked");
        }
        Avatar avatar = new Avatar(member.githubLogin());
        avatar.addClassName("rootology-member-card-avatar");
        Div text = new Div();
        text.addClassName("rootology-member-card-text");
        Span login = new Span(self ? getTranslation("members.you", member.githubLogin()) : member.githubLogin());
        login.addClassName("rootology-member-card-name");
        Span role = new Span(getTranslation("role." + member.role().name()));
        role.addClassName("rootology-member-card-role");
        text.add(login, role);
        if (member.blocked()) {
            Span blocked = new Span(VaadinIcon.BAN.create(), new Span(getTranslation("members.blocked")));
            blocked.addClassName("rootology-member-card-blocked-badge");
            text.add(blocked);
        }
        card.add(avatar, text);
        if (owner && !self) {
            Div actions = new Div();
            actions.addClassName("rootology-member-card-actions");
            if (member.blocked()) {
                actions.add(RowActions.unblock(getTranslation("members.unblock"), e -> unblockDialog(member)));
            } else {
                actions.add(RowActions.edit(getTranslation("members.role.change"), e -> roleDialog(member)));
                if (member.role() != Role.OWNER) {
                    actions.add(RowActions.block(getTranslation("members.block"), e -> blockDialog(member)));
                }
            }
            card.add(actions);
        }
        return card;
    }

    /** A pending invitation: for whom, with which role, who created it and until when; revoked by its owner. */
    private Component card(InvitationSummary invitation) {
        Div card = new Div();
        card.addClassName("rootology-member-card");
        Div text = new Div();
        text.addClassName("rootology-member-card-text");
        Span forWhom = new Span(invitation.invitedEmail() != null ? invitation.invitedEmail()
                : invitation.invitedLogin() != null ? invitation.invitedLogin()
                : getTranslation("space.invite.anyone"));
        forWhom.addClassName("rootology-member-card-name");
        Span details = new Span(getTranslation("members.invitation.details",
                getTranslation("role." + invitation.role().name()), invitation.createdBy()));
        details.addClassName("rootology-member-card-role");
        Span expires = new Span(new Span(getTranslation("space.invite.expires") + " "),
                DateText.dateSpan(invitation.expiresAt().atZone(DateText.ITALY).toLocalDate(), this));
        expires.addClassName("rootology-member-card-role");
        text.add(forWhom, details, expires);
        Div actions = new Div();
        actions.addClassName("rootology-member-card-actions");
        actions.add(RowActions.delete(getTranslation("space.invite.revoke"), e -> RowActions.confirmDelete(
                getTranslation("members.revoke.title"), getTranslation("members.revoke.text", forWhom.getText()), null,
                getTranslation("space.invite.revoke"), getTranslation("common.cancel"),
                () -> act(() -> invitations.revoke(spaceId, currentUser.login(), invitation.id())))));
        card.add(text, actions);
        return card;
    }

    private void roleDialog(Member member) {
        ComboBox<Role> role = new ComboBox<>(getTranslation("space.role"), Role.values());
        role.setItemLabelGenerator(r -> getTranslation("role." + r.name()));
        role.setValue(member.role());
        role.setWidthFull();
        messageDialog(getTranslation("members.role.title", member.githubLogin()), null, role,
                getTranslation("common.save"), false,
                message -> spaces.changeRole(spaceId, currentUser.login(), member.githubLogin(), role.getValue(),
                        message));
    }

    private void blockDialog(Member member) {
        messageDialog(getTranslation("members.block.title", member.githubLogin()),
                getTranslation("members.block.text"), null, getTranslation("members.block"), true,
                message -> spaces.block(spaceId, currentUser.login(), member.githubLogin(), message));
    }

    private void unblockDialog(Member member) {
        Dialog dialog = new Dialog(getTranslation("members.unblock.title", member.githubLogin()));
        dialog.add(new Paragraph(getTranslation("members.unblock.text")));
        Button confirm = new Button(getTranslation("members.unblock"), e -> {
            dialog.close();
            act(() -> spaces.unblock(spaceId, currentUser.login(), member.githubLogin()));
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    private void leaveDialog() {
        messageDialog(getTranslation("members.leave.title"), getTranslation("members.leave.text"), null,
                getTranslation("space.leave"), true, message -> {
                    spaces.leave(spaceId, currentUser.login(), message);
                    // The space is no longer the person's: start again from their own.
                    getUI().ifPresent(ui -> ui.navigate(""));
                });
    }

    /**
     * A change of membership with an optional message for the other person (#105): what it does, an optional
     * field (a role), the message, and the button that does it (red when it takes something away).
     */
    private void messageDialog(String heading, String text, Component field, String confirmLabel, boolean taking,
                               Consumer<String> action) {
        Dialog dialog = new Dialog(heading);
        dialog.addClassName("rootology-member-dialog");
        if (text != null) {
            dialog.add(new Paragraph(text));
        }
        if (field != null) {
            dialog.add(field);
        }
        TextArea message = new TextArea(getTranslation("members.message"));
        message.setHelperText(getTranslation("members.message.help"));
        message.setMaxLength(SpaceService.MAX_MESSAGE);
        message.setWidthFull();
        dialog.add(message);
        Button confirm = new Button(confirmLabel, e -> {
            dialog.close();
            act(() -> action.accept(message.getValue()));
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (taking) {
            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }
        dialog.getFooter().add(new Button(getTranslation("common.cancel"), e -> dialog.close()), confirm);
        dialog.open();
    }

    /** The invitation form in a dialog (#105): the link shows there once, ready to copy. */
    private void inviteDialog() {
        Dialog dialog = new Dialog(getTranslation("members.invite.title"));
        dialog.addClassName("rootology-member-dialog");
        ComboBox<Role> role = new ComboBox<>(getTranslation("space.role"), Role.values());
        role.setItemLabelGenerator(r -> getTranslation("role." + r.name()));
        role.setValue(Role.MEMBER);
        TextField email = new TextField(getTranslation("space.invite.email"));
        TextField onlyFor = new TextField(getTranslation("space.invite.login"));
        TextField link = new TextField(getTranslation("space.invite.link"));
        link.setReadOnly(true);
        link.setVisible(false);
        for (Component c : List.of(role, email, onlyFor, link)) {
            ((com.vaadin.flow.component.HasSize) c).setWidthFull();
        }
        Paragraph intro = new Paragraph(getTranslation("space.invitations.intro"));
        intro.addClassName(LumoUtility.TextColor.SECONDARY);
        dialog.add(intro, role, email, onlyFor, link);
        Button create = new Button(getTranslation("space.invite.create"));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button close = new Button(getTranslation("common.cancel"), e -> dialog.close());
        create.addClickListener(e -> {
            try {
                String token = invitations.create(spaceId, currentUser.login(), role.getValue(), onlyFor.getValue(),
                        email.getValue());
                if (!email.getValue().isBlank()) {
                    Toasts.done(getTranslation("space.invite.sent", email.getValue().trim()));
                }
                link.setValue(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/" + InvitationView.ROUTE + "/" + token).toUriString());
                link.setVisible(true);
                create.setVisible(false);
                close.setText(getTranslation("common.close"));
                refresh();
            } catch (RuntimeException ex) {
                UiErrors.show(this, ex);
            }
        });
        dialog.getFooter().add(close, create);
        dialog.open();
    }

    /** Runs an action, shows its error if any, and refreshes the list. */
    private void act(Runnable action) {
        try {
            action.run();
            Toasts.done(getTranslation("common.saved"));
        } catch (IllegalArgumentException ex) {
            Toasts.problem(getTranslation("members.message.invalid", SpaceService.MAX_MESSAGE));
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
        refresh();
    }

    /** A name the person can use: the service checks it again, this only says why it was refused (#79). */
    private void rename() {
        try {
            spaces.rename(spaceId, currentUser.login(), name.getValue());
            Toasts.done(getTranslation("common.saved"));
            // The switcher and the title of every page carry the name: start again with the new one.
            getUI().ifPresent(ui -> ui.getPage().reload());
        } catch (IllegalArgumentException ex) {
            Toasts.problem(getTranslation("space.rename.invalid", SpaceService.MAX_NAME));
        } catch (RuntimeException ex) {
            UiErrors.show(this, ex);
        }
    }

    private void refresh() {
        members.removeAll();
        pending.removeAll();
        if (spaceId == 0 || !currentUser.memberOf(spaceId)) {
            return;
        }
        spaces.membersOf(spaceId, currentUser.login()).forEach(m -> members.add(card(m)));
        List<InvitationSummary> open = owner ? invitations.pending(spaceId, currentUser.login()) : List.of();
        open.forEach(i -> pending.add(card(i)));
        pendingTitle.setVisible(owner);
        pending.setVisible(!open.isEmpty());
        noPending.setVisible(owner && open.isEmpty());
    }

    /** The members' logins as the page lists them, for the tests. */
    List<String> listed() {
        return members.getChildren().map(c -> c.getElement().getTextRecursively()).toList();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("space.title") + " · Rootology";
    }
}
