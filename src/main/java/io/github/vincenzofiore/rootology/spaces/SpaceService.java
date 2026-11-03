package io.github.vincenzofiore.rootology.spaces;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Who belongs to which space. Every check that a person may see a space goes through here. */
@Service
public class SpaceService {

    private final AppUserRepository users;
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;

    SpaceService(AppUserRepository users, SpaceRepository spaces, SpaceMemberRepository members) {
        this.users = users;
        this.spaces = spaces;
        this.members = members;
    }

    /** GitHub logins are case-insensitive; everything here uses them in lower case. */
    public static String normalizeLogin(String githubLogin) {
        return githubLogin.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Called on every login. On a person's first real login, also when someone had already
     * added them to a space, creates their personal space with them as OWNER.
     */
    @Transactional
    public void signedIn(String githubLogin) {
        String login = normalizeLogin(githubLogin);
        Instant now = Instant.now();
        AppUser user = users.findByGithubLogin(login).orElseGet(() -> users.save(new AppUser(login, now)));
        if (user.hasSignedIn()) {
            return;
        }
        user.markFirstLogin(now);
        Space personal = spaces.save(new Space(login + "'s garden", now));
        members.save(new SpaceMember(personal, user, Role.OWNER, now));
    }

    /** The spaces this person belongs to, oldest first (their personal space comes first). */
    @Transactional(readOnly = true)
    public List<SpaceSummary> spacesOf(String githubLogin) {
        return users.findByGithubLogin(normalizeLogin(githubLogin))
                .map(user -> members.findByUserOrderBySpaceId(user).stream()
                        .map(m -> new SpaceSummary(m.getSpace().getId(), m.getSpace().getName(), m.getRole()))
                        .toList())
                .orElse(List.of());
    }

    /** True if this person is a member of the space. */
    @Transactional(readOnly = true)
    public boolean isMember(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserGithubLogin(spaceId, normalizeLogin(githubLogin)).isPresent();
    }

    /** The members of a space; only its members may list them. */
    @Transactional(readOnly = true)
    public List<Member> membersOf(long spaceId, String requester) {
        requireMember(spaceId, requester);
        return members.findBySpaceIdOrderById(spaceId).stream()
                .map(m -> new Member(m.getUser().getGithubLogin(), m.getRole()))
                .toList();
    }

    /**
     * An OWNER adds a person by GitHub login. The person still needs to be allowed into the app
     * to sign in (see the login allow-list); invitations with links are #23.
     */
    @Transactional
    public void addMember(long spaceId, String requester, String githubLogin, Role role) {
        SpaceMember owner = requireMember(spaceId, requester);
        if (owner.getRole() != Role.OWNER) {
            throw new SpaceAccessException(requester + " is not an owner of space " + spaceId);
        }
        String login = normalizeLogin(githubLogin);
        if (login.isEmpty()) {
            throw new IllegalArgumentException("A GitHub login is required");
        }
        if (members.findBySpaceIdAndUserGithubLogin(spaceId, login).isPresent()) {
            return;
        }
        AppUser user = users.findByGithubLogin(login)
                .orElseGet(() -> users.save(new AppUser(login, Instant.now())));
        members.save(new SpaceMember(owner.getSpace(), user, role, Instant.now()));
    }

    private SpaceMember requireMember(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserGithubLogin(spaceId, normalizeLogin(githubLogin))
                .orElseThrow(() -> new SpaceAccessException(githubLogin + " is not a member of space " + spaceId));
    }
}
