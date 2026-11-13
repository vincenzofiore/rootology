package app.rootology.ui;

/**
 * What Rootology adds to the signed-in principal (#28), whatever provider it came from. The person's own login
 * lives there, so nothing in the app reads the name a provider gave them: that one can change, or be taken by
 * someone else.
 */
public final class SignedIn {

    /** The person's internal login, also the principal's name. */
    public static final String LOGIN_ATTRIBUTE = "rootology_login";

    /** The addresses the provider verified for this person, as a list (empty when it verified none). */
    public static final String VERIFIED_EMAILS_ATTRIBUTE = "verified_emails";

    private SignedIn() {
    }
}
