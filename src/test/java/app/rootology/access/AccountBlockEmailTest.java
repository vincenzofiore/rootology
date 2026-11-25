package app.rootology.access;

import app.rootology.TestcontainersConfiguration;
import app.rootology.mail.OutgoingMail;
import app.rootology.spaces.IdentityProvider;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The emails of a block (#165) with a real SMTP server (Mailpit) catching what is sent: the person hears that
 * the account is blocked, why when the admin wrote a reason, and how to ask for a review; and that it works
 * again after an unblock. Addressed to the verified address the app knows, and to none when it knows none.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountBlockEmailTest {

    @Autowired
    AccountBlockService blocks;

    @Autowired
    SpaceService spaces;

    @Autowired
    IdentityService identities;

    @Value("${rootology.test.mailpit-api}")
    String mailpit;

    @Value("${rootology.privacy.contact}")
    String contact;

    /** The message to this address with this subject, waiting for the background send. */
    private JsonNode message(String address, String subject) throws InterruptedException {
        RestClient api = RestClient.create(mailpit);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            JsonNode found = JsonMapper.builder().build().readTree(api.get()
                    .uri(u -> u.path("/api/v1/search").queryParam("query", "to:\"" + address + "\"").build())
                    .retrieve().body(String.class));
            for (JsonNode summary : found.path("messages")) {
                JsonNode message = JsonMapper.builder().build().readTree(api.get()
                        .uri("/api/v1/message/" + summary.path("ID").asString())
                        .retrieve().body(String.class));
                if (subject.equals(message.path("Subject").asString())) {
                    return message;
                }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("No email \"" + subject + "\" to " + address + " within 20 seconds");
    }

    @Test
    @WithMockUser(username = "blkmail-admin", roles = "ADMIN")
    void theBlockAndTheUnblockAreEmailedInItalian() throws Exception {
        spaces.signedIn("blkmail-ada");
        identities.link("blkmail-ada", IdentityProvider.GITHUB, "772001", "blkmail-ada@example.org");

        assertThat(blocks.block("blkmail-admin", "blkmail-ada", "domande fuori tema")).isTrue();

        JsonNode blocked = message("blkmail-ada@example.org", "Il tuo account Rootology è bloccato");
        assertThat(blocked.path("Text").asString()).contains("bloccato", "Motivo: domande fuori tema",
                contact, "non sono stati cancellati");
        assertThat(blocked.path("HTML").asString()).contains("domande fuori tema", contact);

        assertThat(blocks.unblock("blkmail-admin", "blkmail-ada")).isTrue();

        JsonNode unblocked = message("blkmail-ada@example.org", "Il tuo account Rootology è di nuovo attivo");
        assertThat(unblocked.path("Text").asString()).contains("di nuovo attivo");
    }

    @Test
    @WithMockUser(username = "blkmail-admin", roles = "ADMIN")
    void aBlockWithoutAReasonAndWithoutAnAddress() {
        spaces.signedIn("blkmail-bo");

        assertThat(blocks.block("blkmail-admin", "blkmail-bo", null)).isFalse();
        assertThat(blocks.unblock("blkmail-admin", "blkmail-bo")).isFalse();
    }

    @Test
    void aDeploymentWithoutAContactSaysToReply() {
        OutgoingMail mail = AccountBlockEmail.blocked("someone@example.org", null, null);

        assertThat(mail.text()).contains("rispondi a questa email").doesNotContain("Motivo:");
        assertThat(mail.html()).contains("rispondi a questa email");
    }
}
