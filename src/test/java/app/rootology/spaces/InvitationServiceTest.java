package app.rootology.spaces;

import app.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InvitationServiceTest {

    @Autowired
    InvitationService invitations;

    @Autowired
    SpaceService spaces;

    @Autowired
    InvitationRepository repository;

    @Autowired
    SpaceRepository spaceRepository;

    private long spaceOf(String owner) {
        spaces.signedIn(owner);
        return spaces.spacesOf(owner).getFirst().id();
    }

    @Test
    void anInvitedPersonJoinsOnceAndTheLinkIsUsedUp() {
        long garden = spaceOf("olga");
        String token = invitations.create(garden, "olga", Role.MEMBER, null);

        assertThat(invitations.admits(token, "pat", java.util.Set.of())).isTrue();
        assertThat(invitations.accept(token, "Pat")).isEqualTo(garden);

        assertThat(spaces.isMember(garden, "pat")).isTrue();
        assertThat(invitations.preview(token, "quinn").refusal()).isEqualTo("This invitation was already used");
        assertThatThrownBy(() -> invitations.accept(token, "quinn")).isInstanceOf(SpaceAccessException.class);
        assertThat(invitations.pending(garden, "olga")).isEmpty();
    }

    @Test
    void anInvitationSaysWhetherItNamesAGitHubLogin() {
        long garden = spaceOf("uma");
        String forAnyone = invitations.create(garden, "uma", Role.MEMBER, null);
        String forLogin = invitations.create(garden, "uma", Role.MEMBER, "only-this-one");
        String forEmail = invitations.create(garden, "uma", Role.MEMBER, null, "someone@example.org");

        assertThat(invitations.boundToLogin(forAnyone)).isFalse();
        assertThat(invitations.boundToLogin(forLogin)).isTrue();
        assertThat(invitations.boundToLogin(forEmail)).isFalse();
        assertThat(invitations.boundToLogin("no-such-token")).isFalse();
        assertThat(invitations.boundToLogin(null)).isFalse();
    }

    @Test
    void onlyTheHashOfTheTokenIsStored() {
        long garden = spaceOf("rita");
        String token = invitations.create(garden, "rita", Role.MEMBER, null);

        assertThat(repository.findAll()).noneMatch(i -> token.equals(ReflectionAccess.tokenHash(i)));
        assertThat(repository.findByTokenHash(InvitationService.hash(token))).isPresent();
    }

    @Test
    void aRestrictedInvitationAdmitsOnlyThatAccount() {
        long garden = spaceOf("sam");
        String token = invitations.create(garden, "sam", Role.MEMBER, "Tina");

        assertThat(invitations.admits(token, "uma", java.util.Set.of())).isFalse();
        assertThat(invitations.admits(token, "tina", java.util.Set.of())).isTrue();
    }

    @Test
    void revokedExpiredAndUnknownInvitationsAreRefused() {
        long garden = spaceOf("vic");
        String revoked = invitations.create(garden, "vic", Role.MEMBER, null);
        long revokedId = invitations.pending(garden, "vic").getFirst().id();
        invitations.revoke(garden, "vic", revokedId);

        String expired = "expired-token";
        repository.save(new Invitation(spaceRepository.getReferenceById(garden), InvitationService.hash(expired),
                Role.MEMBER, null, null, "vic", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60)));

        assertThat(invitations.preview(revoked, "walt").refusal()).isEqualTo("This invitation was revoked");
        assertThat(invitations.preview(expired, "walt").refusal()).isEqualTo("This invitation has expired");
        assertThat(invitations.preview("no-such-token", "walt").refusal()).isEqualTo("This invitation does not exist");
    }

    @Test
    void onlyOwnersCreateOrSeeInvitations() {
        long garden = spaceOf("xena");
        String token = invitations.create(garden, "xena", Role.MEMBER, null);
        invitations.accept(token, "yuri");

        assertThatThrownBy(() -> invitations.create(garden, "yuri", Role.MEMBER, null))
                .isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> invitations.pending(garden, "yuri")).isInstanceOf(SpaceAccessException.class);
    }

    /** Reads the stored hash without widening the entity's API. */
    static final class ReflectionAccess {
        static String tokenHash(Invitation invitation) {
            try {
                var field = Invitation.class.getDeclaredField("tokenHash");
                field.setAccessible(true);
                return (String) field.get(invitation);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
