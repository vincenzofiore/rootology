package app.rootology.ui;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import com.vaadin.flow.spring.security.AuthenticationContext;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.user.OAuth2User;
import app.rootology.roles.AppRole;
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
    private final boolean pending;
    private long spaceId;

    @Autowired
    CurrentUser(SpaceService spaces, AuthenticationContext authentication) {
        this(spaces, authentication.getAuthenticatedUser(OAuth2User.class)
                .map(user -> user.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE))
                .orElseThrow(() -> new IllegalStateException("No signed-in user")),
                PendingAccess.isPending());
    }

    CurrentUser(SpaceService spaces, String login) {
        this(spaces, login, false);
    }

    /**
     * A pending person (#65) signed in but is not admitted: nothing is written for them, and they are a
     * member of nothing. They only ever reach their request page, which does not use this class.
     */
    private CurrentUser(SpaceService spaces, String login, boolean pending) {
        this.spaces = spaces;
        this.login = SpaceService.normalizeLogin(login);
        this.pending = pending;
        if (!pending) {
            spaces.signedIn(this.login);
            this.spaceId = spaces.spacesOf(login).getFirst().id();
        }
    }

    /** True for someone signed in but not admitted (#65): no space, no role, nothing to see. */
    public boolean pending() {
        return pending;
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
    /**
     * False for a GUEST of that space (#33): they look, and every change is hidden from them. Takes the
     * space of the page, read from its route (#56): the session's space may be another, in another tab.
     */
    public boolean canContribute(long spaceId) {
        return !pending && spaces.canContribute(spaceId, login);
    }

    public SpaceSummary space() {
        List<SpaceSummary> mine = spaces();
        if (mine.isEmpty()) {
            throw new SpaceAccessException(login + " belongs to no space");
        }
        return mine.stream().filter(s -> s.id() == spaceId).findFirst().orElseGet(() -> {
            SpaceSummary first = mine.getFirst();
            spaceId = first.id();
            return first;
        });
    }

    public List<SpaceSummary> spaces() {
        return pending ? List.of() : spaces.spacesOf(login);
    }

    /** True when the person belongs to the space; a page checks its route's space first (#56). */
    public boolean memberOf(long spaceId) {
        return !pending && spaces.isMember(spaceId, login);
    }

    public void switchTo(long newSpaceId) {
        if (pending || !spaces.isMember(newSpaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + newSpaceId);
        }
        this.spaceId = newSpaceId;
    }

    /**
     * What a pipeline run started from a page belongs to (#56). The space and garden come from the page's
     * route, never from this session: every browser tab shares the session, and two tabs on two gardens
     * must not mix.
     */
    public RunContext runContext(long spaceId, long gardenId) {
        return new RunContext(spaceId, gardenId, login);
    }
}
