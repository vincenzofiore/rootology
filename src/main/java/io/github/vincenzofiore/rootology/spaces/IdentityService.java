package io.github.vincenzofiore.rootology.spaces;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * The external identities of a person (#28): for each provider, the id that provider gives them and never
 * changes. A person is found by it first and by their login only as a fallback, so renaming an account
 * there keeps their spaces, and someone taking a freed name cannot enter their account.
 */
@Service
public class IdentityService {

    private final UserIdentityRepository identities;
    private final AppUserRepository users;

    IdentityService(UserIdentityRepository identities, AppUserRepository users) {
        this.identities = identities;
        this.users = users;
    }

    /** The person's own login, if this provider account is already known. */
    @Transactional(readOnly = true)
    public Optional<String> loginOf(IdentityProvider provider, String subject) {
        return identities.findByProviderAndSubject(provider, subject)
                .map(identity -> identity.getUser().getLogin());
    }

    /** True if this person is already linked to the provider, whichever account they have there. */
    @Transactional(readOnly = true)
    public boolean hasIdentity(String login, IdentityProvider provider) {
        return users.findByLogin(SpaceService.normalizeLogin(login))
                .map(user -> identities.existsByUserIdAndProvider(user.getId(), provider))
                .orElse(false);
    }

    /**
     * Links an account of {@code provider} to the person with this login. Does nothing if it is linked
     * already. Refuses, without writing, when the account belongs to another person or the person already
     * has a different account with that provider.
     */
    @Transactional
    public void link(String login, IdentityProvider provider, String subject, String email) {
        String normalized = SpaceService.normalizeLogin(login);
        AppUser user = users.findByLogin(normalized)
                .orElseThrow(() -> new SpaceAccessException(normalized + " is not a known person"));
        Optional<UserIdentity> linked = identities.findByProviderAndSubject(provider, subject);
        if (linked.isPresent()) {
            if (linked.get().getUser().getId().equals(user.getId())) {
                return;
            }
            throw new SpaceAccessException(provider.id() + " account " + subject + " belongs to another person");
        }
        if (identities.existsByUserIdAndProvider(user.getId(), provider)) {
            throw new SpaceAccessException(normalized + " already has another " + provider.id() + " account");
        }
        identities.save(new UserIdentity(user, provider, subject, email, Instant.now()));
    }
}
