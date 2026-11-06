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
        AppUser user = users.findByLogin(login).orElseGet(() -> users.save(new AppUser(login, now)));
        if (user.hasSignedIn()) {
            return;
        }
        user.markFirstLogin(now);
        Space personal = spaces.save(new Space(login + "'s garden", now, user));
        members.save(new SpaceMember(personal, user, Role.OWNER, now));
    }

    /** The spaces this person belongs to, oldest first (their personal space comes first). */
    @Transactional(readOnly = true)
    public List<SpaceSummary> spacesOf(String githubLogin) {
        return users.findByLogin(normalizeLogin(githubLogin))
                .map(user -> members.findByUserOrderBySpaceId(user).stream()
                        .map(m -> new SpaceSummary(m.getSpace().getId(), m.getSpace().getName(), m.getRole(),
                                m.getSpace().isPersonalSpaceOf(user)))
                        .toList())
                .orElse(List.of());
    }

    /** True if this person is a member of the space. */
    @Transactional(readOnly = true)
    public boolean isMember(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserLogin(spaceId, normalizeLogin(githubLogin)).isPresent();
    }

    /** True for an OWNER or MEMBER of the space; a GUEST only looks (#33). */
    @Transactional(readOnly = true)
    public boolean canContribute(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserLogin(spaceId, normalizeLogin(githubLogin))
                .map(m -> m.getRole().contributes()).orElse(false);
    }

    /** Refuses a guest or a non-member; for every change made in a space. */
    public void requireContributor(long spaceId, String githubLogin) {
        if (!canContribute(spaceId, githubLogin)) {
            throw new SpaceAccessException(githubLogin + " may not change space " + spaceId
                    + (isMember(spaceId, githubLogin) ? " as a guest" : ""));
        }
    }

    /** The members of a space; only its members may list them. */
    @Transactional(readOnly = true)
    public List<Member> membersOf(long spaceId, String requester) {
        requireMember(spaceId, requester);
        return members.findBySpaceIdOrderById(spaceId).stream()
                .map(m -> new Member(m.getUser().getLogin(), m.getRole()))
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
        if (members.findBySpaceIdAndUserLogin(spaceId, login).isPresent()) {
            return;
        }
        AppUser user = users.findByLogin(login)
                .orElseGet(() -> users.save(new AppUser(login, Instant.now())));
        members.save(new SpaceMember(owner.getSpace(), user, role, Instant.now()));
    }

    /** For system jobs, not for people (weather alerts, #37): every member of a space with their role. */
    @Transactional(readOnly = true)
    public List<Member> membersForSystem(long spaceId) {
        return members.findBySpaceIdOrderById(spaceId).stream()
                .map(m -> new Member(m.getUser().getLogin(), m.getRole())).toList();
    }

    /** Every person the app knows (signed in, or added to a space), by login. */
    @Transactional(readOnly = true)
    public List<String> people() {
        return users.findAll().stream().map(AppUser::getLogin).sorted().toList();
    }

    /** True if this person belongs to at least one space, which lets them sign in. */
    @Transactional(readOnly = true)
    public boolean belongsToAnySpace(String githubLogin) {
        return members.existsByUserLogin(normalizeLogin(githubLogin));
    }

    /**
     * Removes a member. An OWNER may remove anyone; a member may remove themselves (leave).
     * A personal space cannot be left, and a space always keeps at least one OWNER.
     * What the space holds stays with the space.
     */
    @Transactional
    public void removeMember(long spaceId, String requester, String githubLogin) {
        String login = normalizeLogin(githubLogin);
        SpaceMember actor = requireMember(spaceId, requester);
        boolean leaving = actor.getUser().getLogin().equals(login);
        if (!leaving && actor.getRole() != Role.OWNER) {
            throw new SpaceAccessException(requester + " is not an owner of space " + spaceId);
        }
        SpaceMember target = requireMember(spaceId, login);
        if (target.getSpace().isPersonalSpaceOf(target.getUser())) {
            throw new SpaceAccessException("Nobody can be removed from their own personal space");
        }
        requireAnotherOwnerIfOwner(spaceId, target);
        members.delete(target);
    }

    /** An OWNER changes a member's role; the last OWNER cannot be demoted. */
    @Transactional
    public void changeRole(long spaceId, String requester, String githubLogin, Role role) {
        SpaceMember owner = requireMember(spaceId, requester);
        if (owner.getRole() != Role.OWNER) {
            throw new SpaceAccessException(requester + " is not an owner of space " + spaceId);
        }
        SpaceMember target = requireMember(spaceId, githubLogin);
        if (role != Role.OWNER) {
            // Also when an owner becomes a guest: a space keeps at least one owner.
            requireAnotherOwnerIfOwner(spaceId, target);
        }
        target.changeRole(role);
    }

    /** Adds someone who accepted an invitation; does nothing if they are already a member. */
    @Transactional
    void join(long spaceId, String githubLogin, Role role) {
        String login = normalizeLogin(githubLogin);
        if (members.findBySpaceIdAndUserLogin(spaceId, login).isPresent()) {
            return;
        }
        AppUser user = users.findByLogin(login).orElseGet(() -> users.save(new AppUser(login, Instant.now())));
        members.save(new SpaceMember(spaces.getReferenceById(spaceId), user, role, Instant.now()));
    }

    SpaceMember requireOwner(long spaceId, String githubLogin) {
        SpaceMember member = requireMember(spaceId, githubLogin);
        if (member.getRole() != Role.OWNER) {
            throw new SpaceAccessException(githubLogin + " is not an owner of space " + spaceId);
        }
        return member;
    }

    private void requireAnotherOwnerIfOwner(long spaceId, SpaceMember target) {
        if (target.getRole() == Role.OWNER && countOwners(spaceId) == 1) {
            throw new SpaceAccessException("A space must keep at least one owner");
        }
    }

    private long countOwners(long spaceId) {
        return members.countBySpaceIdAndRole(spaceId, Role.OWNER);
    }

    private SpaceMember requireMember(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserLogin(spaceId, normalizeLogin(githubLogin))
                .orElseThrow(() -> new SpaceAccessException(githubLogin + " is not a member of space " + spaceId));
    }
}
