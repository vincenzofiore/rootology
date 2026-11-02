package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.inference.facts.FactSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * Asks Jev the same fixed intents several times over identical, sorted facts, without Claude,
 * to see how much the decision layer varies on its own. Recording only, no assertions;
 * per-candidate probabilities are in the JevDecisionLayer log lines.
 */
@Import(TestcontainersConfiguration.class)
@Tag("live-api")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROOTOLOGY_TYPESAFE_API_KEY", matches = ".+")
class JevStabilityRecordingTest {

    private static final int RUNS = 5;

    @Autowired
    DecisionLayer decisionLayer;

    @Autowired
    FactSource factSource;

    @Test
    void recordRepeatedDecisions() {
        GraphFacts facts = GraphFacts.curated(factSource.allFacts());
        List<Intent> intents = List.of(
                Intent.of(QueryKind.COMPANIONS, "Tomato"),
                new Intent(QueryKind.RELEVANT_PESTS, "Basil", "SUMMER"),
                Intent.of(QueryKind.COMPANIONS_OF_SHARED_REPELLERS, "Tomato"),
                Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"),
                Intent.of(QueryKind.COMPANIONS, "Lavender"));
        for (Intent intent : intents) {
            for (int run = 1; run <= RUNS; run++) {
                Decision decision = decisionLayer.decide(intent, facts);
                System.out.printf("STABILITY %s %s run=%d %s%n", intent.kind(), intent.subject(), run, decision);
            }
        }
    }
}
