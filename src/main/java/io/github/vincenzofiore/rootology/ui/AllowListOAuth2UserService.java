package io.github.vincenzofiore.rootology.ui;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Set;

/**
 * Loads the GitHub user and refuses anyone not on the allow-list. Without it, any GitHub
 * account could sign in. An empty allow-list lets nobody in.
 */
class AllowListOAuth2UserService extends DefaultOAuth2UserService {

    private final Set<String> allowedLogins;

    AllowListOAuth2UserService(Set<String> allowedLogins) {
        this.allowedLogins = Set.copyOf(allowedLogins);
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        return requireAllowed(super.loadUser(request));
    }

    OAuth2User requireAllowed(OAuth2User user) {
        Object login = user.getAttribute("login");
        if (login == null || !allowedLogins.contains(login.toString())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"),
                    "GitHub account " + login + " is not allowed");
        }
        return user;
    }
}
