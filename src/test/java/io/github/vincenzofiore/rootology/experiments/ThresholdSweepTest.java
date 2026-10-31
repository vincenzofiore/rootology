package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.JevDecisionLayer;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Case;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Outcome;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Trial;
import io.github.vincenzofiore.rootology.inference.ChainedRelationQuery;
import io.github.vincenzofiore.rootology.inference.CompanionQuery;
import io.github.vincenzofiore.rootology.inference.FactPresenceQuery;
import io.github.vincenzofiore.rootology.inference.JenaModelService;
import io.github.vincenzofiore.rootology.inference.PestQuery;
import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Re-applies the decision policy to Jev's saved judgments at several confidence thresholds,
 * without calling Jev again: the trade-off between wrong answers and escalations.
 * Recording only, no assertions; runs offline as long as the judgments file exists.
 */
class ThresholdSweepTest {

    private static final double[] THRESHOLDS = {0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};

    @Test
    void recordThresholdSweep() throws Exception {
        assumeTrue(Files.exists(NoiseCases.JUDGMENTS_FILE), "run NoiseRecordingTest with -Plive first");
        List<Trial> trials = JsonMapper.builder().build()
                .readValue(NoiseCases.JUDGMENTS_FILE.toFile(), new TypeReference<List<Trial>>() {});

        var modelService = new JenaModelService(new OntologyDefinition());
        var runner = new ExperimentRunner(new CompanionQuery(modelService), new PestQuery(modelService),
                new ChainedRelationQuery(modelService), new FactPresenceQuery(modelService));
        Map<String, Case> cases = NoiseCases.cases(runner).stream()
                .collect(Collectors.toMap(Case::id, Function.identity()));

        for (boolean clean : new boolean[] {true, false}) {
            List<Trial> subset = trials.stream().filter(t -> (t.seed() == 0) == clean).toList();
            System.out.printf("SWEEP %s inputs, %d trials%n", clean ? "clean" : "noisy", subset.size());
            for (double threshold : THRESHOLDS) {
                int[] n = new int[3];
                for (Trial t : subset) {
                    Decision d = JevDecisionLayer.decide(
                            new JevDecisionLayer.Judgments(t.answerable(), t.candidates()), threshold);
                    n[NoiseCases.outcome(cases.get(t.caseId()), d).ordinal()]++;
                }
                System.out.printf("SWEEP   threshold=%.1f right=%d escalated=%d wrong=%d%n",
                        threshold, n[Outcome.RIGHT.ordinal()], n[Outcome.ESCALATED.ordinal()],
                        n[Outcome.WRONG.ordinal()]);
            }
        }
    }
}
