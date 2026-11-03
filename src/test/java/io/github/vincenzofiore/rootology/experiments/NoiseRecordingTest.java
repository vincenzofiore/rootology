package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.JevDecisionLayer;
import io.github.vincenzofiore.rootology.decision.StubDecisionLayer;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Case;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.NoiseRun;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Outcome;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Trial;
import io.github.vincenzofiore.rootology.facts.Fact;
import io.github.vincenzofiore.rootology.facts.FactSource;
import io.github.vincenzofiore.rootology.pipeline.NoisyFacts;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Step 7: the same degraded readings go to the stub (trusts every reading) and to Jev,
 * for every case and several seeds. Jena on the clean dataset is the arbiter. Claude is left
 * out on purpose (fixed intents), so only the decision layer is compared.
 * Records one {@link NoiseRun}, {@code HOLDOUT} by default ({@code -Dnoise.run=FIRST} for the other), and
 * saves Jev's raw judgments to its file for ThresholdSweepTest and BaselineComparisonTest.
 * An existing judgments file is an experiment record and is never overwritten.
 * Recording only, no assertions: read the printed table.
 */
@Import(TestcontainersConfiguration.class)
@Tag("live-api")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROOTOLOGY_TYPESAFE_API_KEY", matches = ".+")
class NoiseRecordingTest {

    @Autowired
    ExperimentRunner runner;

    @Autowired
    FactSource factSource;

    @Autowired
    DecisionLayer decisionLayer;

    @Test
    void recordNoisyCases() throws Exception {
        assumeTrue(decisionLayer instanceof JevDecisionLayer, "Jev is not configured");
        NoiseRun run = NoiseRun.valueOf(System.getProperty("noise.run", "HOLDOUT"));
        if (Files.exists(run.file)) {
            throw new IllegalStateException(run.file + " already exists: it is an experiment record, move it away first");
        }
        JevDecisionLayer jev = (JevDecisionLayer) decisionLayer;
        DecisionLayer stub = new StubDecisionLayer();
        List<Fact> clean = factSource.allFacts();
        List<Trial> trials = new ArrayList<>();
        Map<String, int[]> totals = new TreeMap<>();

        for (Case c : NoiseCases.cases(runner)) {
            for (long seed = run.firstSeed; seed <= run.lastSeed; seed++) {
                GraphFacts facts = new GraphFacts(NoisyFacts.apply(clean, c.intent(), run.noise(seed)));
                JevDecisionLayer.Judgments judgments = jev.judge(c.intent(), facts);
                trials.add(new Trial(c.id(), seed, judgments.answerable(), judgments.candidates()));
                Map<String, Decision> decisions = Map.of(
                        "stub", stub.decide(c.intent(), facts),
                        "jev", JevDecisionLayer.decide(judgments, JevDecisionLayer.MIN_CONFIDENCE));
                for (String name : List.of("stub", "jev")) {
                    Decision d = decisions.get(name);
                    Outcome outcome = NoiseCases.outcome(c, d);
                    if (seed > 0) {
                        totals.computeIfAbsent(name + " " + c.id(), k -> new int[3])[outcome.ordinal()]++;
                    }
                    System.out.printf("NOISE %s seed=%d %-4s %-9s answer=%s conf=%.2f (%s)%n",
                            c.id(), seed, name, outcome, d.answer(), d.confidence(), d.reason());
                }
            }
        }
        totals.forEach((key, n) -> System.out.printf("TOTAL %-11s right=%d escalated=%d wrong=%d%n",
                key, n[0], n[1], n[2]));
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
                .writeValue(run.file.toFile(), trials);
    }
}
