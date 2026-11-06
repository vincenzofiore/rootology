package io.github.vincenzofiore.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    List<SpaceMember> findByUserOrderBySpaceId(AppUser user);

    List<SpaceMember> findBySpaceIdOrderById(Long spaceId);

    Optional<SpaceMember> findBySpaceIdAndUserLogin(Long spaceId, String login);

    long countBySpaceIdAndRole(Long spaceId, Role role);

    boolean existsByUserLogin(String login);
}
