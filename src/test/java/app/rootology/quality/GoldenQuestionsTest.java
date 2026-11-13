package app.rootology.quality;

import app.rootology.experiments.GoldenQuestions;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.DomainFilter;
import app.rootology.decision.Intent;
import app.rootology.facts.KnowledgeContributions;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The offline quality suite (#20): every golden question through the real pipeline, with Claude mocked
 * (it returns the expected intent), the stub decision layer and a fixed "today". Each mismatch fails:
 * a silent wrong answer, a missed escalation, an unexpected escalation or a missing note.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "rootology.jev.api-key=")
class GoldenQuestionsTest extends EmbabelMockitoIntegrationTest {

    static final GoldenQuestions GOLDEN = GoldenQuestions.load();

    @TestBean
    Clock clock;

    static Clock clock() {
        return GoldenFixtures.clock(GOLDEN);
    }

    @MockitoBean
    WeatherService weather;

    /** Offline, the domain filter answers from the case, the way Claude's intent does (#53). */
    @MockitoBean
    DomainFilter domainFilter;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeContributions contributions;

    @TestFactory
    Stream<DynamicTest> goldenQuestions() {
        GoldenFixtures fixtures = new GoldenFixtures(spaces, gardens, contributions);
        whenGenerateText(prompt -> true).thenReturn("phrased answer");
        return GOLDEN.cases().stream().map(c -> DynamicTest.dynamicTest(c.id(), () -> check(fixtures, c)));
    }

    private void check(GoldenFixtures fixtures, GoldenQuestions.Case c) {
        RunContext run = fixtures.space("golden", c);
        whenCreateObject(prompt -> prompt.contains("Question: " + c.question() + "\n"), Intent.class)
                .thenReturn(c.intent());
        when(domainFilter.judge(c.question())).thenReturn(c.offDomain()
                ? DomainFilter.Verdict.refusing(0.02) : DomainFilter.Verdict.allowing(0.98));
        when(weather.around(any(), any())).thenReturn(GoldenFixtures.forecast(c.weather(), clock()));

        GardenPipeline.Run result = pipeline.askKnowledgeBase(new Question(c.id(), c.question()), run);

        GoldenVerdict verdict = GoldenVerdict.of(c, result);
        if (verdict != GoldenVerdict.CORRECT && verdict != GoldenVerdict.CORRECT_ESCALATION
                && verdict != GoldenVerdict.CORRECT_REFUSAL) {
            fail(verdict + " for " + c.id() + ": " + (result.offDomain() ? "refused as off-domain"
                    : result.answer() != null ? "answered " + result.answer().answer() + " with notes " + result.answer().notes()
                    : "escalated: " + result.pending().decision().reason() + " with notes " + result.pending().notes())
                    + "; expected " + (c.offDomain() ? "a refusal as off-domain"
                    : c.answer() != null ? c.answer() + " with notes " + c.notes()
                    : "an escalation about \"" + c.escalate() + "\""));
        }
    }
}
