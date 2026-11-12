package app.rootology.spaces;

import app.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invitations by email (#25), with a real SMTP server (Mailpit) catching what is sent. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.base-url=https://rootology.example", "rootology.invitations.daily-limit=3"})
class InvitationEmailTest {

    @Autowired
    InvitationService invitations;

    @Autowired
    InvitationRepository repository;

    @Autowired
    SpaceService spaces;

    @Autowired
    SpaceRepository spaceRepository;

    @Value("${rootology.test.mailpit-api}")
    String mailpit;

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return app.rootology.TestSpaces.personal(spaces, login);
    }

    /** The message Mailpit received for this address, waiting for the background send. */
    private JsonNode messageTo(String address) throws InterruptedException {
        RestClient api = RestClient.create(mailpit);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = JsonMapper.builder().build().readTree(api.get()
                    .uri(u -> u.path("/api/v1/search").queryParam("query", "to:\"" + address + "\"").build())
                    .retrieve().body(String.class));
            if (found.path("messages_count").asInt() > 0) {
                String id = found.path("messages").get(0).path("ID").asString();
                return JsonMapper.builder().build().readTree(api.get().uri("/api/v1/message/" + id)
                        .retrieve().body(String.class));
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No email to " + address + " within 20 seconds");
    }

    @Test
    void anInvitationIsEmailedInItalianWithItsLink() throws Exception {
        long garden = spaceOf("ada");
        String token = invitations.create(garden, "ada", Role.MEMBER, null, " Bruno@Example.org ");

        JsonNode message = messageTo("bruno@example.org");

        assertThat(message.path("Subject").asString()).isEqualTo("Invito a \"ada\" su Rootology");
        assertThat(message.path("Text").asString()).contains("ada ti invita a unirti")
                .contains("https://rootology.example/invite/" + token).contains("come membro");
        assertThat(message.path("HTML").asString()).contains("href=\"https://rootology.example/invite/" + token + "\"");
    }

    @Test
    void onlySomeoneWithTheAddressVerifiedJoinsAndTheAddressIsThenForgotten() {
        long garden = spaceOf("carla");
        String token = invitations.create(garden, "carla", Role.GUEST, null, "dario@example.org");

        assertThat(invitations.preview(token, null).forEmail()).isEqualTo("d***@example.org");
        assertThat(invitations.admits(token, "dario", Set.of("other@example.org"))).isFalse();
        assertThat(invitations.preview(token, "dario", Set.of()).refusal()).contains("d***@example.org");
        assertThatThrownBy(() -> invitations.accept(token, "dario", Set.of())).isInstanceOf(SpaceAccessException.class);

        invitations.accept(token, "dario", Set.of("dario@example.org", "d@work.example"));

        assertThat(spaces.canContribute(garden, "dario")).isFalse(); // joined as a guest
        assertThat(spaces.isMember(garden, "dario")).isTrue();
        assertThat(repository.findBySpaceIdOrderByCreatedAtDesc(garden)).allMatch(i -> i.getInvitedEmail() == null);
    }

    @Test
    void anOwnerCreatesAtMostTheDailyLimitOfInvitations() {
        long garden = spaceOf("elio");
        for (int i = 0; i < 3; i++) {
            invitations.create(garden, "elio", Role.MEMBER, null);
        }

        assertThatThrownBy(() -> invitations.create(garden, "elio", Role.MEMBER, null, "f@example.org"))
                .isInstanceOf(SpaceAccessException.class).hasMessageContaining("3 invitations");
        assertThatThrownBy(() -> invitations.create(spaceOf("gino"), "gino", Role.MEMBER, null, "not-an-address"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAddressOfAnExpiredInvitationIsForgotten() {
        long garden = spaceOf("ines");
        repository.save(new Invitation(spaceRepository.getReferenceById(garden), InvitationService.hash("old"), Role.MEMBER, null,
                "old@example.org", "ines", Instant.now().minusSeconds(8 * 86400), Instant.now().minusSeconds(86400)));

        invitations.create(garden, "ines", Role.MEMBER, null);

        assertThat(repository.findByTokenHash(InvitationService.hash("old")).orElseThrow().getInvitedEmail()).isNull();
    }
}
