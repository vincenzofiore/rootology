package app.rootology.spaces;

import app.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserIdentityRepositoryTest {

    @Autowired
    AppUserRepository users;

    @Autowired
    UserIdentityRepository identities;

    @Test
    @Transactional
    void anIdentityIsSavedAndReadBack() {
        AppUser ada = users.save(new AppUser("ident-ada", Instant.now()));
        Instant linkedAt = Instant.parse("2026-11-06T10:00:00Z");

        identities.saveAndFlush(new UserIdentity(ada, IdentityProvider.GOOGLE, "google-ada", "ada@example.org", linkedAt));

        assertThat(identities.findByProviderAndSubject(IdentityProvider.GOOGLE, "google-ada")).get()
                .satisfies(identity -> {
                    assertThat(identity.getUser().getId()).isEqualTo(ada.getId());
                    assertThat(identity.getProvider()).isEqualTo(IdentityProvider.GOOGLE);
                    assertThat(identity.getSubject()).isEqualTo("google-ada");
                    assertThat(identity.getEmail()).isEqualTo("ada@example.org");
                    assertThat(identity.getLinkedAt()).isEqualTo(linkedAt);
                });
    }

    @Test
    void aProviderAndSubjectBelongToOneIdentityOnly() {
        AppUser one = users.save(new AppUser("ident-bea", Instant.now()));
        AppUser other = users.save(new AppUser("ident-cam", Instant.now()));
        Instant linkedAt = Instant.now();
        identities.saveAndFlush(new UserIdentity(one, IdentityProvider.GITHUB, "github-bea", null, linkedAt));

        assertThatThrownBy(() ->
                identities.saveAndFlush(new UserIdentity(other, IdentityProvider.GITHUB, "github-bea", null, linkedAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
