/**
 * My garden: the sites a shared space looks after, their plots, and what grows in them.
 * Every read and write checks that the person is a member of the space.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Garden",
        allowedDependencies = { "spaces", "facts" }
)
package app.rootology.garden;
