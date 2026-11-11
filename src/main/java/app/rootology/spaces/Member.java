package app.rootology.spaces;

/** A member of a space, as shown to the other members. */
public record Member(String githubLogin, Role role) {
}
