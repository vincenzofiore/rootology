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
                PendingAccess.isPending() || BlockedAccess.isBlocked());
    }

    CurrentUser(SpaceService spaces, String login) {
        this(spaces, login, false);
    }

    /**
     * A pending person (#65) or a blocked account (#165) is a member of nothing: no space, no paid call. A
     * pending person only reaches their request page, a blocked one only their block, and neither uses this
     * class beyond being turned away.
     */
    CurrentUser(SpaceService spaces, String login, boolean restricted) {
        this.spaces = spaces;
        this.login = SpaceService.normalizeLogin(login);
        this.pending = restricted;
        if (!restricted) {
            spaces.signedIn(this.login);
            this.spaceId = spaces.spacesOf(login).getFirst().id();
        }
    }

    /**
     * True for someone signed in but not admitted (#65), and for a blocked account (#165): no space, no role,
     * nothing to see.
     */
    public boolean pending() {
        return pending;
    }

    public String login() {
        return login;
    }

    /** The name other people see for the signed-in person (#106), re-read so a change shows at once. */
    public String name() {
        return pending ? login : spaces.displayName(login);
    }

    /** The name a page shows for a person known by login (#106); null stays null. */
    public String nameOf(String someone) {
        return someone == null ? null : spaces.displayName(someone);
    }

    /** The name the person chose (#106), empty when they keep the login. */
    public java.util.Optional<String> chosenName() {
        return pending ? java.util.Optional.empty() : spaces.chosenName(login);
    }

    /** True until the person saw the prompt for a name once (#106). */
    public boolean asksForName() {
        return !pending && spaces.asksForName(login);
    }

    /** See {@link SpaceService#chooseName}. */
    public String chooseName(String name) {
        return spaces.chooseName(login, name);
    }

    public void skipName() {
        spaces.skipName(login);
    }

    /** The names for the logins of a list (#106), in one query; a login not known stays as it is. */
    public java.util.Map<String, String> namesOf(java.util.Collection<String> logins) {
        return spaces.displayNames(logins);
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
