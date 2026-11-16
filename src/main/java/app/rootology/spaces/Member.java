package app.rootology.spaces;

/** A member of a space, as shown to the other members; blocked while an owner has blocked them (#105). */
public record Member(String githubLogin, Role role, boolean blocked) {

    public Member(String githubLogin, Role role) {
        this(githubLogin, role, false);
    }
}
