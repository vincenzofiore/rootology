package app.rootology.ui;

import app.rootology.spaces.IdentityProvider;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllowListOAuth2UserServiceTest {

    private final SpaceService spaces = mock(SpaceService.class);
    private final IdentityService identities = mock(IdentityService.class);
    private final InvitationService invitations = mock(InvitationService.class);
    private final app.rootology.roles.RoleService roles =
            mock(app.rootology.roles.RoleService.class);
    private final app.rootology.notifications.NotificationService notifications =
            mock(app.rootology.notifications.NotificationService.class);
    private final app.rootology.access.AccessRequestService accessRequests =
            mock(app.rootology.access.AccessRequestService.class);

    private static OAuth2User githubUser(String id, String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("id", id, "login", login), "id");
    }

    private AllowListOAuth2UserService service(String... allowed) {
        return new AllowListOAuth2UserService(Set.of(allowed), spaces, identities,
                new SignInSteps(spaces, identities, invitations, roles, notifications, accessRequests), mock(GitHubEmails.class));
    }

    @Test
    void letsTheBootstrapAllowListIn() {
        assertThat(service("alice").requireAllowed("alice", null, Set.of(), "1")).isTrue();
    }

    @Test
    void letsInAnyoneWhoAlreadyBelongsToASpace() {
        when(spaces.belongsToAnySpace("bob")).thenReturn(true);

        assertThat(service().requireAllowed("bob", null, Set.of(), "2")).isTrue();
    }

    @Test
    void letsInSomeoneWithAValidInvitation() {
        when(invitations.admits("token", "carol", Set.of())).thenReturn(true);

        assertThat(service().requireAllowed("carol", "token", Set.of(), "3")).isTrue();
    }

    @Test
    void letsInAnIdentityAnAdminApproved() {
        when(accessRequests.isApproved(IdentityProvider.GITHUB, "4")).thenReturn(true);

        assertThat(service().requireAllowed("dora", null, Set.of(), "4")).isTrue();
    }

    @Test
    void refusesEveryoneElse() {
        when(invitations.admits("token", "mallory", Set.of())).thenReturn(false);

        assertThat(service("alice").requireAllowed("mallory", null, Set.of(), "5")).isFalse();
        assertThat(service("alice").requireAllowed("mallory", "token", Set.of(), "5")).isFalse();
    }

    @Test
    void anEmptyAllowListLetsNobodyInOnItsOwn() {
        assertThat(service().requireAllowed("alice", null, Set.of(), "6")).isFalse();
    }

    @Test
    void aKnownGitHubIdKeepsItsOwnLoginWhateverTheGitHubNameIs() {
        when(identities.loginOf(IdentityProvider.GITHUB, "1")).thenReturn(Optional.of("alice"));

        assertThat(service().resolveLogin("1", "alice-renamed")).isEqualTo("alice");
    }

    @Test
    void aFirstSignInKeepsTheGitHubLogin() {
        when(identities.loginOf(IdentityProvider.GITHUB, "7")).thenReturn(Optional.empty());
        when(identities.hasAnyIdentity("dave")).thenReturn(false);

        assertThat(service().resolveLogin("7", "dave")).isEqualTo("dave");
    }

    @Test
    void aFreedGitHubLoginHeldByAnotherIdIsRefused() {
        when(identities.loginOf(IdentityProvider.GITHUB, "2")).thenReturn(Optional.empty());
        when(identities.hasAnyIdentity("alice")).thenReturn(true);

        assertThatThrownBy(() -> service().resolveLogin("2", "alice"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void aLoginDerivedForAGooglePersonIsNotEnteredByGitHub() {
        when(identities.loginOf(IdentityProvider.GITHUB, "3")).thenReturn(Optional.empty());
        when(identities.hasAnyIdentity("mario")).thenReturn(true); // a Google person, not a GitHub one

        assertThatThrownBy(() -> service().resolveLogin("3", "mario"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void anAccountWithoutItsStableIdIsRefused() {
        OAuth2User noId = new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("login", "alice"), "login");

        assertThatThrownBy(() -> service("alice").signIn(noId, GitHubEmails.Verified.NONE, null))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void aLoginIsCaseInsensitive() {
        when(identities.loginOf(IdentityProvider.GITHUB, "9")).thenReturn(Optional.empty());
        when(identities.hasAnyIdentity("alice")).thenReturn(false);

        assertThat(service().resolveLogin("9", "ALICE")).isEqualTo("alice");
    }

    @Test
    void appRolesBecomeAuthoritiesAtSignIn() {
        when(roles.rolesOf("alice")).thenReturn(java.util.EnumSet.of(app.rootology.roles.AppRole.QA));

        OAuth2User user = service("alice").withRoles(githubUser("1", "alice"), "alice", Set.of("alice@example.org"));

        assertThat(user.getAuthorities()).extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("OAUTH2_USER", "ROLE_QA");
        assertThat(user.getName()).isEqualTo("alice");
        assertThat(user.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("alice");
        assertThat(user.<java.util.List<String>>getAttribute(SignedIn.VERIFIED_EMAILS_ATTRIBUTE)).containsExactly("alice@example.org");
    }
}
