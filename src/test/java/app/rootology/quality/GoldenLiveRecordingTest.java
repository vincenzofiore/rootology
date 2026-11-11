package app.rootology.quality;

import app.rootology.experiments.GoldenQuestions;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.DecisionLayer;
import app.rootology.decision.DomainFilter;
import app.rootology.facts.KnowledgeContributions;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The live quality suite (#20), run by hand: ./mvnw test -Plive -Dtest=GoldenLiveRecordingTest. Every golden
 * question goes through the real Claude (it reads the question text) and the configured decision layer (Jev
 * with ROOTOLOGY_TYPESAFE_API_KEY, otherwise the stub), with the same fixtures and "today" as offline. It
 * measures and records, it does not fail: understood intents, silent errors, escalations and confidence go
 * to src/main/resources/quality/live-runs/, kept in the repository like the experiment's recordings.
 */
@Tag("live-api")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROOTOLOGY_ANTHROPIC_API_KEY", matches = ".+")
class GoldenLiveRecordingTest {

    static final Path RUNS = Path.of("src/main/resources/quality/live-runs");
    static final GoldenQuestions GOLDEN = GoldenQuestions.load();

    @TestBean
    Clock clock;

    static Clock clock() {
        return GoldenFixtures.clock(GOLDEN);
    }

    @MockitoBean
    WeatherService weather;

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeContributions contributions;

    @Autowired
    DecisionLayer decisionLayer;

    @Autowired
    DomainFilter domainFilter;

    @Value("${embabel.models.default-llm}")
    String model;

    /**
     * One question's outcome, as recorded. domainProbability is Jev's probability that the question is about
     * gardens (#53), null when it could not be judged; older run files have no such field.
     */
    record Result(String id, String question, String expectedIntent, String understoodIntent, boolean intentMatches,
                  GoldenVerdict verdict, List<String> expected, String expectedEscalation, List<String> answer,
                  String escalationReason, double confidence, List<String> notes, long millis, String error,
                  Double domainProbability) {
    }

    @Test
    void recordALiveRun() throws Exception {
        Instant startedAt = Instant.now();
        String prefix = "live-" + startedAt.toEpochMilli();
        GoldenFixtures fixtures = new GoldenFixtures(spaces, gardens, contributions);
        List<Result> results = new ArrayList<>();
        for (GoldenQuestions.Case c : GOLDEN.cases()) {
            RunContext run = fixtures.space(prefix, c);
            when(weather.around(any(), any())).thenReturn(GoldenFixtures.forecast(c.weather(), clock()));
            // The filter's own judgment, recorded so a live run shows what it saw (#53).
            Double domain = domainProbability(c.question());
            long start = System.nanoTime();
            try {
                GardenPipeline.Run r = pipeline.askKnowledgeBase(new Question(c.id(), c.question()), run);
                var decision = r.answer() != null ? null : r.pending().decision();
                GoldenVerdict verdict = GoldenVerdict.of(c, r, false);
                // A refused question never reaches Claude (#53): a correct refusal needs no understanding, so it
                // does not lower the count of intents understood; a wrong one counts as not understood.
                boolean intentMatches = r.offDomain() ? verdict == GoldenVerdict.CORRECT_REFUSAL
                        : GoldenVerdict.intentMatches(c.intent(), r.intent());
                results.add(new Result(c.id(), c.question(), c.intent().toString(),
                        r.offDomain() ? "refused as off-domain" : String.valueOf(r.intent()), intentMatches, verdict, c.answer(),
                        c.escalate(), r.answer() != null ? r.answer().answer() : decision.answer(),
                        decision == null ? null : decision.reason(),
                        r.answer() != null ? r.answer().confidence() : decision.confidence(),
                        (r.answer() != null ? r.answer().notes() : r.pending().notes()).stream()
                                .map(app.rootology.decision.Note::english).toList(),
                        (System.nanoTime() - start) / 1_000_000,
                        null, domain));
            } catch (RuntimeException e) {
                // A failing call is recorded, not hidden: the other questions still run.
                results.add(new Result(c.id(), c.question(), c.intent().toString(), null, false, null, c.answer(),
                        c.escalate(), List.of(), null, 0, List.of(), (System.nanoTime() - start) / 1_000_000,
                        e.getClass().getSimpleName() + ": " + e.getMessage(), domain));
            }
        }

        Map<GoldenVerdict, Integer> verdicts = new EnumMap<>(GoldenVerdict.class);
        results.stream().filter(r -> r.verdict() != null).forEach(r -> verdicts.merge(r.verdict(), 1, Integer::sum));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("questions", results.size());
        summary.put("verdicts", verdicts);
        summary.put("intentsUnderstood", results.stream().filter(Result::intentMatches).count());
        summary.put("errors", results.stream().filter(r -> r.error() != null).count());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("startedAt", startedAt.toString());
        report.put("suiteToday", GOLDEN.today().toString());
        report.put("llm", model);
        report.put("decisionLayer", decisionLayer.getClass().getSimpleName());
        report.put("summary", summary);
        report.put("results", results);

        Files.createDirectories(RUNS);
        Path file = RUNS.resolve("golden-" + DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm")
                .withZone(ZoneOffset.UTC).format(startedAt) + ".json");
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report);
        System.out.println("Live golden run: " + summary + " -> " + file);
    }

    /** Jev's probability that the question is about gardens, or null when the filter could not judge it. */
    private Double domainProbability(String question) {
        try {
            return domainFilter.judge(question).probability();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
