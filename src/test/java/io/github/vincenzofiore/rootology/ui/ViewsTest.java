package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.Route;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.facts.SourceCatalog;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The views build with the real beans; asking the pipeline is covered by GardenPipelineAgentTest. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ViewsTest {

    @Autowired
    ExperimentRunner runner;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    RunHistory history;

    @Autowired
    InvitationService invitations;

    @Autowired
    GardenService gardens;

    @Autowired
    CropCatalog crops;

    @Autowired
    io.github.vincenzofiore.rootology.weather.WeatherService weatherService;

    @Autowired
    io.github.vincenzofiore.rootology.experiments.GoldenRuns goldenRuns;

    @Autowired
    io.github.vincenzofiore.rootology.roles.RoleService roles;

    @Autowired
    io.github.vincenzofiore.rootology.usage.UsageService usage;

    @Autowired
    io.github.vincenzofiore.rootology.alerts.WeatherAlerts weatherAlerts;

    @Autowired
    io.github.vincenzofiore.rootology.notifications.NotificationService notificationService;

    @Autowired
    io.github.vincenzofiore.rootology.geocoding.GeoNames geoNames;

    @Autowired
    SourceCatalog sourceCatalog;

    @Autowired
    io.github.vincenzofiore.rootology.privacy.DataExport dataExport;

    @Autowired
    io.github.vincenzofiore.rootology.privacy.AccountDeletion accountDeletion;

    @Autowired
    com.vaadin.flow.spring.security.AuthenticationContext authentication;

    @Autowired
    io.github.vincenzofiore.rootology.spaces.IdentityService identityService;

    @Autowired
    io.github.vincenzofiore.rootology.enrichment.KnowledgeEnrichment enrichment;

    @Autowired
    io.github.vincenzofiore.rootology.facts.KnowledgeGraph knowledge;

    @Test
    void everyRouteAndItsLayoutDeclareAccess() {
        // Vaadin denies a view whose route or parent layout has no access annotation.
        for (Class<?> view : List.of(ComparisonView.class, EscalationView.class, HistoryView.class, SpaceView.class,
                GardenView.class, AskView.class, KnowledgeView.class, AdminView.class, NotificationsView.class, HomeView.class,
                AccountView.class)) {
            Route route = view.getAnnotation(Route.class);
            assertThat(view.isAnnotationPresent(PermitAll.class)
                    || view.isAnnotationPresent(jakarta.annotation.security.RolesAllowed.class)).as(view.getSimpleName()).isTrue();
            assertThat(route.layout().isAnnotationPresent(PermitAll.class)).as(route.layout().getSimpleName()).isTrue();
        }
    }

    private CurrentUser viewer() {
        return new CurrentUser(spaces, "views-test-user");
    }

    @Test
    void historyAndSpaceViewsBuild() {
        assertThat(new HistoryView(history, viewer()).getChildren()).isNotEmpty();
        assertThat(new SpaceView(spaces, invitations, viewer()).getChildren()).isNotEmpty();
        assertThat(new GardenView(gardens, crops, geoNames, weatherService, viewer()).getChildren()).isNotEmpty();
        assertThat(new AskView(pipeline, sourceCatalog, viewer(), enrichment, usage).getChildren()).isNotEmpty();
        assertThat(new SourcesView(sourceCatalog).getChildren()).isNotEmpty();
        assertThat(new HomeView(gardens, weatherService, notificationService, pipeline, viewer()).getChildren()).isNotEmpty();
        assertThat(new NotificationsView(notificationService, viewer()).getChildren()).isNotEmpty();
        assertThat(new AccountView(viewer(), dataExport, accountDeletion, authentication, identityService, "not-set")
                .getChildren()).isNotEmpty();
        assertThat(new KnowledgeView(enrichment, crops, knowledge, viewer()).getChildren()).isNotEmpty();
        assertThat(AdminView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("ADMIN");

        assertThat(SourcesView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class)).isTrue();
    }

    @Autowired
    io.github.vincenzofiore.rootology.privacy.Retention retention;

    @Test
    void thePrivacyPageNamesTheControllerOnlyWhenConfigured() {
        // Without a Vaadin session the texts stay keys; the wording is checked through Translations below.
        String configured = new PrivacyView(
                new io.github.vincenzofiore.rootology.privacy.PrivacyContact("Ada Lovelace", "ada@example.org"), retention)
                .getElement().getTextRecursively();
        assertThat(configured).contains("privacy.who.text").doesNotContain("privacy.who.missing");
        String unset = new PrivacyView(new io.github.vincenzofiore.rootology.privacy.PrivacyContact("", ""), retention)
                .getElement().getTextRecursively();
        assertThat(unset).contains("privacy.who.missing");
        assertThat(PrivacyView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class)).isTrue();
        // Public pages need a public layout too: Vaadin denies a route whose layout a visitor may not see.
        for (Class<?> page : List.of(PrivacyView.class, SourcesView.class)) {
            Class<?> layout = page.getAnnotation(Route.class).layout();
            assertThat(layout).as(page.getSimpleName()).isEqualTo(PublicLayout.class);
            assertThat(layout.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class)).isTrue();
        }

        Translations translations = new Translations();
        assertThat(translations.getTranslation("privacy.who.text", java.util.Locale.ITALIAN, "Ada Lovelace", "ada@example.org"))
                .contains("Ada Lovelace", "ada@example.org");
        assertThat(translations.getTranslation("privacy.kept.history", java.util.Locale.ITALIAN, retention.history().toDays()))
                .contains("365 giorni");
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "views-test-user", roles = "ADMIN")
    void adminViewShowsPeopleAndHistoryToAnAdmin() {
        assertThat(new AdminView(roles, usage, weatherAlerts, viewer()).getChildren().filter(Grid.class::isInstance).count()).isEqualTo(2);
    }

    @Test
    void comparisonViewBuildsAndIsForQaOnly() {
        assertThat(new ComparisonView(runner, pipeline, goldenRuns, viewer()).getChildren()).isNotEmpty();
        assertThat(ComparisonView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("QA");
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "views-test-user", roles = "QA")
    void historyShowsTheArbiterToQaOnly() {
        long suiteColumnsForQa = new HistoryView(history, viewer()).getChildren().filter(Grid.class::isInstance)
                .map(Grid.class::cast).flatMap(g -> g.getColumns().stream())
                .filter(c -> ((Grid.Column<?>) c).isVisible()).count();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        long columnsForMember = new HistoryView(history, viewer()).getChildren().filter(Grid.class::isInstance)
                .map(Grid.class::cast).flatMap(g -> g.getColumns().stream())
                .filter(c -> ((Grid.Column<?>) c).isVisible()).count();

        assertThat(suiteColumnsForQa - columnsForMember).as("case, input, Jena, matches").isEqualTo(4);
    }

    @Test
    void escalationViewListsPendingReviews() {
        EscalationView view = new EscalationView(pipeline, viewer());

        assertThat(view.getChildren().anyMatch(Grid.class::isInstance)).isTrue();
    }

    @Test
    void theWelcomeAndLoggedOutPagesOfferTheSameWaysIn() {
        assertThat(hrefs(new WelcomeView(authentication, "not-set")))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE);
        assertThat(hrefs(new WelcomeView(authentication, "1234.apps.googleusercontent.com")))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE, SecurityConfiguration.GOOGLE_LOGIN_PAGE);
        assertThat(hrefs(new LoggedOutView("not-set")))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE);
        assertThat(hrefs(new LoggedOutView("1234.apps.googleusercontent.com")))
                .containsExactly(SecurityConfiguration.GITHUB_LOGIN_PAGE, SecurityConfiguration.GOOGLE_LOGIN_PAGE);
    }

    private static List<String> hrefs(Component view) {
        SignInButtons buttons = view.getChildren().filter(SignInButtons.class::isInstance)
                .map(SignInButtons.class::cast).findFirst().orElseThrow();
        return buttons.getChildren().filter(Anchor.class::isInstance).map(Component::getElement)
                .map(element -> element.getAttribute("href")).toList();
    }
}
