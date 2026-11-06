package io.github.vincenzofiore.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    Optional<UserIdentity> findByProviderAndSubject(IdentityProvider provider, String subject);

    List<UserIdentity> findByUserIdOrderByLinkedAtAsc(Long userId);

    boolean existsByUserIdAndProvider(Long userId, IdentityProvider provider);

    boolean existsByUserId(Long userId);
}
