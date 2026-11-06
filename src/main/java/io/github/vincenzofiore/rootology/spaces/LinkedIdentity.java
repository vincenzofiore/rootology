package io.github.vincenzofiore.rootology.spaces;

import java.time.Instant;

/**
 * One sign-in account of a person as they see it in My account (#28): the provider, the address it verified
 * (or nothing), and when it was linked. Never the provider's id, which is the app's business.
 */
public record LinkedIdentity(IdentityProvider provider, String email, Instant linkedAt) {
}
