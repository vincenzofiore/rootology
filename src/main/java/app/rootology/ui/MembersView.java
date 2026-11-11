package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import app.rootology.spaces.Member;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;

/**
 * The members of one space (#15, #56): their roles, and for owners the way to change them or remove
 * someone. Any member may leave a shared space; nobody leaves their own personal space.
 */
@Route(value = "spaces/:spaceId/members", layout = MainLayout.class)
@PermitAll
public class MembersView extends VerticalLayout implements HasDynamicTitle, BeforeEnterObserver {

    private final SpaceService spaces;
    private final CurrentUser currentUser;
    private final H3 title = new H3();
    private final Paragraph yourRole = new Paragraph();
    private final Grid<Member> members = new Grid<>();
    /** Changing roles and removing someone need an OWNER (#15): the column shows for them only. */
    private final Grid.Column<Member> actions;
    private final Button leave = new Button(getTranslation("space.leave"));
    /** The page's own space (#56), read from its route. */
    private long spaceId;

    public MembersView(SpaceService spaces, CurrentUser currentUser) {
        this.spaces = spaces;
        this.currentUser = currentUser;
        members.addColumn(Member::githubLogin).setHeader(getTranslation("space.login"));
        members.addColumn(m -> getTranslation("role." + m.role().name())).setHeader(getTranslation("space.role"))
                .setAutoWidth(true);
        actions = members.addComponentColumn(this::changeRole).setHeader(getTranslation("space.actions"))
                .setAutoWidth(true);
        actions.setVisible(false);
        members.setAllRowsVisible(true);
        leave.addClickListener(e -> {
            act(() -> spaces.removeMember(spaceId, currentUser.login(), currentUser.login()));
            getUI().ifPresent(ui -> ui.getPage().reload());
        });
        leave.setVisible(false);
        add(title, yourRole, members, leave);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        java.util.Optional<Routed.Where> where = Routed.space(event, currentUser);
        if (where.isEmpty()) {
            return;
        }
        spaceId = where.get().spaceId();
        SpaceSummary space = currentUser.spaces().stream().filter(sp -> sp.id() == spaceId).findFirst().orElseThrow();
        title.setText(space.name());
        yourRole.setText(getTranslation("space.yourrole", getTranslation("role." + space.role().name())));
        leave.setVisible(!space.personal());
        actions.setVisible(space.role() == Role.OWNER);
        refresh();
    }

    private HorizontalLayout changeRole(Member member) {
        ComboBox<Role> role = new ComboBox<>(null, Role.values());
        role.setItemLabelGenerator(r -> getTranslation("role." + r.name()));
        role.setValue(member.role());
        role.setWidth("9rem");
        role.addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null && e.getValue() != member.role()) {
                act(() -> spaces.changeRole(spaceId, currentUser.login(), member.githubLogin(), e.getValue()));
            }
        });
        return new HorizontalLayout(role, new Button(getTranslation("space.remove"), e -> act(() ->
                spaces.removeMember(spaceId, currentUser.login(), member.githubLogin()))));
    }

    /** Runs an action, shows its error if any, and refreshes the list. */
    private void act(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
        refresh();
    }

    private void refresh() {
        members.setItems(spaceId == 0 ? java.util.List.of() : spaces.membersOf(spaceId, currentUser.login()));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("space.title") + " · Rootology";
    }
}
