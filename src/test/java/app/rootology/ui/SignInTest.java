package app.rootology.ui;

import com.vaadin.flow.spring.security.AuthenticationContext;
import app.rootology.TestcontainersConfiguration;
import app.rootology.notifications.NotificationService;
import app.rootology.roles.RoleService;
import app.rootology.spaces.IdentityProvider;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sign-in on a real database (#28): a person is found by GitHub's stable id, so a rename keeps their spaces and
 * a freed GitHub login does not open someone else's account. Each test uses its own logins.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SignInTest {

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

    private static final GitHubEmails.Verified VERIFIED =
            new GitHubEmails.Verified(Set.of("ada@example.org"), "ada@example.org");

    private static OAuth2User githubUser(long id, String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("id", id, "login", login), "id");
    }

    private AllowListOAuth2UserService service(String... allowed) {
        return new AllowListOAuth2UserService(Set.of(allowed), spaces, identities,
                new SignInSteps(spaces, identities, invitations, roles, notifications, accessRequests), mock(GitHubEmails.class));
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
    void anExistingPersonWithoutAnIdentityRecordsItAtTheNextSignIn() {
        spaces.signedIn("sign-ada");

        OAuth2User principal = service("sign-ada").signIn(githubUser(101, "sign-ada"), VERIFIED, null);

        assertThat(principal.getName()).isEqualTo("sign-ada");
        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("sign-ada");
        assertThat(identity("sign-ada", IdentityProvider.GITHUB)).get()
                .satisfies(row -> {
                    assertThat(row.get("subject")).isEqualTo("101");
                    assertThat(row.get("email")).isEqualTo("ada@example.org");
                });
        assertThat(count("select count(*) from app_user where login = 'sign-ada'")).isEqualTo(1);
    }

    @Test
    void aRenamedGitHubAccountKeepsItsLoginAndItsSpaces() {
        service("sign-bea").signIn(githubUser(102, "sign-bea"), GitHubEmails.Verified.NONE, null);
        long personal = spaces.spacesOf("sign-bea").getFirst().id();

        OAuth2User principal = service("sign-bea").signIn(githubUser(102, "sign-bea-renamed"), GitHubEmails.Verified.NONE, null);

        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("sign-bea");
        assertThat(spaces.spacesOf("sign-bea")).extracting(s -> s.id()).containsExactly(personal);
        assertThat(count("select count(*) from app_user where login like 'sign-bea%'")).isEqualTo(1);
        assertThat(identity("sign-bea", IdentityProvider.GITHUB)).get()
                .satisfies(row -> assertThat(row.get("subject")).isEqualTo("102"));
    }

    @Test
    void whoeverTakesAFreedGitHubLoginOfAnotherPersonIsRefused() {
        service("sign-cam").signIn(githubUser(103, "sign-cam"), GitHubEmails.Verified.NONE, null);
        String firstLoginAt = jdbc.sql("select first_login_at::text from app_user where login = 'sign-cam'")
                .query(String.class).single();

        // Someone else's GitHub account now holds the name this person left behind.
        assertThatThrownBy(() -> service("sign-cam").signIn(githubUser(104, "sign-cam"), VERIFIED, null))
                .isInstanceOf(OAuth2AuthenticationException.class);

        assertThat(identity("sign-cam", IdentityProvider.GITHUB)).get()
                .satisfies(row -> assertThat(row.get("subject")).isEqualTo("103"));
        assertThat(count("select count(*) from user_identity where subject = '104'")).isZero();
        assertThat(count("select count(*) from app_user where login = 'sign-cam'")).isEqualTo(1);
        assertThat(jdbc.sql("select first_login_at::text from app_user where login = 'sign-cam'")
                .query(String.class).single()).isEqualTo(firstLoginAt);
    }

    @Test
    void someoneOnTheAllowListGetsAPersonAndAnIdentity() {
        OAuth2User principal = service("sign-new").signIn(githubUser(105, "sign-new"), VERIFIED, null);

        assertThat(principal.getName()).isEqualTo("sign-new");
        assertThat(count("select count(*) from app_user where login = 'sign-new'")).isEqualTo(1);
        assertThat(identity("sign-new", IdentityProvider.GITHUB)).get()
                .satisfies(row -> assertThat(row.get("subject")).isEqualTo("105"));
        assertThat(spaces.spacesOf("sign-new")).extracting(s -> s.name()).containsExactly("sign-new");
        assertThat(jdbc.sql("select email from notification_preference where github_login = 'sign-new'")
                .query(String.class).single()).isEqualTo("ada@example.org");
    }

    @Test
    void someoneNotAllowedSignsInAsPendingAndLeavesNothingBehind() {
        // #65: not admitted, but signed in with the pending authority alone, to ask for an invitation.
        OAuth2User principal = service("sign-other").signIn(githubUser(106, "sign-stranger"), VERIFIED, null);

        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority()).containsExactly("ROLE_PENDING");
        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("sign-stranger");
        assertThat(count("select count(*) from app_user where login = 'sign-stranger'")).isZero();
        assertThat(count("select count(*) from user_identity where subject = '106'")).isZero();
        assertThat(count("select count(*) from notification_preference where github_login = 'sign-stranger'")).isZero();
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void anApprovedIdentityIsAdmittedAndItsRequestIsDeleted() {
        accessRequests.request(IdentityProvider.GITHUB, "108", "sign-approved", "ada@example.org", "please");

        long approved = jdbc.sql("select id from access_request where subject = '108'").query(Long.class).single();
        accessRequests.approve("admin", approved);

        OAuth2User principal = service().signIn(githubUser(108, "sign-approved"), VERIFIED, null);

        assertThat(principal.getAuthorities()).extracting(a -> a.getAuthority())
                .contains("OAUTH2_USER").doesNotContain("ROLE_PENDING");
        assertThat(count("select count(*) from app_user where login = 'sign-approved'")).isEqualTo(1);
        assertThat(identity("sign-approved", IdentityProvider.GITHUB)).get()
                .satisfies(row -> assertThat(row.get("subject")).isEqualTo("108"));
        assertThat(spaces.spacesOf("sign-approved")).isNotEmpty();
        assertThat(count("select count(*) from access_request where subject = '108'")).isZero();
    }

    @Test
    void theCurrentUserReadsTheLoginRootologyPutOnThePrincipal() {
        OAuth2User principal = service("sign-gil").signIn(githubUser(107, "sign-gil"), GitHubEmails.Verified.NONE, null);
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.getAuthenticatedUser(OAuth2User.class)).thenReturn(Optional.of(principal));

        assertThat(new CurrentUser(spaces, authentication).login()).isEqualTo("sign-gil");
    }
}
