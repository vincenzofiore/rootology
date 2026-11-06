package io.github.vincenzofiore.rootology.spaces;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class IdentityServiceTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    IdentityService identities;

    @Test
    void aLinkedAccountFindsThePersonAndIsLinkedOnlyOnce() {
        spaces.signedIn("link-ada");

        identities.link("link-ada", IdentityProvider.GITHUB, "9001", "ada@example.org");
        assertThatCode(() -> identities.link("LINK-Ada", IdentityProvider.GITHUB, "9001", "ada@example.org"))
                .doesNotThrowAnyException();

        assertThat(identities.loginOf(IdentityProvider.GITHUB, "9001")).contains("link-ada");
        assertThat(identities.loginOf(IdentityProvider.GITHUB, "9002")).isEmpty();
        assertThat(identities.hasIdentity("link-ada", IdentityProvider.GITHUB)).isTrue();
        assertThat(identities.hasIdentity("link-ada", IdentityProvider.GOOGLE)).isFalse();
        assertThat(identities.hasIdentity("link-nobody", IdentityProvider.GITHUB)).isFalse();
    }

    @Test
    void anAccountOfOneProviderBelongsToOnePersonOnly() {
        spaces.signedIn("link-bea");
        spaces.signedIn("link-cam");
        identities.link("link-bea", IdentityProvider.GITHUB, "9003", null);

        assertThatThrownBy(() -> identities.link("link-cam", IdentityProvider.GITHUB, "9003", null))
                .isInstanceOf(SpaceAccessException.class);
        assertThat(identities.loginOf(IdentityProvider.GITHUB, "9003")).contains("link-bea");
    }

    @Test
    void aPersonKeepsOneAccountPerProvider() {
        spaces.signedIn("link-dan");
        identities.link("link-dan", IdentityProvider.GITHUB, "9004", null);

        assertThatThrownBy(() -> identities.link("link-dan", IdentityProvider.GITHUB, "9005", null))
                .isInstanceOf(SpaceAccessException.class);
        assertThat(identities.loginOf(IdentityProvider.GITHUB, "9005")).isEmpty();
    }
}
