package app.rootology.spaces;

/**
 * A member of a space, as shown to the other members: the login stays internal and the display name is what
 * people read (#106); blocked while an owner has blocked them (#105).
 */
public record Member(String githubLogin, String displayName, Role role, boolean blocked) {

    public Member(String githubLogin, Role role) {
        this(githubLogin, githubLogin, role, false);
    }
}
