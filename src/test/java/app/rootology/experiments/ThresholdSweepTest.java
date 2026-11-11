package app.rootology.experiments;

import app.rootology.decision.Decision;
import app.rootology.decision.JevDecisionLayer;
import app.rootology.experiments.NoiseCases.Case;
import app.rootology.experiments.NoiseCases.NoiseRun;
import app.rootology.experiments.NoiseCases.Outcome;
import app.rootology.experiments.NoiseCases.Trial;
import app.rootology.inference.ChainedRelationQuery;
import app.rootology.inference.CompanionQuery;
import app.rootology.inference.FactPresenceQuery;
import app.rootology.inference.JenaModelService;
import app.rootology.inference.PestQuery;
import app.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

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
        assumeTrue(Files.exists(NoiseRun.FIRST.file), "run NoiseRecordingTest with -Plive first");
        var modelService = new JenaModelService(new OntologyDefinition());
        var runner = new ExperimentRunner(new CompanionQuery(modelService), new PestQuery(modelService),
                new ChainedRelationQuery(modelService), new FactPresenceQuery(modelService));
        Map<String, Case> cases = NoiseCases.cases(runner).stream()
                .collect(Collectors.toMap(Case::id, Function.identity()));

        for (NoiseRun run : NoiseRun.values()) {
            if (!Files.exists(run.file)) {
                continue;
            }
            List<Trial> trials = NoiseCases.load(run);
            for (boolean clean : new boolean[] {true, false}) {
                List<Trial> subset = trials.stream().filter(t -> (t.seed() == 0) == clean).toList();
                if (!subset.isEmpty()) {
                    sweep(run + " " + (clean ? "clean" : "noisy"), subset, cases);
                }
            }
        }
    }

    private static void sweep(String label, List<Trial> subset, Map<String, Case> cases) {
        System.out.printf("SWEEP %s inputs, %d trials%n", label, subset.size());
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
