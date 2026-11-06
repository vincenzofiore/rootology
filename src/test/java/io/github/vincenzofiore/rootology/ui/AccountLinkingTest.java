package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.RoleService;
import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.LinkedIdentity;
import io.github.vincenzofiore.rootology.spaces.Role;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Linking a second sign-in account from My account (#28), on a real database: only the person already signed in
 * can link, only their own marker is used, and nothing is ever merged or moved. Each test uses its own logins.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountLinkingTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    IdentityService identities;

    @Autowired
    InvitationService invitations;

    @Autowired
    RoleService roles;

    @Autowired
    NotificationService notifications;

    @Autowired
    JdbcClient jdbc;

    private MockHttpServletRequest request;

    @BeforeEach
    void aRequestWithASession() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void noRequestLeft() {
        RequestContextHolder.resetRequestAttributes();
    }

    private AllowListOAuth2UserService github(String... allowed) {
        return new AllowListOAuth2UserService(Set.of(allowed), spaces, identities, steps(), null);
    }

    private GoogleOidcUserService google(String... allowedEmails) {
        return new GoogleOidcUserService(Set.of(allowedEmails), steps(), spaces, identities, invitations);
    }

    private SignInSteps steps() {
        return new SignInSteps(spaces, identities, invitations, roles, notifications);
    }

    private static OAuth2User githubUser(long id, String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("id", id, "login", login), "id");
    }

    private static OidcUser googleUser(String sub, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("email", email);
        claims.put("email_verified", true);
        OidcIdToken idToken = new OidcIdToken("an-id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", sub));
        return new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken,
                new OidcUserInfo(claims), "sub");
    }

    private static String codeOf(OAuth2AuthenticationException refused) {
        return refused.getError().getErrorCode();
    }

    private List<IdentityProvider> providersOf(String login) {
        return identities.linkedTo(login).stream().map(LinkedIdentity::provider).toList();
    }

    private long identitiesOf(String login) {
        return jdbc.sql("select count(*) from user_identity i join app_user u on u.id = i.user_id "
                + "where u.login = :login").param("login", login).query(Long.class).single();
    }

    @Test
    void aGitHubPersonLinksGoogleAndLaterSignsInWithEither() {
        github("link-anna").signIn(githubUser(201, "link-anna"), GitHubEmails.Verified.NONE, null);
        jdbc.sql("""
                insert into app_role_grant (github_login, role, granted_by, granted_at)
                values ('link-anna', 'QA', 'link-anna', now())""").update();

        PendingLink.start("link-anna", IdentityProvider.GOOGLE);
        OidcUser linked = google().link(googleUser("google-201", "anna@example.org"), PendingLink.current());

        // The principal is the marker's person, with their login and their roles.
        assertThat(linked.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("link-anna");
        assertThat(linked.getName()).isEqualTo("link-anna");
        assertThat(linked.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_QA");
        assertThat(providersOf("link-anna")).containsExactlyInAnyOrder(IdentityProvider.GITHUB,
                IdentityProvider.GOOGLE);
        assertThat(identitiesOf("link-anna")).isEqualTo(2);

        // From now on Google alone enters the same person.
        OidcUser again = google().signIn(googleUser("google-201", "anna@example.org"), null);

        assertThat(again.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("link-anna");
        assertThat(again.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_QA");
        assertThat(identitiesOf("link-anna")).isEqualTo(2);
    }

    @Test
    void aGooglePersonLinksGitHubAndTheGitHubNameDoesNotBecomeTheirLogin() {
        google("greta@example.org").signIn(googleUser("google-202", "greta@example.org"), null);
        String login = identities.loginOf(IdentityProvider.GOOGLE, "google-202").orElseThrow();
        assertThat(login).isEqualTo("greta");

        PendingLink.start("greta", IdentityProvider.GITHUB);
        OAuth2User linked = github().link(githubUser(202, "greta-at-github"), GitHubEmails.Verified.NONE,
                PendingLink.current());

        assertThat(linked.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("greta");

        // The GitHub account now enters the Google person, under their internal login.
        OAuth2User again = github().signIn(githubUser(202, "greta-at-github"), GitHubEmails.Verified.NONE, null);

        assertThat(again.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("greta");
        assertThat(again.getName()).isEqualTo("greta");
        assertThat(jdbc.sql("select count(*) from app_user where login like 'greta%'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(providersOf("greta")).containsExactlyInAnyOrder(IdentityProvider.GOOGLE,
                IdentityProvider.GITHUB);
    }

    @Test
    void anAccountOfAnotherPersonIsRefusedAndNothingIsMoved() {
        google("owner@example.org").signIn(googleUser("google-203", "owner@example.org"), null);
        String ownerLogin = identities.loginOf(IdentityProvider.GOOGLE, "google-203").orElseThrow();
        github("link-bruno").signIn(githubUser(203, "link-bruno"), GitHubEmails.Verified.NONE, null);

        PendingLink.start("link-bruno", IdentityProvider.GOOGLE);
        assertThatThrownBy(() -> google().link(googleUser("google-203", "owner@example.org"), PendingLink.current()))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        refused -> assertThat(codeOf(refused)).isEqualTo("taken"));

        assertThat(providersOf("link-bruno")).containsExactly(IdentityProvider.GITHUB);
        assertThat(providersOf(ownerLogin)).containsExactly(IdentityProvider.GOOGLE);
        assertThat(identities.loginOf(IdentityProvider.GOOGLE, "google-203")).contains(ownerLogin);
        assertThat(jdbc.sql("select id from app_user where login = :login").param("login", ownerLogin)
                .query(Long.class).single()).isNotEqualTo(
                jdbc.sql("select id from app_user where login = 'link-bruno'").query(Long.class).single());
    }

    @Test
    void anotherAccountOfAProviderThePersonAlreadyHasIsRefused() {
        google("carla@example.org").signIn(googleUser("google-204", "carla@example.org"), null);
        PendingLink.start("carla", IdentityProvider.GITHUB);
        github().link(githubUser(204, "carla-at-github"), GitHubEmails.Verified.NONE, PendingLink.current());

        PendingLink.start("carla", IdentityProvider.GOOGLE);
        assertThatThrownBy(() -> google().link(googleUser("google-205", "other@example.org"), PendingLink.current()))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        refused -> assertThat(codeOf(refused)).isEqualTo("already-linked"));

        assertThat(identities.loginOf(IdentityProvider.GOOGLE, "google-205")).isEmpty();
        assertThat(identities.loginOf(IdentityProvider.GOOGLE, "google-204")).contains("carla");
        assertThat(identitiesOf("carla")).isEqualTo(2);
    }

    @Test
    void linkingTheSameAccountAgainSucceedsAndWritesNothing() {
        github("link-dino").signIn(githubUser(205, "link-dino"), GitHubEmails.Verified.NONE, null);

        PendingLink.start("link-dino", IdentityProvider.GOOGLE);
        google().link(googleUser("google-206", "dino@example.org"), PendingLink.current());
        PendingLink.start("link-dino", IdentityProvider.GOOGLE);
        OidcUser again = google().link(googleUser("google-206", "dino@example.org"), PendingLink.current());

        assertThat(again.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("link-dino");
        assertThat(providersOf("link-dino")).containsExactlyInAnyOrder(IdentityProvider.GITHUB,
                IdentityProvider.GOOGLE);
        assertThat(identitiesOf("link-dino")).isEqualTo(2);
    }

    @Test
    void anExpiredMarkerIsRefused() {
        github("link-emma").signIn(githubUser(206, "link-emma"), GitHubEmails.Verified.NONE, null);
        PendingLink.Link expired = new PendingLink.Link("link-emma", IdentityProvider.GOOGLE,
                Instant.now().minus(PendingLink.WINDOW).minus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> google().link(googleUser("google-207", "emma@example.org"), expired))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        refused -> assertThat(codeOf(refused)).isEqualTo("expired"));

        assertThat(providersOf("link-emma")).containsExactly(IdentityProvider.GITHUB);
    }

    @Test
    void aMarkerOfTheOtherProviderIsRefused() {
        github("link-fabio").signIn(githubUser(207, "link-fabio"), GitHubEmails.Verified.NONE, null);
        PendingLink.Link forGitHub = new PendingLink.Link("link-fabio", IdentityProvider.GITHUB, Instant.now());

        assertThatThrownBy(() -> google().link(googleUser("google-208", "fabio@example.org"), forGitHub))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        refused -> assertThat(codeOf(refused)).isEqualTo("expired"));

        assertThat(providersOf("link-fabio")).containsExactly(IdentityProvider.GITHUB);
    }

    @Test
    void aMarkerOfAPersonWhoIsGoneIsRefused() {
        PendingLink.Link gone = new PendingLink.Link("link-nobody-like-this", IdentityProvider.GOOGLE,
                Instant.now());

        assertThatThrownBy(() -> google().link(googleUser("google-209", "gone@example.org"), gone))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        refused -> assertThat(codeOf(refused)).isEqualTo("expired"));

        assertThat(identities.loginOf(IdentityProvider.GOOGLE, "google-209")).isEmpty();
        assertThat(identities.isKnownPerson("link-nobody-like-this")).isFalse();
    }

    @Test
    void theMarkerIsForgottenAfterSuccessAndAfterEveryRefusal() {
        github("link-gino").signIn(githubUser(208, "link-gino"), GitHubEmails.Verified.NONE, null);
        google("gino-owner@example.org").signIn(googleUser("google-210", "gino-owner@example.org"), null);

        // Success.
        PendingLink.start("link-gino", IdentityProvider.GOOGLE);
        google().link(googleUser("google-211", "gino@example.org"), PendingLink.current());
        assertThat(PendingLink.current()).isNull();
        assertThat(request.getSession().getAttribute(PendingLink.SESSION_KEY)).isNull();

        // Refusal.
        PendingLink.start("link-gino", IdentityProvider.GOOGLE);
        assertThatThrownBy(() -> google().link(googleUser("google-210", "gino-owner@example.org"),
                PendingLink.current())).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(PendingLink.current()).isNull();
        assertThat(request.getSession().getAttribute(PendingLink.SESSION_KEY)).isNull();

        // A sign-in after it is a plain sign-in.
        OidcUser plain = google().signIn(googleUser("google-211", "gino@example.org"), null);
        assertThat(plain.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("link-gino");
    }

    @Test
    void linkingAcceptsNoInvitationAndRunsNoAllowList() {
        github("link-ivan").signIn(githubUser(209, "link-ivan"), GitHubEmails.Verified.NONE, null);
        spaces.signedIn("link-owner");
        long shared = spaces.spacesOf("link-owner").getFirst().id();
        String token = invitations.create(shared, "link-owner", Role.MEMBER, null, "ivan@example.org");
        request.getSession().setAttribute(PendingInvitation.SESSION_KEY, token);

        // The Google account is on no allow-list, and its address matches the invitation: neither matters here.
        PendingLink.start("link-ivan", IdentityProvider.GOOGLE);
        OidcUser linked = google().link(googleUser("google-212", "ivan@example.org"), PendingLink.current());

        assertThat(linked.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("link-ivan");
        assertThat(jdbc.sql("select count(*) from space_member where space_id = :space")
                .param("space", shared).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from invitation where created_by = 'link-owner' and accepted_at is null")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(PendingInvitation.current()).isEqualTo(token);
    }

    @Test
    void anAccountCanBeUnlinkedOnlyWhileAnotherRemains() {
        google("lia@example.org").signIn(googleUser("google-213", "lia@example.org"), null);
        PendingLink.start("lia", IdentityProvider.GITHUB);
        github().link(githubUser(210, "lia-at-github"), GitHubEmails.Verified.NONE, PendingLink.current());

        identities.unlink("lia", IdentityProvider.GITHUB);
        assertThat(providersOf("lia")).containsExactly(IdentityProvider.GOOGLE);

        assertThatThrownBy(() -> identities.unlink("lia", IdentityProvider.GOOGLE))
                .isInstanceOf(SpaceAccessException.class);
        assertThat(providersOf("lia")).containsExactly(IdentityProvider.GOOGLE);

        assertThatThrownBy(() -> identities.unlink("lia", IdentityProvider.GITHUB))
                .isInstanceOf(SpaceAccessException.class);
    }
}
