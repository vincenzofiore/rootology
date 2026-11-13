package app.rootology.spaces;

/** OWNER manages the members of a space; MEMBER uses it; GUEST only looks (#33). */
public enum Role {
    OWNER,
    MEMBER,
    GUEST;

    /** Whether this role may change the space: ask, edit the garden, answer escalations, propose facts. */
    public boolean contributes() {
        return this != GUEST;
    }
}
