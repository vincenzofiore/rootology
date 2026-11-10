package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.github.vincenzofiore.rootology.privacy.PrivacyContact;
import io.github.vincenzofiore.rootology.privacy.Retention;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

/**
 * The privacy notice (#40), public like the sources: who is responsible, what is kept and why, who
 * receives it, for how long, and the person's rights. The inventory behind it is docs/privacy.md;
 * retention periods come from the same settings the nightly job applies.
 */
@Route(value = PrivacyView.ROUTE, layout = PublicLayout.class)
@AnonymousAllowed
public class PrivacyView extends Div implements HasDynamicTitle {

    static final String ROUTE = "privacy";

    public PrivacyView(PrivacyContact contact, Retention retention,
                       @Value("${server.servlet.session.timeout:30m}") Duration sessionTimeout) {
        // A text column (styles/rootology.css): readable line length on wide screens, full width on a phone,
        // and the spacing of a document rather than a layout's even gaps.
        addClassName("rootology-prose");
        add(new H2(getTranslation("privacy.title")));

        add(new H3(getTranslation("privacy.who.title")));
        add(new Paragraph(contact.configured()
                ? getTranslation("privacy.who.text", contact.controller(), contact.contact())
                : getTranslation("privacy.who.missing")));

        add(new H3(getTranslation("privacy.what.title")), list(
                "privacy.what.account", "privacy.what.garden", "privacy.what.questions",
                "privacy.what.notifications", "privacy.what.usage", "privacy.what.proposals"));
        add(new Paragraph(getTranslation("privacy.what.nothingelse")));

        add(new H3(getTranslation("privacy.why.title")), new Paragraph(getTranslation("privacy.why.text")));

        add(new H3(getTranslation("privacy.who-receives.title")), list(
                "privacy.receives.hosting", "privacy.receives.anthropic", "privacy.receives.typesafe", "privacy.receives.github",
                "privacy.receives.google", "privacy.receives.mail", "privacy.receives.places", "privacy.receives.weather"));
        add(new Paragraph(getTranslation("privacy.receives.transfers")));
        add(new Paragraph(getTranslation("privacy.receives.questions")));

        add(new H3(getTranslation("privacy.kept.title")));
        UnorderedList kept = new UnorderedList(
                new ListItem(getTranslation("privacy.kept.history", days(retention.history()))),
                new ListItem(getTranslation("privacy.kept.escalations", days(retention.escalations()))),
                new ListItem(getTranslation("privacy.kept.notifications", days(retention.notifications()))),
                new ListItem(getTranslation("privacy.kept.usage", days(retention.usage()))),
                new ListItem(getTranslation("privacy.kept.invitations", days(retention.invitations()))),
                new ListItem(getTranslation("privacy.kept.weather")),
                new ListItem(getTranslation("privacy.kept.account")));
        add(kept);

        add(new H3(getTranslation("privacy.rights.title")), new Paragraph(getTranslation("privacy.rights.text")),
                new Paragraph(getTranslation("privacy.rights.complaint")));

        add(new H3(getTranslation("privacy.cookies.title")), new Paragraph(getTranslation("privacy.cookies.text")));
        add(new UnorderedList(
                new ListItem(getTranslation("privacy.cookies.session", sessionTimeout.toMinutes())),
                new ListItem(getTranslation("privacy.cookies.theme")),
                new ListItem(getTranslation("privacy.cookies.providers"))));
    }

    private UnorderedList list(String... keys) {
        UnorderedList list = new UnorderedList();
        for (String key : keys) {
            list.add(new ListItem(getTranslation(key)));
        }
        return list;
    }

    private static long days(Duration duration) {
        return duration.toDays();
    }

    @Override
    public String getPageTitle() {
        return getTranslation("privacy.title") + " · Rootology";
    }
}
