package app.rootology.roles;

/** Roles over the whole app, independent of spaces (where people are OWNER or MEMBER). */
public enum AppRole {
    /** Grants and revokes roles. */
    ADMIN,
    /** Reviews facts proposed for everyone (#24). */
    CURATOR,
    /** Runs and reads the quality suite (#20). */
    QA;

    /** The Spring Security authority of the role, given at sign-in (for example ROLE_ADMIN). */
    public String authority() {
        return "ROLE_" + name();
    }
}
