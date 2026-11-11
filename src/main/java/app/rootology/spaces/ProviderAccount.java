package app.rootology.spaces;

/**
 * The provider and the stable id it gives one sign-in account (#65): personal data, read only to find the
 * records keyed by an identity (an invitation request, for example), never shown.
 */
public record ProviderAccount(IdentityProvider provider, String subject) {
}
