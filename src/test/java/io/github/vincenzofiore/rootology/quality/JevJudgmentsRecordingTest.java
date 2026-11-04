package io.github.vincenzofiore.rootology.quality;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.JevDecisionLayer;
import io.github.vincenzofiore.rootology.experiments.GoldenQuestions;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.pipeline.FactScope;
import io.github.vincenzofiore.rootology.pipeline.GardenPipelineAgent;
import io.github.vincenzofiore.rootology.pipeline.NoiseProfile;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.weather.WeatherService;
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
            if (c.intent().kind() == io.github.vincenzofiore.rootology.decision.QueryKind.OTHER) {
                continue; // never sent to Jev
            }
            RunContext run = fixtures.space("judge-" + startedAt.toEpochMilli(), c);
            when(weather.around(any(), any())).thenReturn(GoldenFixtures.forecast(c.weather(), clock()));
            Intent intent = GardenPipelineAgent.withDefaults(c.intent(), LocalDate.now(clock()).getMonth());
            GraphFacts facts = agent.gatherFacts(intent, NoiseProfile.NONE, FactScope.KNOWLEDGE_BASE, run);
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
