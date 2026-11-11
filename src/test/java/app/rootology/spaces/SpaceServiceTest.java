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
            assertThat(space.name()).isEqualTo("erin's garden");
            assertThat(space.role()).isEqualTo(Role.OWNER);
        });
    }

    @Test
    void anOwnerAddsAMemberWhoThenSeesTheSpace() {
        spaces.signedIn("frank");
        long garden = spaces.spacesOf("frank").getFirst().id();

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
        long shared = spaces.spacesOf("kim").getFirst().id();
        spaces.addMember(shared, "kim", "leo", Role.MEMBER);

        spaces.signedIn("leo");

        assertThat(spaces.spacesOf("leo")).extracting(SpaceSummary::name, SpaceSummary::role)
                .containsExactlyInAnyOrder(
                        tuple("kim's garden", Role.MEMBER),
                        tuple("leo's garden", Role.OWNER));
    }

    @Test
    void gitHubLoginsAreCaseInsensitive() {
        spaces.signedIn("Mona");
        long garden = spaces.spacesOf("mona").getFirst().id();
        spaces.addMember(garden, "MONA", "Nils", Role.MEMBER);
        spaces.signedIn("nils");

        assertThat(spaces.spacesOf("MONA")).singleElement().extracting(SpaceSummary::name).isEqualTo("mona's garden");
        assertThat(spaces.isMember(garden, "NILS")).isTrue();
        assertThat(spaces.membersOf(garden, "mona")).extracting(Member::githubLogin).containsExactly("mona", "nils");
    }

    @Test
    void ownersRemoveMembersAndMembersLeaveButNobodyLeavesTheirPersonalSpace() {
        spaces.signedIn("ada");
        long garden = spaces.spacesOf("ada").getFirst().id();
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
        long shared = spaces.spacesOf("dan").getFirst().id();
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
        long shared = spaces.spacesOf("olga").getFirst().id();
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
        long garden = spaces.spacesOf("heidi").getFirst().id();
        spaces.addMember(garden, "heidi", "judy", Role.MEMBER);

        assertThatThrownBy(() -> spaces.addMember(garden, "judy", "mallory", Role.MEMBER))
                .isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.membersOf(garden, "ivan"))
                .isInstanceOf(SpaceAccessException.class);
        assertThat(spaces.isMember(garden, "ivan")).isFalse();
    }
}
