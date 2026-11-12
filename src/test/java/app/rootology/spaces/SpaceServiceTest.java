package app.rootology.spaces;

import app.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SpaceServiceTest {

    @Autowired
    SpaceService spaces;

    @Test
    void theFirstLoginCreatesAPersonalSpaceOwnedByThatPerson() {
        spaces.signedIn("erin");
        spaces.signedIn("erin");

        assertThat(spaces.spacesOf("erin")).singleElement().satisfies(space -> {
            assertThat(space.name()).isEqualTo("erin");
            assertThat(space.role()).isEqualTo(Role.OWNER);
        });
    }

    @Test
    void anOwnerRenamesTheSpace() {
        spaces.signedIn("nora");
        long space = app.rootology.TestSpaces.personal(spaces, "nora");

        spaces.rename(space, "nora", "  Casa in collina  ");

        assertThat(spaces.spacesOf("nora")).singleElement().extracting(SpaceSummary::name)
                .isEqualTo("Casa in collina");
    }

    @Test
    void onlyAnOwnerRenamesAndTheNameMustBeUsable() {
        spaces.signedIn("otto");
        long space = app.rootology.TestSpaces.personal(spaces, "otto");
        spaces.addMember(space, "otto", "pia", Role.MEMBER);

        assertThatThrownBy(() -> spaces.rename(space, "pia", "Altro")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.rename(space, "otto", "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> spaces.rename(space, "otto", "x".repeat(SpaceService.MAX_NAME + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(spaces.spacesOf("otto")).singleElement().extracting(SpaceSummary::name).isEqualTo("otto");
    }

    @Test
    void anOwnerAddsAMemberWhoThenSeesTheSpace() {
        spaces.signedIn("frank");
        long garden = app.rootology.TestSpaces.personal(spaces, "frank");

        spaces.addMember(garden, "frank", "grace", Role.MEMBER);
        spaces.signedIn("grace");

        assertThat(spaces.isMember(garden, "grace")).isTrue();
        assertThat(spaces.spacesOf("grace")).extracting(SpaceSummary::id).contains(garden);
        assertThat(spaces.membersOf(garden, "grace")).containsExactly(
                new Member("frank", Role.OWNER), new Member("grace", Role.MEMBER));
    }

    @Test
    void someoneAddedBeforeTheirFirstLoginStillGetsAPersonalSpace() {
        spaces.signedIn("kim");
        long shared = app.rootology.TestSpaces.personal(spaces, "kim");
        spaces.addMember(shared, "kim", "leo", Role.MEMBER);

        spaces.signedIn("leo");

        assertThat(spaces.spacesOf("leo")).extracting(SpaceSummary::name, SpaceSummary::role)
                .containsExactlyInAnyOrder(
                        tuple("kim", Role.MEMBER),
                        tuple("leo", Role.OWNER));
    }

    @Test
    void gitHubLoginsAreCaseInsensitive() {
        spaces.signedIn("Mona");
        long garden = app.rootology.TestSpaces.personal(spaces, "mona");
        spaces.addMember(garden, "MONA", "Nils", Role.MEMBER);
        spaces.signedIn("nils");

        assertThat(spaces.spacesOf("MONA")).singleElement().extracting(SpaceSummary::name).isEqualTo("mona");
        assertThat(spaces.isMember(garden, "NILS")).isTrue();
        assertThat(spaces.membersOf(garden, "mona")).extracting(Member::githubLogin).containsExactly("mona", "nils");
    }

    @Test
    void ownersRemoveMembersAndMembersLeaveButNobodyLeavesTheirPersonalSpace() {
        spaces.signedIn("ada");
        long garden = app.rootology.TestSpaces.personal(spaces, "ada");
        spaces.addMember(garden, "ada", "bea", Role.MEMBER);
        spaces.addMember(garden, "ada", "cid", Role.MEMBER);

        spaces.removeMember(garden, "ada", "bea");
        assertThat(spaces.isMember(garden, "bea")).isFalse();

        assertThatThrownBy(() -> spaces.removeMember(garden, "cid", "ada")).isInstanceOf(SpaceAccessException.class);
        spaces.removeMember(garden, "cid", "cid");
        assertThat(spaces.isMember(garden, "cid")).isFalse();

        assertThatThrownBy(() -> spaces.removeMember(garden, "ada", "ada")).isInstanceOf(SpaceAccessException.class);
        assertThat(spaces.spacesOf("ada").getFirst().personal()).isTrue();
    }

    @Test
    void aSpaceAlwaysKeepsAnOwner() {
        spaces.signedIn("dan");
        spaces.signedIn("eve");
        long shared = app.rootology.TestSpaces.personal(spaces, "dan");
        spaces.addMember(shared, "dan", "eve", Role.OWNER);

        spaces.changeRole(shared, "eve", "dan", Role.MEMBER);
        assertThatThrownBy(() -> spaces.changeRole(shared, "eve", "eve", Role.MEMBER))
                .isInstanceOf(SpaceAccessException.class);
        assertThat(spaces.membersOf(shared, "dan")).extracting(Member::githubLogin, Member::role)
                .containsExactlyInAnyOrder(tuple("dan", Role.MEMBER), tuple("eve", Role.OWNER));
    }

    @Test
    void aGuestLooksButDoesNotContributeAndTheLastOwnerCannotBecomeOne() {
        spaces.signedIn("olga");
        spaces.signedIn("gus");
        long shared = app.rootology.TestSpaces.personal(spaces, "olga");
        spaces.addMember(shared, "olga", "gus", Role.GUEST);

        assertThat(spaces.isMember(shared, "gus")).isTrue();
        assertThat(spaces.canContribute(shared, "gus")).isFalse();
        assertThat(spaces.canContribute(shared, "olga")).isTrue();
        assertThatThrownBy(() -> spaces.requireContributor(shared, "gus"))
                .isInstanceOf(SpaceAccessException.class).hasMessageContaining("as a guest");
        assertThatThrownBy(() -> spaces.changeRole(shared, "olga", "olga", Role.GUEST))
                .isInstanceOf(SpaceAccessException.class);

        spaces.changeRole(shared, "olga", "gus", Role.MEMBER);
        assertThat(spaces.canContribute(shared, "gus")).isTrue();
    }

    @Test
    void onlyOwnersAddMembersAndOnlyMembersSeeTheList() {
        spaces.signedIn("heidi");
        spaces.signedIn("ivan");
        long garden = app.rootology.TestSpaces.personal(spaces, "heidi");
        spaces.addMember(garden, "heidi", "judy", Role.MEMBER);

        assertThatThrownBy(() -> spaces.addMember(garden, "judy", "mallory", Role.MEMBER))
                .isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.membersOf(garden, "ivan"))
                .isInstanceOf(SpaceAccessException.class);
        assertThat(spaces.isMember(garden, "ivan")).isFalse();
    }
}
