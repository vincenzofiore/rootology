package io.github.vincenzofiore.rootology.ui;

/**
 * What Rootology adds to the signed-in principal (#28). The person's own login lives there, so nothing in the
 * app reads the name a provider gave them: that one can change, or be taken by someone else.
 */
public final class SignedIn {

    /** The person's internal login, also the principal's name. */
    public static final String LOGIN_ATTRIBUTE = "rootology_login";

    private SignedIn() {
    }
}
