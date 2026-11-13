package app.rootology.pipeline;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.DomainFilter;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.garden.GardenService;
import app.rootology.history.PipelineRunRecord;
import app.rootology.history.RunHistory;
import app.rootology.spaces.SpaceService;
import app.rootology.usage.UsageService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The domain filter (#53): a question clearly outside gardens costs no Claude call and no reviewer, is stored
 * and counted, and "Ask anyway" takes the normal path; the suite and the answers to waiting questions never
 * reach the filter.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "rootology.jev.api-key=")
class GardenPipelineDomainTest extends EmbabelMockitoIntegrationTest {

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    RunHistory history;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    UsageService usage;

    @Autowired
    MeterRegistry meters;

    /** Never the real service in tests. */
    @MockitoBean
    app.rootology.weather.WeatherService weatherService;

    /** Offline the real filter is not used: each test says what Jev would answer. */
    @MockitoBean
    DomainFilter domainFilter;

    @BeforeEach
    void stubPhrasing() {
        whenGenerateText(prompt -> prompt.contains("Write a one or two sentence answer"))
                .thenReturn("phrased answer");
    }

    /** A person with their personal space, as after their first login. */
    private RunContext signedIn(String login) {
        spaces.signedIn(login);
        return new RunContext(app.rootology.TestSpaces.personal(spaces, login), 0, login);
    }

    private RunContext inAGarden(String login) {
        RunContext home = signedIn(login);
        long garden = gardens.saveGarden(home.spaceId(), login, null, "Home garden", null, null, null).id();
        return new RunContext(home.spaceId(), garden, login);
    }

    private double questions(String outcome) {
        var timer = Search.in(meters).name("rootology.questions")
                .tags("scope", "knowledge-base", "outcome", outcome).timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void aQuestionClearlyOutsideGardensIsRefusedWithoutAClaudeCall() {
        String text = "Come si fa il punto zig-zag a macchina? 7132";
        when(domainFilter.judge(text)).thenReturn(DomainFilter.Verdict.refusing(0.02));
        RunContext run = inAGarden("nina");
        double before = questions("off_domain");

        GardenPipeline.Run result = pipeline.askKnowledgeBase(new Question("ask", text), run);

        assertThat(result.offDomain()).isTrue();
        assertThat(result.pending()).isNull();
        assertThat(result.intent()).isNull();
        assertThat(result.answer().answer()).isEmpty();
        assertThat(result.answer().text()).isNull();
        verifyNoInteractions();
        assertThat(questions("off_domain")).isEqualTo(before + 1);
    }

    @Test
    void aRefusedQuestionIsStoredWithItsSpaceAndGardenAndCountsAgainstTheLimit() {
        String text = "How do I change a car tyre? 8123";
        when(domainFilter.judge(text)).thenReturn(DomainFilter.Verdict.refusing(0.01));
        RunContext run = inAGarden("otto");

        pipeline.askKnowledgeBase(new Question("ask", text), run);

        PipelineRunRecord row = history.recent(run.spaceId(), run.gardenId(), run.startedBy()).getFirst();
        assertThat(row.isOffDomain()).isTrue();
        assertThat(row.getQuestion()).isEqualTo(text);
        // The context was never resolved, but the row still says where it was asked.
        assertThat(history.contextOf(row)).hasValueSatisfying(context -> {
            assertThat(context.spaceName()).isNotBlank();
            assertThat(context.gardenName()).isEqualTo("Home garden");
        });
        assertThat(usage.today("otto").used()).isEqualTo(1);
    }

    @Test
    void askAnywayTakesTheNormalPathAndNeverAsksTheFilter() {
        whenCreateObject(prompt -> prompt.contains("Question: How do I change a car tyre? 9101"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "tomato"));
        RunContext run = signedIn("piero");

        GardenPipeline.Run result = pipeline.askKnowledgeBase(
                new Question("ask", "How do I change a car tyre? 9101"), run, true);

        assertThat(result.offDomain()).isFalse();
        assertThat(result.answer()).isNotNull();
        verify(domainFilter, never()).judge(anyString());
    }

    @Test
    void theSuiteNeverAsksTheFilter() {
        var answer = pipeline.ask(new Question("easy-1", "Which plants are companions of Tomato?"),
                Intent.of(QueryKind.COMPANIONS, "Tomato"));

        assertThat(answer.answer()).isNotEmpty();
        verify(domainFilter, never()).judge(anyString());
    }

    @Test
    void answeringAWaitingQuestionNeverAsksTheFilter() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of Lavender? 3311"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "Lavender"));
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")).thenReturn("a human answered");
        RunContext asker = signedIn("quinn");
        GardenPipeline.Run waiting = pipeline.start(
                new Question("hard-3", "Which plants are companions of Lavender? 3311"), NoiseProfile.NONE, asker);
        clearInvocations(domainFilter);

        pipeline.respond(waiting.pending().processId(), asker.spaceId(), asker.gardenId(),
                new HumanAnswer(java.util.List.of(), "not in the dataset", "quinn"));

        verify(domainFilter, never()).judge(anyString());
    }
}
