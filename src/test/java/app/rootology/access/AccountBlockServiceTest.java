package app.rootology.access;

import app.rootology.TestcontainersConfiguration;
import app.rootology.TestSpaces;
import app.rootology.spaces.AccountBlock;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Blocking a whole account (#165) on a real database, with the ADMIN authority the tests provide
 * ({@code @WithMockUser}): an account is blocked and unblocked, its data stays, only an admin may decide,
 * nobody blocks themselves and the last admin is never blocked. Each test uses its own logins.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountBlockServiceTest {

    @Autowired
    AccountBlockService blocks;

    @Autowired
    SpaceService spaces;

    @Autowired
    JdbcClient jdbc;

    private Map<String, AccountBlock> blocked() {
        return blocks.blocked();
    }

    private String blockedBy(String login) {
        return jdbc.sql("select blocked_by from app_user where login = :l").param("l", login)
                .query(String.class).optional().orElse(null);
    }

    /** An active ADMIN grant written straight into the table, as docs/operations.md does for the first one. */
    private void makeAdmin(String login) {
        spaces.signedIn(login);
        jdbc.sql("""
                        insert into app_role_grant (github_login, role, granted_by, granted_at)
                        values (:l, 'ADMIN', 'blk-setup', now())""").param("l", login).update();
    }

    private void forgetGrant(String login) {
        jdbc.sql("delete from app_role_grant where github_login = :l and granted_by = 'blk-setup'")
                .param("l", login).update();
    }

    @Test
    @WithMockUser(username = "blk-admin", roles = "ADMIN")
    void anAdminBlocksAndUnblocksAnAccount() {
        spaces.signedIn("blk-rita");

        assertThat(blocks.block("blk-admin", "Blk-Rita", "domande fuori tema")).as("no address, no email").isFalse();

        assertThat(blocks.isBlocked("blk-rita")).isTrue();
        assertThat(blocked()).containsKey("blk-rita");
        assertThat(blocks.blockOf("blk-rita")).get()
                .satisfies(block -> {
                    assertThat(block.blockedBy()).isEqualTo("blk-admin");
                    assertThat(block.reason()).isEqualTo("domande fuori tema");
                    assertThat(block.blockedAt()).isNotNull();
                });

        assertThat(blocks.unblock("blk-admin", "blk-rita")).isFalse();

        assertThat(blocks.isBlocked("blk-rita")).isFalse();
        assertThat(blocked()).doesNotContainKey("blk-rita");
        assertThat(blocks.blockOf("blk-rita")).isEmpty();
        assertThat(jdbc.sql("select blocked_at, blocked_by, blocked_reason from app_user where login = 'blk-rita'")
                .query().listOfRows()).singleElement().satisfies(row ->
                assertThat(row.values()).containsOnlyNulls());
        assertThatThrownBy(() -> blocks.unblock("blk-admin", "blk-rita"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("not blocked");
    }

    @Test
    @WithMockUser(username = "blk-admin", roles = "ADMIN")
    void blockingKeepsTheDataAndTheSharedSpaceOfTheOtherMembers() {
        spaces.signedIn("blk-anna");
        spaces.signedIn("blk-bob");
        long shared = TestSpaces.personal(spaces, "blk-anna");
        spaces.addMember(shared, "blk-anna", "blk-bob", Role.MEMBER);

        blocks.block("blk-admin", "blk-bob", null);

        assertThat(spaces.spacesOf("blk-bob")).extracting(s -> s.id()).contains(shared);
        assertThat(spaces.isMember(shared, "blk-bob")).as("the membership stays, but a blocked account does nothing,"
                + " even from a page left open").isFalse();
        assertThat(spaces.canContribute(shared, "blk-bob")).isFalse();
        assertThat(spaces.canContribute(shared, "blk-anna")).isTrue();

        blocks.unblock("blk-admin", "blk-bob");

        assertThat(spaces.canContribute(shared, "blk-bob")).isTrue();
    }

    @Test
    @WithMockUser(username = "blk-nina")
    void onlyAnAdminBlocksOrUnblocks() {
        spaces.signedIn("blk-nina");
        spaces.signedIn("blk-omar");

        assertThatThrownBy(() -> blocks.block("blk-nina", "blk-omar", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> blocks.unblock("blk-nina", "blk-omar"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(this::blocked).isInstanceOf(AccessDeniedException.class);
        assertThat(blocks.isBlocked("blk-omar")).as("reading the state needs no role").isFalse();
    }

    @Test
    @WithMockUser(username = "blk-self", roles = "ADMIN")
    void nobodyBlocksThemselves() {
        makeAdmin("blk-self");
        makeAdmin("blk-twin");
        try {
            assertThatThrownBy(() -> blocks.block("blk-self", "blk-self", null))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("themselves");
            assertThat(blocks.isBlocked("blk-self")).isFalse();
        } finally {
            forgetGrant("blk-self");
            forgetGrant("blk-twin");
        }
    }

    @Test
    @WithMockUser(username = "blk-last", roles = "ADMIN")
    void theLastAdminCannotBeBlocked() {
        makeAdmin("blk-last");
        // The test database is shared (docs/testing.md): the other tests may leave admins of their own, so
        // this one becomes the last by revoking every other, and gives them back at the end.
        jdbc.sql("""
                        update app_role_grant set revoked_at = now(), revoked_by = 'blk-test'
                        where role = 'ADMIN' and revoked_at is null and github_login <> 'blk-last'""").update();
        try {
            assertThatThrownBy(() -> blocks.block("blk-last", "blk-last", null))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("last admin");
            assertThat(blocks.isBlocked("blk-last")).isFalse();
        } finally {
            jdbc.sql("update app_role_grant set revoked_at = null, revoked_by = null where revoked_by = 'blk-test'")
                    .update();
            forgetGrant("blk-last");
        }
    }

    @Test
    @WithMockUser(username = "blk-admin", roles = "ADMIN")
    void theReasonIsOneShortLineOfPlainText() {
        spaces.signedIn("blk-vic");

        blocks.block("blk-admin", "blk-vic", "  domande\nfuori\ttema  ");
        assertThat(blocked().get("blk-vic").reason()).isEqualTo("domande fuori tema");
        blocks.unblock("blk-admin", "blk-vic");

        blocks.block("blk-admin", "blk-vic", "   ");
        assertThat(blocked().get("blk-vic").reason()).as("no reason").isNull();
        blocks.unblock("blk-admin", "blk-vic");

        assertThatThrownBy(() -> blocks.block("blk-admin", "blk-vic", "x".repeat(AccountBlockService.MAX_REASON + 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most");
        assertThatThrownBy(() -> blocks.block("blk-admin", "blk-nobody", null))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("not a person");
    }

    @Test
    @WithMockUser(username = "blk-admin", roles = "ADMIN")
    void anAccountIsBlockedOnlyOnce() {
        spaces.signedIn("blk-wes");

        blocks.block("blk-admin", "blk-wes", null);

        assertThatThrownBy(() -> blocks.block("blk-admin", "blk-wes", "again"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already blocked");
        assertThat(blocks.blockOf("blk-wes")).get().satisfies(block -> assertThat(block.reason()).isNull());
        blocks.unblock("blk-admin", "blk-wes");
    }

    /** Blocked accounts and admins of other tests are visible in the shared database: this test speaks only its own. */
    @Test
    @WithMockUser(username = "blk-admin", roles = "ADMIN")
    void blockedAccountsAreListedByLogin() {
        spaces.signedIn("blk-yan");
        spaces.signedIn("blk-zoe");
        blocks.block("blk-admin", "blk-yan", "uno");

        assertThat(blocked()).containsKey("blk-yan").doesNotContainKey("blk-zoe");
        blocks.unblock("blk-admin", "blk-yan");
    }

    /** Nothing of a blocked account is deleted, so its spaces are still listed for the other members. */
    @Test
    @WithMockUser(username = "blk-admin", roles = "ADMIN")
    void unblockingGivesBackExactlyWhatThereWas() {
        spaces.signedIn("blk-uma");
        long space = TestSpaces.personal(spaces, "blk-uma");

        blocks.block("blk-admin", "blk-uma", "una prova");
        blocks.unblock("blk-admin", "blk-uma");

        assertThat(spaces.spacesOf("blk-uma")).extracting(s -> s.id()).containsExactly(space);
        assertThat(blocks.blockOf("blk-uma")).isEmpty();
    }
}
