package io.github.vincenzofiore.rootology.quality;

import io.github.vincenzofiore.rootology.experiments.GoldenQuestions;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.Question;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.weather.WeatherService;
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

    @Value("${embabel.models.default-llm}")
    String model;

    /** One question's outcome, as recorded. */
    record Result(String id, String question, String expectedIntent, String understoodIntent, boolean intentMatches,
                  GoldenVerdict verdict, List<String> expected, String expectedEscalation, List<String> answer,
                  String escalationReason, double confidence, List<String> notes, long millis, String error) {
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
            long start = System.nanoTime();
            try {
                GardenPipeline.Run r = pipeline.askKnowledgeBase(new Question(c.id(), c.question()), run);
                var decision = r.answer() != null ? null : r.pending().decision();
                results.add(new Result(c.id(), c.question(), c.intent().toString(), String.valueOf(r.intent()),
                        GoldenVerdict.intentMatches(c.intent(), r.intent()), GoldenVerdict.of(c, r, false), c.answer(),
                        c.escalate(), r.answer() != null ? r.answer().answer() : decision.answer(),
                        decision == null ? null : decision.reason(),
                        r.answer() != null ? r.answer().confidence() : decision.confidence(),
                        r.answer() != null ? r.answer().notes().stream().map(io.github.vincenzofiore.rootology.decision.Note::english).toList() : List.of(), (System.nanoTime() - start) / 1_000_000,
                        null));
            } catch (RuntimeException e) {
                // A failing call is recorded, not hidden: the other questions still run.
                results.add(new Result(c.id(), c.question(), c.intent().toString(), null, false, null, c.answer(),
                        c.escalate(), List.of(), null, 0, List.of(), (System.nanoTime() - start) / 1_000_000,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
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
}
