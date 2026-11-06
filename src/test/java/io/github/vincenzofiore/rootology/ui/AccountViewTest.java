package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.privacy.AccountDeletion;
import io.github.vincenzofiore.rootology.privacy.DataExport;
import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.LinkedIdentity;
import io.github.vincenzofiore.rootology.spaces.Role;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.spaces.SpaceSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The sign-in accounts of My account (#28): what is offered depends on what the person already has. */
class AccountViewTest {

    private static final String GOOGLE_CLIENT_ID = "1234.apps.googleusercontent.com";

    private static final LinkedIdentity GITHUB =
            new LinkedIdentity(IdentityProvider.GITHUB, "ada@example.org", Instant.parse("2026-11-01T10:00:00Z"));
    private static final LinkedIdentity GOOGLE =
            new LinkedIdentity(IdentityProvider.GOOGLE, "ada@gmail.com", Instant.parse("2026-11-05T10:00:00Z"));

    private static AccountView view(List<LinkedIdentity> identities, String googleClientId) {
        SpaceService spaces = mock(SpaceService.class);
        when(spaces.spacesOf("ada")).thenReturn(List.of(new SpaceSummary(1, "ada’s garden", Role.OWNER, true)));
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.linkedTo("ada")).thenReturn(identities);
        AccountDeletion deletion = mock(AccountDeletion.class);
        when(deletion.preview("ada")).thenReturn(new AccountDeletion.Preview(List.of(), List.of(), false));
        return new AccountView(new CurrentUser(spaces, "ada"), mock(DataExport.class), deletion,
                mock(AuthenticationContext.class), identityService, googleClientId);
    }

    private static List<String> buttonIds(AccountView view) {
        return all(view).filter(Button.class::isInstance).map(Component::getId)
                .flatMap(Optional::stream).toList();
    }

    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(AccountViewTest::all));
    }

    @Test
    void aPersonWithOneAccountIsOfferedTheMissingProviderAndNoUnlink() {
        AccountView view = view(List.of(GITHUB), GOOGLE_CLIENT_ID);

        assertThat(buttonIds(view)).containsExactly("link-google");
        assertThat(buttonIds(view)).doesNotContain("unlink-github");
    }

    @Test
    void googleIsOfferedOnlyWhenItIsConfigured() {
        assertThat(buttonIds(view(List.of(GITHUB), "not-set"))).isEmpty();
        assertThat(buttonIds(view(List.of(GOOGLE), "not-set"))).containsExactly("link-github");
    }

    @Test
    void aPersonWithTwoAccountsCanUnlinkEitherAndHasNothingToLink() {
        AccountView view = view(List.of(GITHUB, GOOGLE), GOOGLE_CLIENT_ID);

        assertThat(buttonIds(view)).containsExactlyInAnyOrder("unlink-github", "unlink-google");
        assertThat(buttonIds(view)).noneMatch(id -> id.startsWith("link-"));
    }

    @Test
    void anAccountWithoutAVerifiedAddressIsStillShown() {
        AccountView view = view(List.of(new LinkedIdentity(IdentityProvider.GITHUB, null, Instant.now())),
                GOOGLE_CLIENT_ID);

        // Without a Vaadin session the texts stay keys; the section exists either way.
        assertThat(view.getElement().getTextRecursively()).contains("account.signin.noemail");
    }
}
