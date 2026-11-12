package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;

/**
 * "Sign in" at the top right of the public pages (#87): it opens the ways in (GitHub, and Google when it is
 * configured), and tells whoever has no access yet that signing in leads to the invitation request (#65).
 */
class SignInMenu extends Div {

    SignInMenu(boolean googleConfigured) {
        Button open = new Button(getTranslation("signin.menu"), VaadinIcon.SIGN_IN.create());
        open.addThemeVariants(ButtonVariant.PRIMARY);
        open.addClassName("rootology-signin-open");

        SignInButtons ways = new SignInButtons(getTranslation("signin.github"), getTranslation("signin.google"),
                googleConfigured);
        ways.addClassName("rootology-signin-menu-ways");
        Popover popover = new Popover();
        Paragraph unregistered = new Paragraph(getTranslation("signin.menu.unregistered"));
        unregistered.addClassName("rootology-signin-menu-request");

        popover.setTarget(open);
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.addThemeVariants(PopoverVariant.ARROW);
        popover.setAutofocus(true);
        popover.setAriaLabel(getTranslation("signin.menu"));
        popover.add(ways, unregistered);
        popover.addClassName("rootology-signin-menu");
        add(open, popover);
        addClassName("rootology-signin-menu-root");
    }
}
