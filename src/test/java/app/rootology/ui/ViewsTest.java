package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import app.rootology.TestcontainersConfiguration;
import app.rootology.experiments.ExperimentRunner;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.history.RunHistory;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.spaces.InvitationService;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.spaces.SpaceSummary;
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

    /** The map's style URL in tests: never fetched, the map is only inspected as data (#66). */
    private static final String STYLE = "https://tiles.openfreemap.org/styles/liberty";

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
    ClimateCells cells;

    @Autowired
    app.rootology.weather.WeatherService weatherService;

    @Autowired
    app.rootology.experiments.GoldenRuns goldenRuns;

    @Autowired
    app.rootology.roles.RoleService roles;

    @Autowired
    app.rootology.usage.UsageService usage;

    @Autowired
    app.rootology.alerts.WeatherAlerts weatherAlerts;

    @Autowired
    app.rootology.notifications.NotificationService notificationService;

    @Autowired
    app.rootology.notifications.TelegramLinks telegramLinks;

    @Autowired
    app.rootology.access.AccessRequestService accessRequests;

    @Autowired
    app.rootology.geocoding.GeoNames geoNames;

    @Autowired
    SourceCatalog sourceCatalog;

    @Autowired
    app.rootology.privacy.DataExport dataExport;

    @Autowired
    app.rootology.privacy.AccountDeletion accountDeletion;

    @Autowired
    com.vaadin.flow.spring.security.AuthenticationContext authentication;

    @Autowired
    app.rootology.spaces.IdentityService identityService;

    @Autowired
    app.rootology.enrichment.KnowledgeEnrichment enrichment;

    @Autowired
    app.rootology.facts.KnowledgeGraph knowledge;

    @Test
    void theQuestionCountShowsOnlyWhenFewAreLeft() {
        assertThat(AskView.fewLeft(0, 30)).isFalse();
        assertThat(AskView.fewLeft(24, 30)).isFalse();
        assertThat(AskView.fewLeft(25, 30)).isTrue();
        assertThat(AskView.fewLeft(26, 30)).isTrue();
        assertThat(AskView.fewLeft(30, 30)).isTrue();
    }

    @Test
    void everyRouteAndItsLayoutDeclareAccess() {
        // Vaadin denies a view whose route or parent layout has no access annotation.
        for (Class<?> view : List.of(ComparisonView.class, EscalationView.class, HistoryView.class, MembersView.class,
                InvitationsView.class, CuratorView.class,
                PlotsView.class, GardensView.class, GardenHomeView.class, AskView.class, KnowledgeView.class,
                AdminView.class, NotificationsView.class, StartView.class,
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
    void historySpaceAndCuratorViewsBuild() {
        HistoryView historyView = new HistoryView(history, gardens, viewer());
        assertThat(historyView.getChildren()).isNotEmpty();
        // The page has a title of its own (#80), like the other pages.
        assertThat(historyView.getChildren().anyMatch(com.vaadin.flow.component.html.H3.class::isInstance)).isTrue();
        assertThat(new MembersView(spaces, viewer()).getChildren()).isNotEmpty();
        assertThat(new InvitationsView(invitations, viewer()).getChildren()).isNotEmpty();
        assertThat(new PlotsView(gardens, crops, geoNames, weatherService, cells, viewer()).getChildren()).isNotEmpty();
        assertThat(new GardensView(gardens, geoNames, ungardened, viewer(), cells, pipeline, weatherService, STYLE)
                .getChildren()).isNotEmpty();
        assertThat(new AskView(pipeline, sourceCatalog, viewer(), enrichment, usage, gardens, crops, knowledge).getChildren()).isNotEmpty();
        assertThat(new SourcesView(sourceCatalog).getChildren()).isNotEmpty();
        assertThat(new GardenHomeView(gardens, cells, weatherService, notificationService, pipeline, history, viewer()).getChildren())
                .isNotEmpty();
        assertThat(new StartView(gardens, viewer()).getChildren()).isEmpty();
        assertThat(new NotificationsView(notificationService, gardens, viewer(), telegramLinks).getChildren()).isNotEmpty();
        assertThat(new AccountView(viewer(), dataExport, accountDeletion, authentication, identityService, "not-set")
                .getChildren()).isNotEmpty();
        assertThat(new KnowledgeView(enrichment, crops, knowledge, gardens, viewer()).getChildren()).isNotEmpty();
        assertThat(AdminView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("ADMIN");

        assertThat(SourcesView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class)).isTrue();
    }

    @Autowired
    Ungardened ungardened;

    @Test
    void openingANotificationOfAnotherContextAsksFirst() {
        CurrentUser me = viewer();
        long mine = personalSpace(me.login());
        long other = personalSpace("notifications-other-space");
        long garden = registeredGarden(mine, me.login());
        long otherGarden = registeredGarden(mine, me.login());
        long strangerGarden = registeredGarden(other, "notifications-other-space");

        NotificationsView fromGarden = new NotificationsView(notificationService, gardens, me, telegramLinks);
        fromGarden.beforeEnter(notificationsFrom(mine, garden));
        assertThat(fromGarden.asksBeforeOpening(notification(mine, garden))).as("same garden").isFalse();
        assertThat(fromGarden.asksBeforeOpening(notification(mine, otherGarden))).as("another garden").isTrue();
        assertThat(fromGarden.asksBeforeOpening(notification(mine))).as("the space level while in a garden").isTrue();
        assertThat(fromGarden.asksBeforeOpening(notification(other))).as("another space").isTrue();

        NotificationsView fromSpace = new NotificationsView(notificationService, gardens, me, telegramLinks);
        fromSpace.beforeEnter(notificationsFrom(mine, 0));
        assertThat(fromSpace.asksBeforeOpening(notification(mine))).as("the space level, from there").isFalse();
        assertThat(fromSpace.asksBeforeOpening(notification(mine, garden))).as("a garden, from the space level").isTrue();

        NotificationsView fromNowhere = new NotificationsView(notificationService, gardens, me, telegramLinks);
        fromNowhere.beforeEnter(notificationsFrom(0, 0));
        assertThat(fromNowhere.asksBeforeOpening(notification(mine))).as("no context").isTrue();
        assertThat(fromNowhere.asksBeforeOpening(notification(mine, garden))).as("no context, a garden").isTrue();

        NotificationsView fromAStranger = new NotificationsView(notificationService, gardens, me, telegramLinks);
        fromAStranger.beforeEnter(notificationsFrom(other, strangerGarden));
        assertThat(fromAStranger.asksBeforeOpening(notification(mine))).as("a space the person is not in is ignored").isTrue();
    }

    @Test
    void aWeatherAlertWithoutAGardenOpensTheSpacesGardens() {
        CurrentUser me = viewer();
        long mine = personalSpace(me.login());
        long garden = registeredGarden(mine, me.login());
        NotificationsView view = new NotificationsView(notificationService, gardens, me, telegramLinks);

        NotificationsView.Target alertOfAGarden = view.target(weather(mine, garden));
        assertThat(alertOfAGarden.view()).isEqualTo(GardenHomeView.class);
        assertThat(alertOfAGarden.route().get(Routed.GARDEN)).contains(String.valueOf(garden));

        // Stored before #56 in a space with several gardens, or of a deleted garden: no garden to open.
        NotificationsView.Target alertWithoutAGarden = view.target(weather(mine, 0));
        assertThat(alertWithoutAGarden.view()).isEqualTo(GardensView.class);
        assertThat(alertWithoutAGarden.route().get(Routed.GARDEN)).isEmpty();
        assertThat(alertWithoutAGarden.route().get(Routed.SPACE)).contains(String.valueOf(mine));
    }

    private long registeredGarden(long spaceId, String login) {
        return gardens.saveGarden(spaceId, login, null, "Garden " + System.nanoTime(), null, null, null).id();
    }

    /** The context the bell hands to the notifications page (#56): a hint, read from the query string. */
    private static BeforeEnterEvent notificationsFrom(long spaceId, long gardenId) {
        BeforeEnterEvent event = org.mockito.Mockito.mock(BeforeEnterEvent.class);
        java.util.Map<String, java.util.List<String>> query = new java.util.LinkedHashMap<>();
        if (spaceId > 0) {
            query.put(NotificationsView.SPACE, java.util.List.of(String.valueOf(spaceId)));
        }
        if (gardenId > 0) {
            query.put(NotificationsView.GARDEN, java.util.List.of(String.valueOf(gardenId)));
        }
        org.mockito.Mockito.when(event.getLocation()).thenReturn(new Location("notifications",
                new QueryParameters(query)));
        return event;
    }

    private static app.rootology.notifications.NotificationView notification(long spaceId) {
        return notification(spaceId, 0);
    }

    private static app.rootology.notifications.NotificationView weather(long spaceId, long gardenId) {
        return new app.rootology.notifications.NotificationView(2, spaceId, gardenId,
                "WEATHER_ALERT", null, "Rischio gelo", "Minima prevista", java.time.Instant.now(), null, null, null,
                "frost:" + gardenId + ":2026-11-12");
    }

    private static app.rootology.notifications.NotificationView notification(long spaceId,
                                                                                              long gardenId) {
        return new app.rootology.notifications.NotificationView(1, spaceId, gardenId,
                "ESCALATION_WAITING", "p-1", "title", "body", java.time.Instant.now(), null, null, null, null);
    }

    @Autowired
    app.rootology.privacy.Retention retention;

    @Test
    void thePrivacyPageNamesTheControllerOnlyWhenConfigured() {
        // Without a Vaadin session the texts stay keys; the wording is checked through Translations below.
        String configured = new PrivacyView(
                new app.rootology.privacy.PrivacyContact("Ada Lovelace", "ada@example.org"), retention,
                java.time.Duration.ofMinutes(30)).getElement().getTextRecursively();
        assertThat(configured).contains("privacy.who.text").doesNotContain("privacy.who.missing");
        String unset = new PrivacyView(new app.rootology.privacy.PrivacyContact("", ""), retention,
                java.time.Duration.ofMinutes(30)).getElement().getTextRecursively();
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
        assertThat(new AdminView(roles, usage, weatherAlerts, viewer(), accessRequests).getChildren().filter(Grid.class::isInstance).count()).isEqualTo(2);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "views-test-user", roles = "CURATOR")
    void curatorViewBuildsAndIsForCuratorsOnly() {
        assertThat(new CuratorView(enrichment, crops, knowledge, viewer()).getChildren()).isNotEmpty();
        assertThat(CuratorView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("CURATOR");
    }

    @Test
    void comparisonViewBuildsAndIsForQaOnly() {
        assertThat(new ComparisonView(runner, pipeline, goldenRuns, viewer()).getChildren()).isNotEmpty();
        assertThat(ComparisonView.class.getAnnotation(jakarta.annotation.security.RolesAllowed.class).value())
                .containsExactly("QA");
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "views-test-user", roles = "QA")
    void historyShowsTheSameColumnsToEveryone() {
        // The quality suite's own details live only in Compare (#80): a gardener and QA see the same history.
        List<String> forQa = historyColumns();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        List<String> forMember = historyColumns();

        assertThat(forMember).hasSize(6).containsExactlyElementsOf(forQa);
    }

    private List<String> historyColumns() {
        return new HistoryView(history, gardens, viewer()).getChildren().filter(Grid.class::isInstance)
                .map(Grid.class::cast)
                .flatMap(g -> g.getColumns().stream())
                .filter(c -> ((Grid.Column<?>) c).isVisible())
                .map(c -> ((Grid.Column<?>) c).getHeaderText() == null ? "" : ((Grid.Column<?>) c).getHeaderText())
                .toList();
    }

    /** A person's own space, signed in so it exists. */
    private long personalSpace(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).stream().filter(SpaceSummary::personal).findFirst().orElseThrow().id();
    }

    @Test
    void escalationViewShowsOneCardPerWaitingQuestion() {
        EscalationView view = new EscalationView(pipeline, gardens, viewer(), crops, knowledge, sourceCatalog,
                enrichment);

        // One card per waiting question, never a grid with a form below it (#86).
        assertThat(view.getChildren().anyMatch(Grid.class::isInstance)).isFalse();
        assertThat(view.getChildren().filter(Div.class::isInstance).map(Div.class::cast)
                .anyMatch(d -> d.getClassNames().contains("rootology-cards"))).isTrue();
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
