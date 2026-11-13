package app.rootology.quality;

import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.DecisionLayer;
import app.rootology.decision.GraphFacts;
import app.rootology.decision.Intent;
import app.rootology.decision.JevDecisionLayer;
import app.rootology.experiments.GoldenQuestions;
import app.rootology.facts.KnowledgeContributions;
import app.rootology.garden.GardenService;
import app.rootology.pipeline.FactScope;
import app.rootology.pipeline.GardenPipelineAgent;
import app.rootology.pipeline.NoiseProfile;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Diagnostic for #35, run by hand: ./mvnw test -Plive -Dtest=JevJudgmentsRecordingTest. For each golden
 * question, gathers the facts exactly as the pipeline does (same fixtures and "today") and records Jev's raw
 * judgments: the answerable probability and every candidate's probability, with the facts it saw. No Claude
 * call: the expected intent is used. Recorded next to the live runs.
 */
@Tag("live-api")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROOTOLOGY_TYPESAFE_API_KEY", matches = ".+")
class JevJudgmentsRecordingTest {

    static final GoldenQuestions GOLDEN = GoldenQuestions.load();

    @TestBean
    Clock clock;

    static Clock clock() {
        return GoldenFixtures.clock(GOLDEN);
    }

    @MockitoBean
    WeatherService weather;

    @Autowired
    GardenPipelineAgent agent;

    @Autowired
    DecisionLayer decisionLayer;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeContributions contributions;

    record Judged(String id, String intent, int facts, List<String> factLines, double answerable,
                  Map<String, Double> candidates, String decision) {
    }

    @Test
    void recordJudgments() throws Exception {
        JevDecisionLayer jev = (JevDecisionLayer) decisionLayer;
        Instant startedAt = Instant.now();
        GoldenFixtures fixtures = new GoldenFixtures(spaces, gardens, contributions);
        List<Judged> judged = new ArrayList<>();
        for (GoldenQuestions.Case c : GOLDEN.cases()) {
            if (c.intent().kind() == app.rootology.decision.QueryKind.OTHER) {
                continue; // never sent to Jev
            }
            RunContext run = fixtures.space("judge-" + startedAt.toEpochMilli(), c);
            when(weather.around(any(), any())).thenReturn(GoldenFixtures.forecast(c.weather(), clock()));
            var context = agent.resolveContext(c.intent(), run);
            Intent intent = context.intent();
            GraphFacts facts = agent.gatherFacts(context, NoiseProfile.NONE, FactScope.KNOWLEDGE_BASE, run);
            JevDecisionLayer.Judgments j = jev.judge(intent, facts);
            judged.add(new Judged(c.id(), intent.toString(), facts.readings().size(),
                    facts.readings().stream().map(r -> r.fact().subject() + " " + r.fact().predicate() + " "
                            + r.fact().object() + " [" + r.source() + "]").toList(),
                    j.answerable(), new LinkedHashMap<>(j.candidates()),
                    JevDecisionLayer.decide(j, JevDecisionLayer.MIN_CONFIDENCE).toString()));
        }
        Path file = GoldenLiveRecordingTest.RUNS.resolve("jev-judgments-" + DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmm")
                .withZone(ZoneOffset.UTC).format(startedAt) + ".json");
        Files.createDirectories(GoldenLiveRecordingTest.RUNS);
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValue(file.toFile(),
                Map.of("startedAt", startedAt.toString(), "judgments", judged));
        System.out.println("Jev judgments: " + judged.size() + " -> " + file);
    }
}
