package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import io.github.vincenzofiore.rootology.roles.AppRole;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The signed-in person and the space they are working in, for one browser session. The only
 * place the UI learns who is asking; views never read the login on their own.
 */
@Component
@VaadinSessionScope
public class CurrentUser {

    private final SpaceService spaces;
    private final String login;
    private long spaceId;

    @Autowired
    CurrentUser(SpaceService spaces, AuthenticationContext authentication) {
        this(spaces, authentication.getAuthenticatedUser(OAuth2User.class)
                .map(user -> String.valueOf(user.<Object>getAttribute("login")))
                .orElseThrow(() -> new IllegalStateException("No signed-in user")));
    }

    CurrentUser(SpaceService spaces, String login) {
        this.spaces = spaces;
        this.login = SpaceService.normalizeLogin(login);
        spaces.signedIn(this.login);
        this.spaceId = spaces.spacesOf(login).getFirst().id();
    }

    public String login() {
        return login;
    }

    /** An app role (#32) of the signed-in person, from the authorities given at sign-in. */
    public boolean hasRole(AppRole role) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role.authority()));
    }

    /**
     * The current space, re-read so a role change shows up. If the person was removed from it,
     * falls back to their first space (their personal space).
     */
    /** False for a GUEST of the current space (#33): they look, and every change is hidden from them. */
    public boolean canContribute() {
        return space().role().contributes();
    }

    public SpaceSummary space() {
        List<SpaceSummary> mine = spaces();
        return mine.stream().filter(s -> s.id() == spaceId).findFirst().orElseGet(() -> {
            SpaceSummary first = mine.getFirst();
            spaceId = first.id();
            return first;
        });
    }

    public List<SpaceSummary> spaces() {
        return spaces.spacesOf(login);
    }

    public void switchTo(long newSpaceId) {
        if (!spaces.isMember(newSpaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + newSpaceId);
        }
        this.spaceId = newSpaceId;
    }

    /** What a pipeline run started now belongs to. */
    public RunContext runContext() {
        return new RunContext(space().id(), login);
    }
}
