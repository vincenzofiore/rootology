package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
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
    private final io.github.vincenzofiore.rootology.roles.RoleService roles =
            mock(io.github.vincenzofiore.rootology.roles.RoleService.class);

    private static OAuth2User githubUser(String id, String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("id", id, "login", login), "id");
    }

    private AllowListOAuth2UserService service(String... allowed) {
        return new AllowListOAuth2UserService(Set.of(allowed), spaces, identities, invitations, roles,
                mock(GitHubEmails.class), mock(io.github.vincenzofiore.rootology.notifications.NotificationService.class));
    }

    @Test
    void letsTheBootstrapAllowListIn() {
        assertThatCode(() -> service("alice").requireAllowed("alice", null, Set.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void letsInAnyoneWhoAlreadyBelongsToASpace() {
        when(spaces.belongsToAnySpace("bob")).thenReturn(true);

        assertThatCode(() -> service().requireAllowed("bob", null, Set.of())).doesNotThrowAnyException();
    }

    @Test
    void letsInSomeoneWithAValidInvitation() {
        when(invitations.admits("token", "carol", Set.of())).thenReturn(true);

        assertThatCode(() -> service().requireAllowed("carol", "token", Set.of())).doesNotThrowAnyException();
    }

    @Test
    void refusesEveryoneElse() {
        when(invitations.admits("token", "mallory", Set.of())).thenReturn(false);

        assertThatThrownBy(() -> service("alice").requireAllowed("mallory", null, Set.of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> service("alice").requireAllowed("mallory", "token", Set.of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void anEmptyAllowListLetsNobodyInOnItsOwn() {
        assertThatThrownBy(() -> service().requireAllowed("alice", null, Set.of()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void aKnownGitHubIdKeepsItsOwnLoginWhateverTheGitHubNameIs() {
        when(identities.loginOf(IdentityProvider.GITHUB, "1")).thenReturn(Optional.of("alice"));

        assertThat(service().resolveLogin("1", "alice-renamed")).isEqualTo("alice");
    }

    @Test
    void aFirstSignInKeepsTheGitHubLogin() {
        when(identities.loginOf(IdentityProvider.GITHUB, "7")).thenReturn(Optional.empty());
        when(identities.hasIdentity("dave", IdentityProvider.GITHUB)).thenReturn(false);

        assertThat(service().resolveLogin("7", "dave")).isEqualTo("dave");
    }

    @Test
    void aFreedGitHubLoginHeldByAnotherIdIsRefused() {
        when(identities.loginOf(IdentityProvider.GITHUB, "2")).thenReturn(Optional.empty());
        when(identities.hasIdentity("alice", IdentityProvider.GITHUB)).thenReturn(true);

        assertThatThrownBy(() -> service().resolveLogin("2", "alice"))
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
        when(identities.hasIdentity("alice", IdentityProvider.GITHUB)).thenReturn(false);

        assertThat(service().resolveLogin("9", "ALICE")).isEqualTo("alice");
    }

    @Test
    void appRolesBecomeAuthoritiesAtSignIn() {
        when(roles.rolesOf("alice")).thenReturn(java.util.EnumSet.of(io.github.vincenzofiore.rootology.roles.AppRole.QA));

        OAuth2User user = service("alice").withRoles(githubUser("1", "alice"), "alice", Set.of("alice@example.org"));

        assertThat(user.getAuthorities()).extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("OAUTH2_USER", "ROLE_QA");
        assertThat(user.getName()).isEqualTo("alice");
        assertThat(user.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("alice");
        assertThat(user.<java.util.List<String>>getAttribute(GitHubEmails.ATTRIBUTE)).containsExactly("alice@example.org");
    }
}
