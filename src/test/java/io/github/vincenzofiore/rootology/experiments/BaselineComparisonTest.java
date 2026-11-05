package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.JevDecisionLayer;
import io.github.vincenzofiore.rootology.decision.SkepticalStubDecisionLayer;
import io.github.vincenzofiore.rootology.decision.StubDecisionLayer;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Case;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.NoiseRun;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Outcome;
import io.github.vincenzofiore.rootology.experiments.NoiseCases.Trial;
import io.github.vincenzofiore.rootology.facts.Fact;
import io.github.vincenzofiore.rootology.facts.OntologyFacts;
import io.github.vincenzofiore.rootology.inference.ChainedRelationQuery;
import io.github.vincenzofiore.rootology.inference.CompanionQuery;
import io.github.vincenzofiore.rootology.inference.FactPresenceQuery;
import io.github.vincenzofiore.rootology.inference.JenaModelService;
import io.github.vincenzofiore.rootology.inference.PestQuery;
import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import io.github.vincenzofiore.rootology.pipeline.NoisyFacts;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Issue #1: naive stub, skeptical stub (drops UNCONFIRMED readings) and Jev on the same noisy
 * readings. The stubs re-run on the readings regenerated from the same seeds; Jev's side comes
 * from the saved judgments, so nothing is called. Recording only, no assertions.
 */
class BaselineComparisonTest {

    @Test
    void recordBaselineComparison() throws Exception {
        assumeTrue(Files.exists(NoiseRun.FIRST.file), "run NoiseRecordingTest with -Plive first");
        var modelService = new JenaModelService(new OntologyDefinition());
        var runner = new ExperimentRunner(new CompanionQuery(modelService), new PestQuery(modelService),
                new ChainedRelationQuery(modelService), new FactPresenceQuery(modelService));
        Map<String, Case> cases = NoiseCases.cases(runner).stream()
                .collect(Collectors.toMap(Case::id, Function.identity()));
        List<Fact> clean = OntologyFacts.from(new OntologyDefinition());

        for (NoiseRun run : NoiseRun.values()) {
            if (Files.exists(run.file)) {
                compare(run, NoiseCases.load(run), cases, clean);
            }
        }
    }

    private static void compare(NoiseRun run, List<Trial> trials, Map<String, Case> cases, List<Fact> clean) {
        Map<String, DecisionLayer> stubs = new LinkedHashMap<>();
        stubs.put("naive-stub", new StubDecisionLayer());
        stubs.put("skeptical-stub", new SkepticalStubDecisionLayer());
        Map<String, int[]> totals = new LinkedHashMap<>();

        for (Trial t : trials.stream().filter(t -> t.seed() > 0).toList()) {
            Case c = cases.get(t.caseId());
            GraphFacts facts = new GraphFacts(NoisyFacts.apply(clean, c.intent(), run.noise(t.seed())));
            Map<String, Decision> decisions = new LinkedHashMap<>();
            stubs.forEach((name, layer) -> decisions.put(name, layer.decide(c.intent(), facts)));
            decisions.put("jev", JevDecisionLayer.decide(
                    new JevDecisionLayer.Judgments(t.answerable(), t.candidates()), JevDecisionLayer.MIN_CONFIDENCE));
            decisions.forEach((name, d) -> {
                Outcome outcome = NoiseCases.outcome(c, d);
                totals.computeIfAbsent(name, k -> new int[3])[outcome.ordinal()]++;
                totals.computeIfAbsent(name + " " + c.id(), k -> new int[3])[outcome.ordinal()]++;
                System.out.printf("BASELINE %s %s seed=%d %-14s %-9s answer=%s%n",
                        run, c.id(), t.seed(), name, outcome, d.answer());
            });
        }
        totals.forEach((key, n) -> System.out.printf("BASELINE-TOTAL %s %-21s right=%d escalated=%d wrong=%d%n",
                run, key, n[0], n[1], n[2]));
    }
}
