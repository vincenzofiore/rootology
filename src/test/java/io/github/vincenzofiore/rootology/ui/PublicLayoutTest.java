package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.router.BeforeEnterEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The bar of the public pages (#50): the welcome page shows no link back to itself for a visitor, while the
 * other public pages keep it.
 */
class PublicLayoutTest {

    private static Anchor backLink(PublicLayout layout) {
        return layout.getChildren().filter(Header.class::isInstance).map(Header.class::cast)
                .flatMap(Header::getChildren).filter(Anchor.class::isInstance).map(Anchor.class::cast)
                .filter(a -> a.getElement().getClassList().contains("rootology-public-back"))
                .findFirst().orElseThrow();
    }

    private static PublicLayout navigatedTo(Class<? extends com.vaadin.flow.component.Component> target) {
        PublicLayout layout = new PublicLayout();
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        doReturn(target).when(event).getNavigationTarget();
        layout.beforeEnter(event);
        return layout;
    }

    @Test
    void theWelcomePageHasNoBackLink() {
        assertThat(backLink(navigatedTo(WelcomeView.class)).isVisible()).isFalse();
    }

    @Test
    void thePrivacyAndSourcesPagesKeepTheBackLink() {
        assertThat(backLink(navigatedTo(PrivacyView.class)).isVisible()).isTrue();
        assertThat(backLink(navigatedTo(SourcesView.class)).isVisible()).isTrue();
    }

    /** A pending principal (#65): ROLE_PENDING alone. */
    private static void signInPending() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("someone", null,
                        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList(
                                PendingAccess.AUTHORITY)));
    }

    @org.junit.jupiter.api.AfterEach
    void signOut() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    /** Bug of 2026-11-11: "/" and the welcome page both led a pending person back to their request. */
    @Test
    void aPendingPersonsLinkSignsThemOutToTheWelcomePage() {
        signInPending();

        Anchor back = backLink(navigatedTo(AccessRequestView.class));
        signOut();
        String visitors = backLink(navigatedTo(PrivacyView.class)).getElement().getTextRecursively();

        assertThat(back.getHref()).isEqualTo("/" + WelcomeView.ROUTE);
        assertThat(back.getElement().getTextRecursively()).as("it says it signs out").isNotBlank().isNotEqualTo(visitors);
        assertThat(back.isVisible()).isTrue();
    }

    @Test
    void aPendingPersonReachesTheWelcomePage() {
        signInPending();
        var authentication = mock(com.vaadin.flow.spring.security.AuthenticationContext.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

        new WelcomeView(authentication, "not-set").beforeEnter(event);

        verify(event, never()).rerouteTo(any(Class.class));
    }

    @Test
    void anAdmittedPersonIsStillSentHomeFromTheWelcomePage() {
        var authentication = mock(com.vaadin.flow.spring.security.AuthenticationContext.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        new WelcomeView(authentication, "not-set").beforeEnter(event);

        verify(event).rerouteTo(StartView.class);
    }
}
