package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.pipeline.NoiseProfile;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

/** Cases, noise settings and outcome classification shared by the step 7 recordings. */
final class NoiseCases {

    static final int SEEDS = 5;

    /** Where NoiseRecordingTest stores Jev's raw judgments, so thresholds can be compared offline. */
    static final Path JUDGMENTS_FILE = Path.of("src/test/resources/experiments/jev-noise-judgments.json");

    enum Outcome { RIGHT, ESCALATED, WRONG }

    /** What the arbiter says, and whether the right behavior is to escalate (missing fact, out of scope). */
    record Case(String id, Intent intent, List<String> jena, boolean mustEscalate) {
    }

    /** One Jev call: the judgments for a case at a seed (seed 0 is the clean baseline). */
    record Trial(String caseId, long seed, double answerable, java.util.Map<String, Double> candidates) {
    }

    static List<Case> cases(ExperimentRunner runner) {
        return List.of(
                new Case("easy-1", Intent.of(QueryKind.COMPANIONS, "Tomato"), runner.jenaCompanionsOf("Tomato"), false),
                new Case("easy-2", new Intent(QueryKind.RELEVANT_PESTS, "Basil", "SUMMER"),
                        runner.jenaRelevantPests("Basil", "SUMMER"), false),
                new Case("hard-1", Intent.of(QueryKind.COMPANIONS_OF_SHARED_REPELLERS, "Tomato"),
                        runner.jenaChainedCompanions("Tomato"), false),
                new Case("hard-2", Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"),
                        runner.jenaDeclaredSeasonsOf("Mosquito").orElse(List.of()), true),
                new Case("hard-3", Intent.of(QueryKind.COMPANIONS, "Lavender"), runner.jenaCompanionsOf("Lavender"), true));
    }

    /** Seed 0 is clean; seeds 1..SEEDS use 15% dropout, 2 false facts about the subject, 20% wrong source tags. */
    static NoiseProfile noise(long seed) {
        return seed == 0 ? NoiseProfile.NONE : new NoiseProfile(0.15, 2, 0.2, seed);
    }

    static Outcome outcome(Case c, Decision d) {
        if (d.escalate()) {
            return c.mustEscalate() ? Outcome.RIGHT : Outcome.ESCALATED;
        }
        boolean matches = new HashSet<>(c.jena()).equals(new HashSet<>(d.answer()));
        return !c.mustEscalate() && matches ? Outcome.RIGHT : Outcome.WRONG;
    }

    private NoiseCases() {
    }
}
