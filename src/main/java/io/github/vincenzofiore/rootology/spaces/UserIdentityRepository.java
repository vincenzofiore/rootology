package io.github.vincenzofiore.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);
}
