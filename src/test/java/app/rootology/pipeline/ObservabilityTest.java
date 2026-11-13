package app.rootology.pipeline;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.notifications.NotificationPreferences;
import app.rootology.notifications.NotificationService;
import app.rootology.spaces.SpaceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a question leaves behind for operations (#27): the domain metrics count it with the right outcome, and
 * the logs of the whole run, notifications included, hold neither the question's text nor the asker's address.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"rootology.jev.api-key=", "rootology.notifications.group-window=PT0S"})
class ObservabilityTest extends EmbabelMockitoIntegrationTest {

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    NotificationService notifications;

    @Autowired
    MeterRegistry meters;

    /** Never the real service in tests. */
    @MockitoBean
    app.rootology.weather.WeatherService weatherService;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void captureLogs() {
        logs.start();
        root.addAppender(logs);
        whenGenerateText(prompt -> prompt.contains("Write a one or two sentence answer")).thenReturn("phrased answer");
    }

    @AfterEach
    void stopCapturing() {
        root.detachAppender(logs);
        logs.stop();
    }

    private RunContext signedIn(String login, String email) {
        spaces.signedIn(login);
        notifications.save(login, new NotificationPreferences(email, true, true, true, true, null, null));
        return new RunContext(app.rootology.TestSpaces.personal(spaces, login), 0, login);
    }

    private double questions(String outcome) {
        var timer = Search.in(meters).name("rootology.questions")
                .tags("scope", "knowledge-base", "outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void anAnsweredQuestionIsCountedAsAnswered() {
        String text = "Which plants are companions of tomato? 4471";
        whenCreateObject(prompt -> prompt.contains(text), Intent.class).thenReturn(Intent.of(QueryKind.COMPANIONS, "tomato"));
        double before = questions("answered");

        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text), signedIn("olga", "olga@example.test"));

        assertThat(run.answer()).isNotNull();
        assertThat(questions("answered")).isEqualTo(before + 1);
        assertThat(Search.in(meters).name("rootology.questions.confidence").tag("scope", "knowledge-base").summary())
                .isNotNull();
    }

    @Test
    void aQuestionWaitingForAPersonIsCountedAsEscalatedAndLogsNoPersonalData() {
        String text = "Which plants are companions of zzyzxberry? 9384";
        String email = "pia.private@example.test";
        whenCreateObject(prompt -> prompt.contains(text), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "zzyzxberry"));
        double before = questions("escalated");

        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text), signedIn("pia", email));
        notifications.digest();

        assertThat(run.pending()).isNotNull();
        assertThat(questions("escalated")).isEqualTo(before + 1);
        assertThat(logged()).isNotEmpty().noneMatch(line -> line.contains(text) || line.contains(email));
    }

    @Test
    void anAnswerToAWaitingQuestionLogsNoQuestionTextOrAddress() {
        String text = "Which plants are companions of zzyzxberry? 5521";
        String email = "ugo.private@example.test";
        whenCreateObject(prompt -> prompt.contains(text), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "zzyzxberry"));
        double escalated = questions("escalated");
        double answered = questions("answered");

        RunContext asker = signedIn("ugo", email);
        spaces.addMember(asker.spaceId(), asker.startedBy(), "vera", app.rootology.spaces.Role.MEMBER);
        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", text), asker);

        pipeline.respond(run.pending().processId(), asker.spaceId(), asker.gardenId(),
                new HumanAnswer(List.of("Rosmarino"), "", "vera"));
        notifications.digest();

        assertThat(questions("escalated")).isEqualTo(escalated + 1);
        assertThat(questions("answered")).as("answering is not a new question").isEqualTo(answered);
        assertThat(logged()).isNotEmpty().noneMatch(line -> line.contains(text) || line.contains(email));
    }

    /** Every line a run leaves in the logs, with its throwable and MDC values. */
    private List<String> logged() {
        return logs.list.stream().flatMap(e -> Stream.concat(
                        Stream.of(e.getFormattedMessage(), e.getThrowableProxy() == null ? null : e.getThrowableProxy().getMessage()),
                        e.getMDCPropertyMap().values().stream()))
                .filter(Objects::nonNull).toList();
    }
}
