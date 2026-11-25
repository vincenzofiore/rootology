package app.rootology.ui;

import app.rootology.TestcontainersConfiguration;
import app.rootology.access.AccountBlockService;
import app.rootology.spaces.IdentityProvider;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A whole account blocked by an admin (#165): it signs in with {@code ROLE_BLOCKED} alone, is sent to the
 * blocked page from every route but the public ones, and an open session of an account blocked meanwhile stops
 * at its next navigation. Being a member of nothing, it has no space to ask a question from, so nothing reaches
 * Claude or Jev. Each test uses its own logins.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BlockedAccountTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    IdentityService identities;

    @Autowired
    AllowListOAuth2UserService signIn;

    @Autowired
    GoogleOidcUserService googleSignIn;

    @Autowired
    AccountBlockService blocks;

    @Autowired
    AccessRouting routing;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static OAuth2User githubUser(long id, String login) {
        return new DefaultOAuth2User(AuthorityUtils.createAuthorityList("OAUTH2_USER"),
                Map.of("id", id, "login", login), "id");
    }

    private static OidcUser googleUser(String sub, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("email", email);
        claims.put("email_verified", true);
        OidcIdToken idToken = new OidcIdToken("an-id-token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", sub));
        return new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken,
                new OidcUserInfo(claims), "sub");
    }

    /** Blocks the account the way an admin does: the service asks for the ADMIN authority, which the test lends. */
    private void block(String admin, String login) {
        var before = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null,
                AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        try {
            blocks.block(admin, login, "domande fuori tema");
        } finally {
            SecurityContextHolder.getContext().setAuthentication(before);
        }
    }

    private static void as(OAuth2User principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void aBlockedAccountSignsInWithTheBlockedAuthorityAlone() {
        spaces.signedIn("blk-sign-sam");
        identities.link("blk-sign-sam", IdentityProvider.GITHUB, "881001", "sam@example.org");
        block("blk-sign-admin", "blk-sign-sam");
        String firstLoginAt = jdbc.sql("select first_login_at::text from app_user where login = 'blk-sign-sam'")
                .query(String.class).single();

        OAuth2User principal = signIn.signIn(githubUser(881001, "blk-sign-sam"), GitHubEmails.Verified.NONE, null);

        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(BlockedAccess.AUTHORITY);
        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("blk-sign-sam");
        assertThat(jdbc.sql("select first_login_at::text from app_user where login = 'blk-sign-sam'")
                .query(String.class).single()).as("nothing is written for a blocked sign-in").isEqualTo(firstLoginAt);
        assertThat(jdbc.sql("select count(*) from user_identity where subject = '881001'").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void aBlockedAccountSignsInWithGoogleWithTheBlockedAuthorityAlone() {
        spaces.signedIn("blk-sign-gio");
        identities.link("blk-sign-gio", IdentityProvider.GOOGLE, "gio-sub-1", "gio@example.org");
        block("blk-sign-admin", "blk-sign-gio");

        OidcUser principal = googleSignIn.signIn(googleUser("gio-sub-1", "gio@example.org"), null);

        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(BlockedAccess.AUTHORITY);
        assertThat(principal.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE)).isEqualTo("blk-sign-gio");
    }

    @Test
    void aBlockedPersonIsSentToTheBlockedPageFromEveryOtherRoute() {
        spaces.signedIn("blk-sign-tom");
        identities.link("blk-sign-tom", IdentityProvider.GITHUB, "881002", "tom@example.org");
        block("blk-sign-admin", "blk-sign-tom");
        as(signIn.signIn(githubUser(881002, "blk-sign-tom"), GitHubEmails.Verified.NONE, null));

        for (Class<?> target : List.of(GardensView.class, AskView.class, StartView.class, AccountView.class,
                AdminView.class, NotificationsView.class)) {
            var event = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
            when(event.getNavigationTarget()).thenReturn((Class) target);
            routing.beforeEnter(event);
            verify(event).rerouteTo(BlockedView.class);
        }
    }

    @Test
    void aBlockedPersonStillReadsThePublicPagesAndHisOwn() {
        spaces.signedIn("blk-pub-yan");
        identities.link("blk-pub-yan", IdentityProvider.GITHUB, "881003", "yan@example.org");
        block("blk-sign-admin", "blk-pub-yan");
        as(signIn.signIn(githubUser(881003, "blk-pub-yan"), GitHubEmails.Verified.NONE, null));

        for (Class<?> target : List.of(BlockedView.class, SourcesView.class, PrivacyView.class, WelcomeView.class,
                LoggedOutView.class)) {
            var event = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
            when(event.getNavigationTarget()).thenReturn((Class) target);
            routing.beforeEnter(event);
            verify(event, never()).rerouteTo(any(Class.class));
        }
    }

    @Test
    void anOpenSessionOfAnAccountBlockedMeanwhileStopsAtItsNextNavigation() {
        spaces.signedIn("blk-open-uma");
        identities.link("blk-open-uma", IdentityProvider.GITHUB, "881004", "uma@example.org");
        OAuth2User admitted = signIn.signIn(githubUser(881004, "blk-open-uma"), GitHubEmails.Verified.NONE, null);
        as(admitted);
        assertThat(admitted.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .as("an admitted person, before the block").contains("OAUTH2_USER");

        var before = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
        when(before.getNavigationTarget()).thenReturn((Class) GardensView.class);
        routing.beforeEnter(before);
        verify(before, never()).rerouteTo(any(Class.class));

        block("blk-sign-admin", "blk-open-uma");

        var after = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
        when(after.getNavigationTarget()).thenReturn((Class) GardensView.class);
        routing.beforeEnter(after);
        verify(after).rerouteTo(BlockedView.class);

        var sources = mock(com.vaadin.flow.router.BeforeEnterEvent.class);
        when(sources.getNavigationTarget()).thenReturn((Class) SourcesView.class);
        routing.beforeEnter(sources);
        verify(sources, never()).rerouteTo(any(Class.class));
    }

    @Test
    void aBlockedPersonIsAMemberOfNothingSoItCannotAsk() {
        spaces.signedIn("blk-ask-zoe");
        block("blk-sign-admin", "blk-ask-zoe");

        CurrentUser blocked = new CurrentUser(spaces, "blk-ask-zoe", true);

        assertThat(blocked.pending()).isTrue();
        assertThat(blocked.spaces()).isEmpty();
        assertThat(blocked.chosenName()).isEmpty();
        assertThat(blocked.memberOf(app.rootology.TestSpaces.personal(spaces, "blk-ask-zoe"))).isFalse();
        assertThat(blocked.canContribute(app.rootology.TestSpaces.personal(spaces, "blk-ask-zoe"))).isFalse();
        assertThatThrownBy(blocked::space).isInstanceOf(SpaceAccessException.class);
    }
}
