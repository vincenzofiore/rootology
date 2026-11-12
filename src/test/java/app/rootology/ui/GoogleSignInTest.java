package app.rootology.ui;

import app.rootology.TestcontainersConfiguration;
import app.rootology.notifications.NotificationService;
import app.rootology.roles.RoleService;
import app.rootology.spaces.IdentityProvider;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sign in with Google (#28) on a real database: a person is recognised by Google's {@code sub}, never by email
 * or by login, and a new person is created only once admitted. Each test uses its own addresses and ids.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GoogleSignInTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    IdentityService identities;

    @Autowired
    InvitationService invitations;

    @Autowired
    RoleService roles;

    @Autowired
    NotificationService notifications;

    @Autowired
    app.rootology.access.AccessRequestService accessRequests;

    @Autowired
    JdbcClient jdbc;

    private static OidcUser googleUser(String sub, String email, Map<String, Object> extra) {
        Map<String, Object> claims = new HashMap<>(extra);
        claims.put("sub", sub);
        if (email != null) {
            claims.put("email", email);
        }
        OidcIdToken idToken = new OidcIdToken("an-id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", sub));
        return new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken,
                new OidcUserInfo(claims), "sub");
    }

    /** A Google account whose address Google verified. */
    private static OidcUser googleUser(String sub, String email) {
        return googleUser(sub, email, Map.of("email_verified", true));
    }

    private GoogleOidcUserService service(String... allowedEmails) {
        return new GoogleOidcUserService(Set.of(allowedEmails),
                new SignInSteps(spaces, identities, invitations, roles, notifications, accessRequests), spaces, identities, invitations);
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private Optional<Map<String, Object>> identity(String login, IdentityProvider provider) {
        return jdbc.sql("""
                        select i.subject, i.email from user_identity i join app_user u on u.id = i.user_id
                        where u.login = :login and i.provider = :provider""")
                .param("login", login).param("provider", provider.id()).query().listOfRows().stream().findFirst();
    }

    @Test
    void someoneOnTheBootstrapListGetsAPersonAnIdentityAndAPersonalSpace() {
        OidcUser principal = service("gio@example.org")
                .signIn(googleUser("google-101", "Gio@Example.org"), null);

        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("gio");
        assertThat(principal.getName()).isEqualTo("gio");
        assertThat(count("select count(*) from app_user where login = 'gio'")).isEqualTo(1);
        assertThat(identity("gio", IdentityProvider.GOOGLE)).get()
                .satisfies(row -> {
                    assertThat(row.get("subject")).isEqualTo("google-101");
                    assertThat(row.get("email")).isEqualTo("gio@example.org");
                });
        assertThat(spaces.spacesOf("gio")).extracting(s -> s.name()).containsExactly("gio");
        assertThat(jdbc.sql("select email from notification_preference where github_login = 'gio'")
                .query(String.class).single()).isEqualTo("gio@example.org");
    }

    @Test
    void theSameSubjectSignsInAgainAsTheSamePerson() {
        service("via@example.org").signIn(googleUser("google-102", "via@example.org"), null);

        OidcUser again = service().signIn(googleUser("google-102", "via@example.org"), null);

        assertThat(again.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("via");
        assertThat(count("select count(*) from app_user where login like 'via%'")).isEqualTo(1);
        assertThat(identities.loginOf(IdentityProvider.GOOGLE, "google-102")).contains("via");
    }

    @Test
    void anInvitationBoundToTheVerifiedAddressAdmitsThem() {
        spaces.signedIn("gowner");
        long shared = spaces.spacesOf("gowner").getFirst().id();
        String token = invitations.create(shared, "gowner", Role.MEMBER, null, "invitee@example.org");

        OidcUser principal = service().signIn(googleUser("google-103", "invitee@example.org"), token);

        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("invitee");
        assertThat(spaces.spacesOf("invitee")).extracting(s -> s.id()).contains(shared);
    }

    @Test
    void anInvitationBoundToAGitHubLoginIsNotUsedByGoogle() {
        spaces.signedIn("ghowner");
        long shared = spaces.spacesOf("ghowner").getFirst().id();
        String token = invitations.create(shared, "ghowner", Role.MEMBER, "some-github-name");

        // #65: the invitation is not for a Google person, and without an admission they sign in as pending.
        OidcUser principal = service().signIn(googleUser("google-104", "somegithubname@example.org"), token);

        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_PENDING");
        assertThat(count("select count(*) from app_user where login like 'somegithubname%'")).isZero();
        assertThat(count("select count(*) from user_identity where subject = 'google-104'")).isZero();
        assertThat(count("select count(*) from space_member where space_id = " + shared)).isEqualTo(1);
    }

    @Test
    void anAddressGoogleDidNotVerifyIsRefused() {
        OidcUser unverified = googleUser("google-105", "unverified@example.org", Map.of("email_verified", false));
        OidcUser missing = googleUser("google-106", "gsi-missing@example.org", Map.of());

        assertThatThrownBy(() -> service("unverified@example.org").signIn(unverified, null))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> service("gsi-missing@example.org").signIn(missing, null))
                .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(count("select count(*) from app_user where login like 'unverified%'")).isZero();
        assertThat(count("select count(*) from app_user where login like 'gsi-missing%'")).isZero();
        assertThat(count("select count(*) from user_identity where subject in ('google-105', 'google-106')")).isZero();
    }

    @Test
    void someoneWithNeitherListNorInvitationSignsInAsPendingAndLeavesNothingBehind() {
        // #65: not admitted, but signed in with the pending authority alone, to ask for an invitation.
        OidcUser principal = service("someone-else@example.org")
                .signIn(googleUser("google-107", "gsi-stranger@example.org"), null);

        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_PENDING");
        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("gsi-stranger");
        assertThat(count("select count(*) from app_user where login like 'gsi-stranger%'")).isZero();
        assertThat(count("select count(*) from user_identity where subject = 'google-107'")).isZero();
        assertThat(count("select count(*) from notification_preference where github_login like 'gsi-stranger%'")).isZero();
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void anApprovedGooglePersonIsAdmittedAndItsRequestIsDeleted() {
        accessRequests.request(IdentityProvider.GOOGLE, "google-111", "approved", "approved@example.org", "please");
        long request = jdbc.sql("select id from access_request where subject = 'google-111'").query(Long.class).single();
        accessRequests.approve("admin", request);

        OidcUser principal = service().signIn(googleUser("google-111", "approved@example.org"), null);

        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("approved");
        assertThat(count("select count(*) from app_user where login = 'approved'")).isEqualTo(1);
        assertThat(spaces.spacesOf("approved")).isNotEmpty();
        assertThat(count("select count(*) from access_request where subject = 'google-111'")).isZero();
    }

    @Test
    void anExistingGitHubPersonWithTheSameAddressIsNotEntered() {
        spaces.signedIn("git-marco");
        identities.link("git-marco", IdentityProvider.GITHUB, "github-108", "gsi-marco@example.org");

        // Not admitted: a Google account is never merged with an existing person by its address (#65: pending).
        OidcUser pending = service().signIn(googleUser("google-109", "gsi-marco@example.org"), null);
        assertThat(pending.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_PENDING");

        assertThat(count("select count(*) from user_identity where subject = 'google-109'")).isZero();
        assertThat(identity("git-marco", IdentityProvider.GITHUB)).get()
                .satisfies(row -> assertThat(row.get("subject")).isEqualTo("github-108"));
        assertThat(count("select count(*) from user_identity i join app_user u on u.id = i.user_id "
                + "where u.login = 'git-marco'")).isEqualTo(1);

        // Admitted, a separate person is created: the GitHub one keeps its account and its single identity.
        OidcUser principal = service("gsi-marco@example.org").signIn(googleUser("google-109", "gsi-marco@example.org"), null);

        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("gsi-marco");
        assertThat(identity("gsi-marco", IdentityProvider.GOOGLE)).get()
                .satisfies(row -> assertThat(row.get("subject")).isEqualTo("google-109"));
        assertThat(count("select count(*) from user_identity i join app_user u on u.id = i.user_id "
                + "where u.login = 'git-marco'")).isEqualTo(1);
        assertThat(jdbc.sql("select id from app_user where login = 'gsi-marco'").query(Long.class).single())
                .isNotEqualTo(jdbc.sql("select id from app_user where login = 'git-marco'").query(Long.class).single());
    }

    @Test
    void aGitHubAccountCannotTakeTheLoginDerivedForAGooglePerson() {
        service("gsi-gina@example.org").signIn(googleUser("google-110", "gsi-gina@example.org"), null);

        AllowListOAuth2UserService github = new AllowListOAuth2UserService(Set.of("gsi-gina"), spaces, identities,
                new SignInSteps(spaces, identities, invitations, roles, notifications, accessRequests), null);

        assertThatThrownBy(() -> github.signIn(githubUser(111, "gsi-gina"), GitHubEmails.Verified.NONE, null))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(count("select count(*) from user_identity where subject = '111'")).isZero();
        assertThat(count("select count(*) from app_user where login = 'gsi-gina'")).isEqualTo(1);
    }

    @Test
    void aLoginComesFromTheAddressByRule() {
        assertThat(GoogleOidcUserService.deriveLogin("Mario.Rossi+orto@gmail.com")).isEqualTo("mario.rossiorto");
        assertThat(GoogleOidcUserService.deriveLogin("città@example.org")).isEqualTo("citt");
        assertThat(GoogleOidcUserService.deriveLogin("...@example.org")).isEqualTo("...");
        assertThat(GoogleOidcUserService.deriveLogin("+!£@example.org")).isEqualTo("user");
        assertThat(GoogleOidcUserService.deriveLogin("a".repeat(50) + "@example.org")).hasSize(39);
    }

    @Test
    void aTakenLoginGetsTheNextFreeNumber() {
        spaces.signedIn("mario");
        spaces.signedIn("mario2");

        assertThat(identities.freeLogin("mario")).isEqualTo("mario3");
        assertThat(identities.freeLogin("nobody-has-this")).isEqualTo("nobody-has-this");
    }

    private static OAuth2User githubUser(long id, String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("id", id, "login", login), "id");
    }
}
