package io.github.vincenzofiore.rootology.spaces;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
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
