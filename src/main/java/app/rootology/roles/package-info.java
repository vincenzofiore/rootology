/**
 * App-wide roles (#32): ADMIN manages roles, CURATOR reviews facts proposed for everyone (#24), QA runs
 * the quality suite (#20). Grants and their history live in Postgres; as Spring Security intends, a person's
 * roles become authorities at sign-in (ROLE_ADMIN, ...), so a change applies from their next sign-in.
 * Views use {@code @RolesAllowed}, services {@code @PreAuthorize}. The first admin of an environment is
 * inserted by hand once (docs/operations.md); later roles are granted in the Admin view.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "App Roles",
        allowedDependencies = { "spaces" }
)
package app.rootology.roles;
