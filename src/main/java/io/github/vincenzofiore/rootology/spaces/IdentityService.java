package io.github.vincenzofiore.rootology.spaces;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    /** True if this person came in with any account at all, whichever provider. */
    @Transactional(readOnly = true)
    public boolean hasAnyIdentity(String login) {
        return users.findByLogin(SpaceService.normalizeLogin(login))
                .map(user -> identities.existsByUserId(user.getId()))
                .orElse(false);
    }

    /** True if a person with this login exists, whether or not they have an identity. */
    @Transactional(readOnly = true)
    public boolean isKnownPerson(String login) {
        return users.findByLogin(SpaceService.normalizeLogin(login)).isPresent();
    }

    /** The person's sign-in accounts, oldest first, as My account shows them (#28). */
    @Transactional(readOnly = true)
    public List<LinkedIdentity> linkedTo(String login) {
        return users.findByLogin(SpaceService.normalizeLogin(login))
                .map(user -> identities.findByUserIdOrderByLinkedAtAsc(user.getId()).stream()
                        .map(identity -> new LinkedIdentity(identity.getProvider(), identity.getEmail(),
                                identity.getLinkedAt()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * The provider and stable id of each sign-in account of a person, without addresses: personal data (#40)
     * uses them to find the records keyed by an identity, such as an invitation request (#65).
     */
    @Transactional(readOnly = true)
    public List<ProviderAccount> accountsOf(String login) {
        return users.findByLogin(SpaceService.normalizeLogin(login))
                .map(user -> identities.findByUserIdOrderByLinkedAtAsc(user.getId()).stream()
                        .map(identity -> new ProviderAccount(identity.getProvider(), identity.getSubject()))
                        .toList())
                .orElse(List.of());
    }

    /**
     * A login derived from {@code base} that nobody has yet: the base itself, or the base with 2, 3, ... after
     * it. Used for a person whose login must be invented, as for Google (#28).
     */
    @Transactional(readOnly = true)
    public String freeLogin(String base) {
        if (users.findByLogin(base).isEmpty()) {
            return base;
        }
        for (int n = 2; ; n++) {
            String candidate = base + n;
            if (users.findByLogin(candidate).isEmpty()) {
                return candidate;
            }
        }
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

    /**
     * Removes the person's account with {@code provider} (#28). Refuses when it is their last one: nobody may be
     * left without a way in. Nothing else about the person changes.
     */
    @Transactional
    public void unlink(String login, IdentityProvider provider) {
        String normalized = SpaceService.normalizeLogin(login);
        AppUser user = users.findByLogin(normalized)
                .orElseThrow(() -> new SpaceAccessException(normalized + " is not a known person"));
        List<UserIdentity> own = identities.findByUserIdOrderByLinkedAtAsc(user.getId());
        UserIdentity target = own.stream().filter(identity -> identity.getProvider() == provider).findFirst()
                .orElseThrow(() -> new SpaceAccessException(normalized + " has no " + provider.id() + " account"));
        if (own.size() < 2) {
            throw new SpaceAccessException(normalized + " would be left without a way in");
        }
        identities.delete(target);
    }
}
