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
import java.util.concurrent.Executor;

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

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor")
    Executor askExecutor;

    @Test
    void aQuestionRunsOnTheAppsOwnExecutor() {
        // The Ask page runs the question off the UI thread (#82) on the executor Spring Boot already runs (@EnableAsync).
        assertThat(askExecutor).isNotNull();
    }

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
                AreasView.class, PlotsRedirectView.class, GardensView.class, GardenHomeView.class, AskView.class, KnowledgeView.class,
                AdminView.class, NotificationsView.class, NotificationSettingsView.class, StartView.class,
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
        assertThat(new MembersView(spaces, viewer()).getChildren()).isNotEmpty();
        assertThat(new InvitationsView(invitations, viewer()).getChildren()).isNotEmpty();
        assertThat(new AreasView(gardens, crops, geoNames, weatherService, cells, viewer()).getChildren()).isNotEmpty();
        assertThat(new GardensView(gardens, geoNames, ungardened, viewer(), cells, pipeline, weatherService, STYLE, "")
                .getChildren()).isNotEmpty();
        assertThat(new AskView(pipeline, sourceCatalog, viewer(), enrichment, usage, gardens, crops, knowledge, history).getChildren()).isNotEmpty();
        assertThat(new SourcesView(sourceCatalog).getChildren()).isNotEmpty();
        assertThat(new GardenHomeView(gardens, cells, weatherService, notificationService, pipeline, history, viewer()).getChildren())
                .isNotEmpty();
        assertThat(new StartView(gardens, viewer()).getChildren()).isEmpty();
        assertThat(new NotificationsView(notificationService, gardens, viewer()).getChildren()).isNotEmpty();
        assertThat(new AccountView(viewer(), dataExport, accountDeletion, authentication, identityService, "not-set")
                .getChildren()).isNotEmpty();
        assertThat(new NotificationSettingsView(notificationService, viewer(), telegramLinks).getChildren()).isNotEmpty();
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

        NotificationsView fromGarden = new NotificationsView(notificationService, gardens, me);
        fromGarden.beforeEnter(notificationsFrom(mine, garden));
        assertThat(fromGarden.asksBeforeOpening(notification(mine, garden))).as("same garden").isFalse();
        assertThat(fromGarden.asksBeforeOpening(notification(mine, otherGarden))).as("another garden").isTrue();
        assertThat(fromGarden.asksBeforeOpening(notification(mine))).as("the space level while in a garden").isTrue();
        assertThat(fromGarden.asksBeforeOpening(notification(other))).as("another space").isTrue();

        NotificationsView fromSpace = new NotificationsView(notificationService, gardens, me);
        fromSpace.beforeEnter(notificationsFrom(mine, 0));
        assertThat(fromSpace.asksBeforeOpening(notification(mine))).as("the space level, from there").isFalse();
        assertThat(fromSpace.asksBeforeOpening(notification(mine, garden))).as("a garden, from the space level").isTrue();

        NotificationsView fromNowhere = new NotificationsView(notificationService, gardens, me);
        fromNowhere.beforeEnter(notificationsFrom(0, 0));
        assertThat(fromNowhere.asksBeforeOpening(notification(mine))).as("no context").isTrue();
        assertThat(fromNowhere.asksBeforeOpening(notification(mine, garden))).as("no context, a garden").isTrue();

        NotificationsView fromAStranger = new NotificationsView(notificationService, gardens, me);
        fromAStranger.beforeEnter(notificationsFrom(other, strangerGarden));
        assertThat(fromAStranger.asksBeforeOpening(notification(mine))).as("a space the person is not in is ignored").isTrue();
    }

    @Test
    void aWeatherAlertWithoutAGardenOpensTheSpacesGardens() {
        CurrentUser me = viewer();
        long mine = personalSpace(me.login());
        long garden = registeredGarden(mine, me.login());
        NotificationsView view = new NotificationsView(notificationService, gardens, me);

        NotificationsView.Target alertOfAGarden = view.target(weather(mine, garden));
        assertThat(alertOfAGarden.view()).isEqualTo(GardenHomeView.class);
        assertThat(alertOfAGarden.route().get(Routed.GARDEN)).contains(String.valueOf(garden));

        // Stored before #56 in a space with several gardens, or of a deleted garden: no garden to open.
        NotificationsView.Target alertWithoutAGarden = view.target(weather(mine, 0));
        assertThat(alertWithoutAGarden.view()).isEqualTo(GardensView.class);
        assertThat(alertWithoutAGarden.route().get(Routed.GARDEN)).isEmpty();
        assertThat(alertWithoutAGarden.route().get(Routed.SPACE)).contains(String.valueOf(mine));
    }

    @Test
    void aNotificationCardSaysWhatHappenedAndWhetherItIsUnread() {
        // Without a Vaadin session getTranslation hands back the key itself (#93): the test checks which
        // text each kind gets.
        NotificationsView view = new NotificationsView(notificationService, gardens, viewer());
        app.rootology.notifications.NotificationView waiting = notification(1);
        assertThat(waiting.unread()).isTrue();
        assertThat(view.what(waiting)).contains("notifications.waiting");
        assertThat(view.status(waiting)).contains("notifications.status.waiting");
        // The details are built here, in the page's language (#94): without a session the key names it.
        assertThat(view.details(waiting)).contains("notifications.details.asked");

        // A weather alert carries its own title, and has no status beyond it.
        app.rootology.notifications.NotificationView alert = weather(1, 0);
        assertThat(view.what(alert)).isEqualTo("Rischio gelo");
        assertThat(view.status(alert)).isEmpty();

        java.time.Instant now = java.time.Instant.now();
        app.rootology.notifications.NotificationView answered = new app.rootology.notifications.NotificationView(3, 1,
                0, "ESCALATION_ANSWERED", "p-2", "title", "body", now, now, now, "ada", null, null);
        assertThat(view.what(answered)).contains("notifications.answered");
        assertThat(view.status(answered)).isEmpty();
        assertThat(view.details(answered)).as("an old answered row keeps its sentence (#94)").isEqualTo("body");

        app.rootology.notifications.NotificationView request = new app.rootology.notifications.NotificationView(4, 0,
                0, "ACCESS_REQUEST", null, "title", "body", now, null, null, null, null, null);
        assertThat(view.what(request)).contains("notifications.accessrequest");
        assertThat(view.status(request)).isEmpty();
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
                "frost:" + gardenId + ":2026-11-12", null);
    }

    private static app.rootology.notifications.NotificationView notification(long spaceId,
                                                                                              long gardenId) {
        return new app.rootology.notifications.NotificationView(1, spaceId, gardenId,
                "ESCALATION_WAITING", "p-1", "title", "body", java.time.Instant.now(), null, null, null, null,
                "mia");
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
    void theHistoryIsBesideTheQuestion() {
        // #100: a button in Ask's header opens the history; closed until the browser says otherwise.
        AskView ask = new AskView(pipeline, sourceCatalog, viewer(), enrichment, usage, gardens, crops, knowledge, history);
        assertThat(ask.historyOpen()).isFalse();
        PageHeader header = ask.getChildren().filter(PageHeader.class::isInstance).map(PageHeader.class::cast)
                .findFirst().orElseThrow();
        assertThat(header.getChildren().flatMap(c -> c.getChildren())
                .anyMatch(c -> c.getElement().getClassList().contains("rootology-history-toggle"))).isTrue();
        // The old History page forwards to Ask in the same context, with the column open.
        assertThat(HistoryView.target("3", "5")).isEqualTo("spaces/3/gardens/5/ask");
        assertThat(HistoryView.target("3", "")).isEqualTo("spaces/3/ask");
    }

    @Test
    void theHistorySearchFindsEveryWordIgnoringCaseAndAccents() {
        assertThat(HistoryPanel.matches("Perché il pomodoro ingiallisce?", "perche POMODORO")).isTrue();
        assertThat(HistoryPanel.matches("Quando semino il basilico?", "  ")).isTrue();
        assertThat(HistoryPanel.matches("Quando semino il basilico?", "basilico pomodoro")).isFalse();
        assertThat(HistoryPanel.matches(null, "x")).isFalse();
    }

    @Test
    void anAnsweredQuestionsNotificationOpensItsAnswerInAsk() {
        CurrentUser me = viewer();
        long mine = personalSpace(me.login());
        NotificationsView view = new NotificationsView(notificationService, gardens, me);

        NotificationsView.Target target = view.target(new app.rootology.notifications.NotificationView(3, mine, 0,
                "ESCALATION_ANSWERED", "p-7", "title", "body", java.time.Instant.now(), null, null, null, null, "mia"));

        assertThat(target.view()).isEqualTo(AskView.class);
        assertThat(target.query().getSingleParameter(AskView.RUN)).contains("p-7");
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
    void theWelcomePageDrawsItsFourFeatures() {
        // #87: answers with sources, it does not guess, it sees problems coming, a garden to share.
        WelcomeView view = new WelcomeView(authentication);
        Div features = view.getChildren().filter(Div.class::isInstance).map(Div.class::cast)
                .filter(d -> d.getClassNames().contains("rootology-features")).findFirst().orElseThrow();
        assertThat(features.getChildren().map(f -> f.getChildren().findFirst().orElseThrow()))
                .hasSize(4).allMatch(drawing -> drawing.getClassNames().contains("rootology-feature-drawing"));
    }

}
