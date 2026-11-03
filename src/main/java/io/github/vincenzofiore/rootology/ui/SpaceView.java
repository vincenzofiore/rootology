package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.spaces.Member;
import io.github.vincenzofiore.rootology.spaces.Role;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import jakarta.annotation.security.PermitAll;

/** The current space and its members; an owner adds people by GitHub login. */
@Route(value = "space", layout = MainLayout.class)
@PageTitle("Space · Rootology")
@PermitAll
public class SpaceView extends VerticalLayout {

    private final SpaceService spaces;
    private final CurrentUser currentUser;
    private final Grid<Member> grid = new Grid<>();

    public SpaceView(SpaceService spaces, CurrentUser currentUser) {
        this.spaces = spaces;
        this.currentUser = currentUser;
        SpaceSummary space = currentUser.space();

        grid.addColumn(Member::githubLogin).setHeader("GitHub login");
        grid.addColumn(m -> m.role().name()).setHeader("Role").setAutoWidth(true);
        grid.setAllRowsVisible(true);
        add(new H3(space.name()), new Paragraph("Your role here: " + space.role()), grid);

        if (space.role() == Role.OWNER) {
            TextField login = new TextField("GitHub login");
            ComboBox<Role> role = new ComboBox<>("Role", Role.values());
            role.setValue(Role.MEMBER);
            Button add = new Button("Add member", e -> {
                try {
                    spaces.addMember(space.id(), currentUser.login(), login.getValue(), role.getValue());
                    login.clear();
                    refresh();
                } catch (RuntimeException ex) {
                    Notification.show("Could not add the member: " + ex.getMessage());
                }
            });
            add.addThemeName("primary");
            HorizontalLayout form = new HorizontalLayout(login, role, add);
            form.setDefaultVerticalComponentAlignment(Alignment.END);
            add(form, new Paragraph("The person must also be allowed to sign in to Rootology."));
        }
        refresh();
    }

    private void refresh() {
        grid.setItems(spaces.membersOf(currentUser.space().id(), currentUser.login()));
    }
}
