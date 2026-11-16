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
    void ownersBlockMembersAndMembersLeaveButNobodyLeavesTheirPersonalSpace() {
        spaces.signedIn("ada");
        long garden = app.rootology.TestSpaces.personal(spaces, "ada");
        spaces.addMember(garden, "ada", "bea", Role.MEMBER);
        spaces.addMember(garden, "ada", "cid", Role.MEMBER);

        // #105: a blocked member is listed as blocked and is a member for nothing else.
        spaces.block(garden, "ada", "bea", "  Ci rivediamo in primavera  ");
        assertThat(spaces.isMember(garden, "bea")).isFalse();
        assertThat(spaces.canContribute(garden, "bea")).isFalse();
        assertThat(spaces.spacesOf("bea")).extracting(SpaceSummary::id).doesNotContain(garden);
        assertThat(spaces.membersOf(garden, "ada")).contains(new Member("bea", "bea", Role.MEMBER, true));
        assertThat(spaces.membersForSystem(garden)).extracting(Member::githubLogin).doesNotContain("bea");
        assertThatThrownBy(() -> spaces.membersOf(garden, "bea")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.block(garden, "cid", "ada", "")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.unblock(garden, "cid", "bea")).isInstanceOf(SpaceAccessException.class);

        spaces.unblock(garden, "ada", "bea");
        assertThat(spaces.canContribute(garden, "bea")).as("the role they had").isTrue();

        spaces.leave(garden, "cid", "");
        assertThat(spaces.isMember(garden, "cid")).isFalse();
        assertThat(spaces.membersOf(garden, "ada")).extracting(Member::githubLogin).doesNotContain("cid");

        assertThatThrownBy(() -> spaces.leave(garden, "ada", "")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.block(garden, "ada", "ada", "")).isInstanceOf(SpaceAccessException.class);
        assertThat(spaces.spacesOf("ada").getFirst().personal()).isTrue();
    }

    @Test
    void anOwnerIsNeverBlockedAndAMessageIsBounded() {
        spaces.signedIn("sb-kai");
        long shared = app.rootology.TestSpaces.personal(spaces, "sb-kai");
        spaces.addMember(shared, "sb-kai", "sb-lea", Role.OWNER);
        spaces.addMember(shared, "sb-kai", "sb-max", Role.GUEST);

        assertThatThrownBy(() -> spaces.block(shared, "sb-kai", "sb-lea", "")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> spaces.block(shared, "sb-kai", "sb-max", "x".repeat(SpaceService.MAX_MESSAGE + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(spaces.isMember(shared, "sb-max")).isTrue();
        assertThat(SpaceService.checkedMessage(" ciao\u0007\na presto ")).isEqualTo("ciao\na presto");
        assertThat(SpaceService.checkedMessage(null)).isEmpty();
    }

    @Test
    void aSpaceAlwaysKeepsAnOwner() {
        spaces.signedIn("dan");
        spaces.signedIn("eve");
        long shared = app.rootology.TestSpaces.personal(spaces, "dan");
        spaces.addMember(shared, "dan", "eve", Role.OWNER);

        spaces.changeRole(shared, "eve", "dan", Role.MEMBER, "");
        assertThatThrownBy(() -> spaces.changeRole(shared, "eve", "eve", Role.MEMBER, ""))
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
        assertThatThrownBy(() -> spaces.changeRole(shared, "olga", "olga", Role.GUEST, ""))
                .isInstanceOf(SpaceAccessException.class);

        spaces.changeRole(shared, "olga", "gus", Role.MEMBER, "");
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

    @Test
    void aPersonChoosesTheNameOthersSeeAndThePromptIsAskedOnce() {
        spaces.signedIn("dn-ugo");
        assertThat(spaces.asksForName("dn-ugo")).isTrue();
        assertThat(spaces.displayName("dn-ugo")).as("the login until a name is chosen").isEqualTo("dn-ugo");

        assertThat(spaces.chooseName("DN-UGO", "  Ugo \t  Rossi ")).isEqualTo("Ugo Rossi");

        assertThat(spaces.asksForName("dn-ugo")).isFalse();
        assertThat(spaces.displayName("dn-ugo")).isEqualTo("Ugo Rossi");
        assertThat(spaces.chosenName("dn-ugo")).contains("Ugo Rossi");
        assertThat(spaces.spacesOf("dn-ugo")).singleElement().extracting(SpaceSummary::name)
                .as("the personal space, still called after the login, takes the name").isEqualTo("Ugo Rossi");
        long space = app.rootology.TestSpaces.personal(spaces, "dn-ugo");
        assertThat(spaces.membersOf(space, "dn-ugo")).singleElement()
                .extracting(Member::githubLogin, Member::displayName).containsExactly("dn-ugo", "Ugo Rossi");

        // A second change leaves a personal space the person renamed themselves alone.
        spaces.rename(space, "dn-ugo", "Orto di casa");
        spaces.chooseName("dn-ugo", "Ugo R.");
        assertThat(spaces.spacesOf("dn-ugo")).singleElement().extracting(SpaceSummary::name).isEqualTo("Orto di casa");
        assertThat(spaces.displayNames(java.util.List.of("dn-ugo", "DN-UGO", "nobody-known")))
                .containsEntry("dn-ugo", "Ugo R.").containsEntry("DN-UGO", "Ugo R.")
                .containsEntry("nobody-known", "nobody-known");
    }

    @Test
    void skippingKeepsTheLoginAndNobodyShowsAnotherPersonsName() {
        spaces.signedIn("dn-vera");
        spaces.signedIn("dn-walt");
        spaces.skipName("dn-walt");
        assertThat(spaces.asksForName("dn-walt")).isFalse();
        assertThat(spaces.displayName("dn-walt")).isEqualTo("dn-walt");

        spaces.chooseName("dn-vera", "Vera Dn");
        assertThatThrownBy(() -> spaces.chooseName("dn-walt", "VERA DN"))
                .as("another person's chosen name, ignoring case").isInstanceOf(DisplayNameTakenException.class);
        assertThatThrownBy(() -> spaces.chooseName("dn-vera", "DN-Walt"))
                .as("another person's login while they keep it").isInstanceOf(DisplayNameTakenException.class);
        assertThatThrownBy(() -> spaces.chooseName("dn-walt", "x")).isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(DisplayNameTakenException.class);
        assertThatThrownBy(() -> spaces.chooseName("dn-walt", "x".repeat(SpaceService.MAX_DISPLAY_NAME + 1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(spaces.chooseName("dn-vera", "vera dn")).as("one's own name in another case").isEqualTo("vera dn");
        assertThat(spaces.chooseName("dn-walt", "dn-walt")).as("one's own login").isEqualTo("dn-walt");
    }
}
