package io.github.vincenzofiore.rootology.ui;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowListOAuth2UserServiceTest {

    private static OAuth2User githubUser(String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"), Map.of("login", login), "login");
    }

    @Test
    void letsAllowListedUsersIn() {
        var service = new AllowListOAuth2UserService(Set.of("alice"));

        assertThat(service.requireAllowed(githubUser("alice")).getName()).isEqualTo("alice");
    }

    @Test
    void refusesEveryoneElse() {
        var service = new AllowListOAuth2UserService(Set.of("alice"));

        assertThatThrownBy(() -> service.requireAllowed(githubUser("mallory")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void anEmptyAllowListLetsNobodyIn() {
        var service = new AllowListOAuth2UserService(Set.of());

        assertThatThrownBy(() -> service.requireAllowed(githubUser("alice")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
