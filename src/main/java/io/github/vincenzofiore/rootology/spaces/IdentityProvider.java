package io.github.vincenzofiore.rootology.spaces;

/** The external providers a person can sign in with (#28). */
enum IdentityProvider {

    GITHUB("github"),
    GOOGLE("google");

    private final String id;

    IdentityProvider(String id) {
        this.id = id;
    }

    /** As stored: the lowercase name the database check constraint allows. */
    String id() {
        return id;
    }

    static IdentityProvider of(String id) {
        for (IdentityProvider provider : values()) {
            if (provider.id.equals(id)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown identity provider: " + id);
    }
}
