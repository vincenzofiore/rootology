package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllowListOAuth2UserServiceTest {

    private final SpaceService spaces = mock(SpaceService.class);
    private final InvitationService invitations = mock(InvitationService.class);
    private final io.github.vincenzofiore.rootology.roles.RoleService roles =
            mock(io.github.vincenzofiore.rootology.roles.RoleService.class);

    private static OAuth2User githubUser(String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"), Map.of("login", login), "login");
    }

    private AllowListOAuth2UserService service(String... allowed) {
        return new AllowListOAuth2UserService(Set.of(allowed), spaces, invitations, roles);
    }

    @Test
    void letsTheBootstrapAllowListIn() {
        assertThat(service("alice").requireAllowed(githubUser("alice"), null)).isEqualTo("alice");
    }

    @Test
    void ignoresTheCaseOfTheLogin() {
        assertThat(service("Alice").requireAllowed(githubUser("ALICE"), null)).isEqualTo("alice");
    }

    @Test
    void letsInAnyoneWhoAlreadyBelongsToASpace() {
        when(spaces.belongsToAnySpace("bob")).thenReturn(true);

        assertThat(service().requireAllowed(githubUser("bob"), null)).isEqualTo("bob");
    }

    @Test
    void letsInSomeoneWithAValidInvitation() {
        when(invitations.admits("token", "carol")).thenReturn(true);

        assertThat(service().requireAllowed(githubUser("carol"), "token")).isEqualTo("carol");
    }

    @Test
    void refusesEveryoneElse() {
        when(invitations.admits("token", "mallory")).thenReturn(false);

        assertThatThrownBy(() -> service("alice").requireAllowed(githubUser("mallory"), null))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> service("alice").requireAllowed(githubUser("mallory"), "token"))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void anEmptyAllowListLetsNobodyInOnItsOwn() {
        assertThatThrownBy(() -> service().requireAllowed(githubUser("alice"), null))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void appRolesBecomeAuthoritiesAtSignIn() {
        when(roles.rolesOf("alice")).thenReturn(java.util.EnumSet.of(io.github.vincenzofiore.rootology.roles.AppRole.QA));

        OAuth2User user = service("alice").withRoles(githubUser("alice"), "alice", "login");

        assertThat(user.getAuthorities()).extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("OAUTH2_USER", "ROLE_QA");
        assertThat(user.getName()).isEqualTo("alice");
    }
}
