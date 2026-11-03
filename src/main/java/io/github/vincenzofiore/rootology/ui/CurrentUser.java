package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

    /** The current space, re-read so a role change shows up. */
    public SpaceSummary space() {
        return spaces().stream().filter(s -> s.id() == spaceId).findFirst()
                .orElseThrow(() -> new SpaceAccessException(login + " is no longer a member of space " + spaceId));
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
