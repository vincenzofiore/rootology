package app.rootology.spaces;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Who belongs to which space. Every check that a person may see a space goes through here. */
@Service
public class SpaceService {

    private final AppUserRepository users;
    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final ApplicationEventPublisher events;

    SpaceService(AppUserRepository users, SpaceRepository spaces, SpaceMemberRepository members,
                 ApplicationEventPublisher events) {
        this.users = users;
        this.spaces = spaces;
        this.members = members;
        this.events = events;
    }

    /** How long the message of a change of membership may be (#105). */
    public static final int MAX_MESSAGE = 500;

    /**
     * The optional message of a change of membership (#105): trimmed, no control characters but line breaks,
     * at most {@link #MAX_MESSAGE} characters; empty when there is none.
     */
    static String checkedMessage(String message) {
        String clean = message == null ? "" : message.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        if (clean.length() > MAX_MESSAGE) {
            throw new IllegalArgumentException("A message is at most " + MAX_MESSAGE + " characters");
        }
        return clean;
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
        // The personal space is called after its owner (#79): no English "garden" in an Italian UI.
        Space personal = spaces.save(new Space(login, now, user));
        members.save(new SpaceMember(personal, user, Role.OWNER, now));
    }

    /** How long a display name may be (#106): enough for a first and last name, short enough for a card. */
    public static final int MAX_DISPLAY_NAME = 40;

    /**
     * A display name as people read it (#106): no control characters, spaces collapsed and trimmed, two to
     * {@link #MAX_DISPLAY_NAME} characters.
     */
    static String checkedDisplayName(String name) {
        String clean = name == null ? "" : name.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() < 2) {
            throw new IllegalArgumentException("A display name has at least 2 characters");
        }
        if (clean.length() > MAX_DISPLAY_NAME) {
            throw new IllegalArgumentException("A display name is at most " + MAX_DISPLAY_NAME + " characters");
        }
        return clean;
    }

    /** The name other people see for this login (#106); the login itself for someone the app does not know. */
    @Transactional(readOnly = true)
    public String displayName(String login) {
        if (login == null) {
            return null;
        }
        return users.findByLogin(normalizeLogin(login)).map(AppUser::shownName).orElse(login);
    }

    /** The names for several logins at once (#106), for a list; a login the app does not know stays as it is. */
    @Transactional(readOnly = true)
    public Map<String, String> displayNames(Collection<String> logins) {
        Set<String> wanted = logins.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = new HashMap<>();
        wanted.forEach(login -> names.put(login, login));
        if (!wanted.isEmpty()) {
            Map<String, String> byNormal = users.findByLoginIn(wanted.stream().map(SpaceService::normalizeLogin)
                    .collect(Collectors.toSet())).stream().collect(Collectors.toMap(AppUser::getLogin, AppUser::shownName));
            wanted.forEach(login -> names.put(login, byNormal.getOrDefault(normalizeLogin(login), login)));
        }
        return names;
    }

    /** The name the person chose (#106), empty when they chose none. */
    @Transactional(readOnly = true)
    public Optional<String> chosenName(String login) {
        return users.findByLogin(normalizeLogin(login)).map(AppUser::getDisplayName);
    }

    /** True until the person has seen the prompt for a name once, choosing or skipping (#106). */
    @Transactional(readOnly = true)
    public boolean asksForName(String login) {
        return users.findByLogin(normalizeLogin(login)).map(u -> !u.wasAskedForName()).orElse(false);
    }

    /** The person skipped the prompt for a name (#106): they keep the login, and choose later in My account. */
    @Transactional
    public void skipName(String login) {
        users.findByLogin(normalizeLogin(login)).ifPresent(u -> u.askedForName(Instant.now()));
    }

    /**
     * The person chooses the name other people see (#106), freely and as often as they like. No one else may
     * show the same name, ignoring case: neither as a chosen name nor as a login. Their personal space, still
     * called after their login, takes the new name.
     *
     * @throws DisplayNameTakenException when someone else shows that name
     */
    @Transactional
    public String chooseName(String login, String name) {
        AppUser user = users.findByLogin(normalizeLogin(login))
                .orElseThrow(() -> new SpaceAccessException(login + " is not known"));
        String checked = checkedDisplayName(name);
        if (users.nameShownByAnother(user.getLogin(), checked)) {
            throw new DisplayNameTakenException(checked);
        }
        String before = user.shownName();
        user.chooseName(checked, Instant.now());
        spaces.findByPersonalOf(user).filter(s -> s.getName().equals(before) || s.getName().equals(user.getLogin()))
                .ifPresent(s -> s.rename(checked));
        return checked;
    }

    /** The spaces this person belongs to, oldest first (their personal space comes first). */
    @Transactional(readOnly = true)
    public List<SpaceSummary> spacesOf(String githubLogin) {
        return users.findByLogin(normalizeLogin(githubLogin))
                .map(user -> members.findByUserAndBlockedAtIsNullOrderBySpaceId(user).stream()
                        .map(m -> new SpaceSummary(m.getSpace().getId(), m.getSpace().getName(), m.getRole(),
                                m.getSpace().isPersonalSpaceOf(user)))
                        .toList())
                .orElse(List.of());
    }

    /** How long a space's name may be (#79): enough for a place, short enough for a menu. */
    public static final int MAX_NAME = 60;

    /** A space's name as people read it (#79): trimmed, not empty, not too long. */
    static String checkedName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A space needs a name");
        }
        if (trimmed.length() > MAX_NAME) {
            throw new IllegalArgumentException("A space name is at most " + MAX_NAME + " characters");
        }
        return trimmed;
    }

    /** An OWNER renames the space (#79). The name is all that changes: its gardens and questions stay. */
    @Transactional
    public void rename(long spaceId, String requester, String name) {
        requireOwner(spaceId, requester).getSpace().rename(checkedName(name));
    }

    /** True if this person is a member of the space, and not blocked there (#105). */
    @Transactional(readOnly = true)
    public boolean isMember(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserLoginAndBlockedAtIsNull(spaceId, normalizeLogin(githubLogin)).isPresent();
    }

    /** True for an OWNER or MEMBER of the space; a GUEST only looks (#33), a blocked member does nothing (#105). */
    @Transactional(readOnly = true)
    public boolean canContribute(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserLoginAndBlockedAtIsNull(spaceId, normalizeLogin(githubLogin))
                .map(m -> m.getRole().contributes()).orElse(false);
    }

    /** Refuses a guest or a non-member; for every change made in a space. */
    public void requireContributor(long spaceId, String githubLogin) {
        if (!canContribute(spaceId, githubLogin)) {
            throw new SpaceAccessException(githubLogin + " may not change space " + spaceId
                    + (isMember(spaceId, githubLogin) ? " as a guest" : ""));
        }
    }

    /** The name of a space, for the context line of a question (#55); only its members may read it. */
    @Transactional(readOnly = true)
    public Optional<String> spaceName(long spaceId, String requester) {
        if (!isMember(spaceId, requester)) {
            throw new SpaceAccessException(requester + " is not a member of space " + spaceId);
        }
        return spaces.findById(spaceId).map(Space::getName);
    }

    /** The members of a space, blocked ones too (#105); only its members may list them. */
    @Transactional(readOnly = true)
    public List<Member> membersOf(long spaceId, String requester) {
        requireMember(spaceId, requester);
        return members.findBySpaceIdOrderById(spaceId).stream()
                .map(m -> new Member(m.getUser().getLogin(), m.getUser().shownName(), m.getRole(), m.isBlocked()))
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
            // Already listed, blocked or not: unblocking is the owner's explicit choice (#105).
            return;
        }
        AppUser user = users.findByLogin(login)
                .orElseGet(() -> users.save(new AppUser(login, Instant.now())));
        members.save(new SpaceMember(owner.getSpace(), user, role, Instant.now()));
    }

    /** For system jobs, not for people (weather alerts, #37): every member of a space with their role, not blocked. */
    @Transactional(readOnly = true)
    public List<Member> membersForSystem(long spaceId) {
        return members.findBySpaceIdAndBlockedAtIsNullOrderById(spaceId).stream()
                .map(m -> new Member(m.getUser().getLogin(), m.getUser().shownName(), m.getRole(), false)).toList();
    }

    /** Every person the app knows (signed in, or added to a space), by login. */
    @Transactional(readOnly = true)
    public List<String> people() {
        return users.findAll().stream().map(AppUser::getLogin).sorted().toList();
    }

    /** True if this person belongs to at least one space, not blocked there, which lets them sign in. */
    @Transactional(readOnly = true)
    public boolean belongsToAnySpace(String githubLogin) {
        return members.existsByUserLoginAndBlockedAtIsNull(normalizeLogin(githubLogin));
    }

    /**
     * A member leaves a space (#105: the member's own choice; owners block instead of removing). A personal space
     * cannot be left, and a space always keeps at least one OWNER. What the space holds stays with the space. The
     * owners hear about it, with the member's message when they wrote one.
     */
    @Transactional
    public void leave(long spaceId, String githubLogin, String message) {
        String text = checkedMessage(message);
        SpaceMember member = requireMember(spaceId, githubLogin);
        if (member.getSpace().isPersonalSpaceOf(member.getUser())) {
            throw new SpaceAccessException("Nobody can leave their own personal space");
        }
        requireAnotherOwnerIfOwner(spaceId, member);
        members.delete(member);
        Set<String> owners = members.findBySpaceIdAndBlockedAtIsNullOrderById(spaceId).stream()
                .filter(m -> m.getRole() == Role.OWNER).map(m -> m.getUser().getLogin()).collect(Collectors.toSet());
        events.publishEvent(new MembershipChanged(spaceId, member.getSpace().getName(), member.getUser().getLogin(),
                owners, MembershipChanged.Event.LEFT, member.getRole(), text));
    }

    /**
     * An OWNER changes a member's role; the last OWNER cannot be demoted. The member hears about it (#105), with
     * the owner's message when they wrote one; an owner changing their own role tells nobody.
     */
    @Transactional
    public void changeRole(long spaceId, String requester, String githubLogin, Role role, String message) {
        String text = checkedMessage(message);
        SpaceMember owner = requireOwner(spaceId, requester);
        SpaceMember target = requireMember(spaceId, githubLogin);
        if (target.getRole() == role) {
            return;
        }
        if (role != Role.OWNER) {
            // Also when an owner becomes a guest: a space keeps at least one owner.
            requireAnotherOwnerIfOwner(spaceId, target);
        }
        target.changeRole(role);
        tell(owner, target, MembershipChanged.Event.ROLE_CHANGED, text);
    }

    /**
     * An OWNER blocks a member (#105): they stay listed as blocked, no longer see or use the space, and keep
     * their past questions attributed. An owner is never blocked (their role changes first, so the space keeps
     * an owner), nor anyone in their own personal space.
     */
    @Transactional
    public void block(long spaceId, String requester, String githubLogin, String message) {
        String text = checkedMessage(message);
        SpaceMember owner = requireOwner(spaceId, requester);
        SpaceMember target = requireMember(spaceId, githubLogin);
        if (target.getRole() == Role.OWNER) {
            throw new SpaceAccessException("An owner is not blocked: change their role first");
        }
        if (target.getSpace().isPersonalSpaceOf(target.getUser())) {
            throw new SpaceAccessException("Nobody is blocked in their own personal space");
        }
        target.block(owner.getUser().getLogin(), Instant.now());
        tell(owner, target, MembershipChanged.Event.BLOCKED, text);
    }

    /** An OWNER unblocks a blocked member (#105): they are a member again, with the role they had. */
    @Transactional
    public void unblock(long spaceId, String requester, String githubLogin) {
        SpaceMember owner = requireOwner(spaceId, requester);
        SpaceMember target = members.findBySpaceIdAndUserLogin(spaceId, normalizeLogin(githubLogin))
                .filter(SpaceMember::isBlocked)
                .orElseThrow(() -> new SpaceAccessException(githubLogin + " is not blocked in space " + spaceId));
        target.unblock();
        tell(owner, target, MembershipChanged.Event.UNBLOCKED, "");
    }

    /** Tells a member what an owner did to their membership (#105), unless the owner did it to themselves. */
    private void tell(SpaceMember owner, SpaceMember target, MembershipChanged.Event event, String message) {
        String to = target.getUser().getLogin();
        if (to.equals(owner.getUser().getLogin())) {
            return;
        }
        events.publishEvent(new MembershipChanged(target.getSpace().getId(), target.getSpace().getName(),
                owner.getUser().getLogin(), Set.of(to), event, target.getRole(), message));
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
        return members.countBySpaceIdAndRoleAndBlockedAtIsNull(spaceId, Role.OWNER);
    }

    /** An active member: a blocked one is a member for nothing (#105). */
    private SpaceMember requireMember(long spaceId, String githubLogin) {
        return members.findBySpaceIdAndUserLoginAndBlockedAtIsNull(spaceId, normalizeLogin(githubLogin))
                .orElseThrow(() -> new SpaceAccessException(githubLogin + " is not a member of space " + spaceId));
    }
}
