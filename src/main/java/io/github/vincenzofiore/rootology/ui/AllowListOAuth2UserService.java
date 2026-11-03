package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the GitHub user and refuses anyone not on the allow-list. Without it, any GitHub
 * account could sign in. An empty allow-list lets nobody in.
 */
class AllowListOAuth2UserService extends DefaultOAuth2UserService {

    private final Set<String> allowedLogins;
    private final SpaceService spaces;

    AllowListOAuth2UserService(Set<String> allowedLogins, SpaceService spaces) {
        this.allowedLogins = allowedLogins.stream().map(SpaceService::normalizeLogin).collect(Collectors.toSet());
        this.spaces = spaces;
    }

    /** Allowed users are registered on sign-in: the first time, they get their personal space. */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = requireAllowed(super.loadUser(request));
        spaces.signedIn(String.valueOf(user.<Object>getAttribute("login")));
        return user;
    }

    OAuth2User requireAllowed(OAuth2User user) {
        Object login = user.getAttribute("login");
        if (login == null || !allowedLogins.contains(SpaceService.normalizeLogin(login.toString()))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"),
                    "GitHub account " + login + " is not allowed");
        }
        return user;
    }
}
