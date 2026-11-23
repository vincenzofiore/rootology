package app.rootology.spaces;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Memberships. The "Active" lookups leave out blocked members (#105), who are members for nothing but their own
 * row on the members page: every check that a person may see or use a space goes through them.
 */
interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    List<SpaceMember> findByUserAndBlockedAtIsNullOrderBySpaceId(AppUser user);

    List<SpaceMember> findBySpaceIdOrderById(Long spaceId);

    List<SpaceMember> findBySpaceIdAndBlockedAtIsNullOrderById(Long spaceId);

    Optional<SpaceMember> findBySpaceIdAndUserLogin(Long spaceId, String login);

    Optional<SpaceMember> findBySpaceIdAndUserLoginAndBlockedAtIsNullAndUserBlockedAtIsNull(Long spaceId, String login);

    long countBySpaceIdAndRoleAndBlockedAtIsNull(Long spaceId, Role role);

    boolean existsByUserLoginAndBlockedAtIsNull(String login);
}
