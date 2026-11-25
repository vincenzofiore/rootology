package app.rootology.pipeline;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
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
class GardenPipelineDomainTest extends PipelineContext {

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
        doReturn(DomainFilter.Verdict.refusing(0.02)).when(domainFilter).judge(text);
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
        doReturn(DomainFilter.Verdict.refusing(0.01)).when(domainFilter).judge(text);
        RunContext run = inAGarden("otto");

        pipeline.askKnowledgeBase(new Question("ask", text), run);

        PipelineRunRecord row = history.recent(run.spaceId(), run.gardenId(), run.startedBy()).getFirst();
        assertThat(row.isOffDomain()).isTrue();
        assertThat(row.isDomainUnjudged()).isFalse();
        assertThat(row.getQuestion()).isEqualTo(text);
        // The context was never resolved, but the row still says where it was asked.
        assertThat(history.contextOf(row)).hasValueSatisfying(context -> {
            assertThat(context.spaceName()).isNotBlank();
            assertThat(context.gardenName()).isEqualTo("Home garden");
        });
        assertThat(usage.today("otto").used()).isEqualTo(1);
    }

    @Test
    void aQuestionTheFilterCouldNotJudgeGoesOnAndSaysSo() {
        whenCreateObject(prompt -> prompt.contains("Question: How do I change a car tyre? 9102"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "tomato"));
        doReturn(DomainFilter.Verdict.unjudged()).when(domainFilter).judge("How do I change a car tyre? 9102");
        doReturn(DomainFilter.Verdict.allowing(0.9)).when(domainFilter).judge("How do I change a car tyre? 9103");
        whenCreateObject(prompt -> prompt.contains("Question: How do I change a car tyre? 9103"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "tomato"));
        RunContext run = signedIn("piero");

        GardenPipeline.Run unscreened = pipeline.askKnowledgeBase(new Question("ask", "How do I change a car tyre? 9102"), run);
        GardenPipeline.Run screened = pipeline.askKnowledgeBase(new Question("ask", "How do I change a car tyre? 9103"), run);

        assertThat(unscreened.offDomain()).isFalse();
        assertThat(unscreened.domainUnjudged()).as("an outage of the filter is kept with the run (#161)").isTrue();
        assertThat(screened.domainUnjudged()).isFalse();

        // #165: the history row says the question went on unscreened, so a later signal can leave it out.
        assertThat(history.recent(run.spaceId(), 0, "piero"))
                .filteredOn(row -> row.getQuestion().endsWith("9102")).singleElement()
                .satisfies(row -> assertThat(row.isDomainUnjudged()).isTrue());
        assertThat(history.recent(run.spaceId(), 0, "piero"))
                .filteredOn(row -> row.getQuestion().endsWith("9103")).singleElement()
                .satisfies(row -> assertThat(row.isDomainUnjudged()).isFalse());
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
        whenGenerateText(prompt -> prompt.contains("A person of the space answered this question")).thenReturn("a human answered");
        RunContext asker = signedIn("quinn");
        GardenPipeline.Run waiting = pipeline.start(
                new Question("hard-3", "Which plants are companions of Lavender? 3311"), NoiseProfile.NONE, asker);
        clearInvocations(domainFilter);

        pipeline.respond(waiting.pending().processId(), asker.spaceId(), asker.gardenId(),
                new HumanAnswer(java.util.List.of(), "not in the dataset", "quinn"));

        verify(domainFilter, never()).judge(anyString());
    }
}
