package io.github.vincenzofiore.rootology.roles;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RoleServiceTest {

    @Autowired
    RoleService roles;

    @Autowired
    RoleGrantRepository grants;

    @Autowired
    SpaceService spaces;

    /** The first admin, inserted by hand as in docs/operations.md. */
    @BeforeEach
    void firstAdmin() {
        spaces.signedIn("root");
        if (!grants.existsByGithubLoginAndRoleAndRevokedAtIsNull("root", AppRole.ADMIN)) {
            grants.save(new RoleGrant("root", AppRole.ADMIN, "manual", Instant.now()));
        }
    }

    @Test
    @WithMockUser(username = "nina")
    void onlyAnAdminManagesRoles() {
        spaces.signedIn("nina");

        assertThatThrownBy(() -> roles.grant("nina", "nina", AppRole.QA)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> roles.revoke("nina", "root", AppRole.ADMIN)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> roles.people("nina")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> roles.history("nina")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "root", roles = "ADMIN")
    void anAdminGrantsAndRevokesWithHistory() {
        spaces.signedIn("quinn");
        spaces.signedIn("nina");

        roles.grant("root", "Quinn", AppRole.QA);
        assertThat(roles.rolesOf("quinn")).containsExactly(AppRole.QA);
        assertThat(roles.people("root")).containsEntry("quinn", EnumSet.of(AppRole.QA))
                .containsEntry("nina", EnumSet.noneOf(AppRole.class))
                .containsEntry("root", EnumSet.of(AppRole.ADMIN));
        assertThatThrownBy(() -> roles.grant("root", "quinn", AppRole.QA)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> roles.grant("root", "stranger", AppRole.QA))
                .isInstanceOf(IllegalArgumentException.class);

        roles.revoke("root", "quinn", AppRole.QA);
        assertThat(roles.rolesOf("quinn")).isEmpty();
        roles.grant("root", "quinn", AppRole.QA);

        assertThat(roles.rolesOf("quinn")).as("granted again after a revocation").containsExactly(AppRole.QA);
        assertThat(roles.history("root")).filteredOn(g -> g.githubLogin().equals("quinn"))
                .extracting(RoleGrantView::grantedBy, RoleGrantView::revokedBy)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("root", null),
                        org.assertj.core.groups.Tuple.tuple("root", "root"));
    }

    @Test
    @WithMockUser(username = "ada", roles = "ADMIN")
    void theLastAdminStays() {
        spaces.signedIn("ada");
        roles.grant("root", "ada", AppRole.ADMIN);
        roles.revoke("ada", "root", AppRole.ADMIN);

        assertThatThrownBy(() -> roles.revoke("ada", "ada", AppRole.ADMIN))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least one admin");
        assertThat(roles.rolesOf("ada")).contains(AppRole.ADMIN);
        roles.grant("ada", "root", AppRole.ADMIN); // root back, as the other tests expect
    }
}
