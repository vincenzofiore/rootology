package io.github.vincenzofiore.rootology.roles;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface RoleGrantRepository extends JpaRepository<RoleGrant, Long> {

    boolean existsByGithubLoginAndRoleAndRevokedAtIsNull(String githubLogin, AppRole role);

    Optional<RoleGrant> findByGithubLoginAndRoleAndRevokedAtIsNull(String githubLogin, AppRole role);

    List<RoleGrant> findByRevokedAtIsNull();

    long countByRoleAndRevokedAtIsNull(AppRole role);

    List<RoleGrant> findAllByOrderByIdDesc();
}
