package app.rootology.ui;

import app.rootology.TestcontainersConfiguration;
import app.rootology.garden.GardenService;
import app.rootology.notifications.NotificationService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A person who signed in but is not admitted (#65, OWASP A01): they may only open their request page and the
 * public pages, and every other route sends them back there. Nothing was written for them, and the services
 * treat them as a stranger, since they belong to no space.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PendingAccessTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    IdentityService identities;

    @Autowired
    GardenService gardens;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    NotificationService notifications;

    @Autowired
    AllowListOAuth2UserService signIn;

    @Autowired
    AccessRouting routing;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** A GitHub account nobody admitted: the service hands back a pending principal and writes nothing. */
    private OAuth2User pending(String login, long id) {
        OAuth2User principal = signIn.signIn(new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                        AuthorityUtils.createAuthorityList("OAUTH2_USER"), java.util.Map.of("id", id, "login", login),
                        "id"),
                GitHubEmails.Verified.NONE, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        return principal;
    }

    @Test
    void aPendingPersonIsSentToTheRequestPageFromEveryOtherRoute() {
        pending("pending-routing", 900_001L);
        assertThat(jdbc.sql("select count(*) from app_user where login = 'pending-routing'").query(Long.class).single())
                .as("nothing is written before admission").isZero();
        assertThat(jdbc.sql("select count(*) from user_identity where subject = '900001'").query(Long.class).single())
                .isZero();

        for (Class<?> target : List.of(StartView.class, GardensView.class, AdminView.class, AccountView.class,
                NotificationsView.class, ComparisonView.class, CuratorView.class)) {
            var event = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
            when(event.getNavigationTarget()).thenReturn((Class) target);

            routing.beforeEnter(event);

            verify(event).rerouteTo(AccessRequestView.class);
        }
    }

    @Test
    void aPendingPersonReachesTheRequestPageThePublicPagesAndAnInvitation() {
        pending("pending-public", 900_002L);

        // An invitation too: a pending person accepts it by signing in again with it in the session.
        for (Class<?> target : List.of(AccessRequestView.class, SourcesView.class, PrivacyView.class,
                WelcomeView.class, LoggedOutView.class, InvitationView.class)) {
            var event = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
            when(event.getNavigationTarget()).thenReturn((Class) target);

            routing.beforeEnter(event);

            verify(event, never()).rerouteTo(AccessRequestView.class);
        }
    }

    @Test
    void anAdmittedPersonWhoOpensTheRequestPageIsSentHome() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("someone",
                null, AuthorityUtils.createAuthorityList("OAUTH2_USER")));
        var event = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
        when(event.getNavigationTarget()).thenReturn((Class) AccessRequestView.class);

        routing.beforeEnter(event);

        verify(event).rerouteTo("");
    }

    @Test
    void theServicesTreatAPendingPersonAsAStranger() {
        String login = "pending-stranger";
        pending(login, 900_003L);
        spaces.signedIn("pending-owner");
        long space = spaces.spacesOf("pending-owner").getFirst().id();

        assertThat(spaces.spacesOf(login)).isEmpty();
        assertThat(spaces.isMember(space, login)).isFalse();
        assertThatThrownBy(() -> gardens.gardens(space, login)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("pending-1", "Che cosa semino?"),
                new RunContext(space, 0, login))).isInstanceOf(RuntimeException.class);
        assertThat(notifications.of(login, 10)).isEmpty();
        assertThat(notifications.unread(login)).isZero();
    }
}
