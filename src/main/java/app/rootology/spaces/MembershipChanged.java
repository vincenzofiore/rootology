package app.rootology.spaces;

import java.util.Set;

/**
 * A change of membership someone should hear about (#105): a role changed, a block or an unblock (told to the
 * member), a member who left (told to the owners). {@code message} is the optional text of who acted, already
 * checked, empty when they wrote none; {@code spaceName} is the space's name now, since a blocked member no
 * longer sees the space.
 */
public record MembershipChanged(long spaceId, String spaceName, String actor, Set<String> recipients, Event event,
                                Role role, String message) {

    public enum Event { ROLE_CHANGED, BLOCKED, UNBLOCKED, LEFT }
}
