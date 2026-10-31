package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Reading;
import io.github.vincenzofiore.rootology.decision.Reading.Source;
import io.github.vincenzofiore.rootology.inference.facts.Fact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Turns clean facts into degraded readings: missing facts, false facts about the
 * question's subject, and imperfect source tags. Deterministic for a given seed.
 */
public final class NoisyFacts {

    public static List<Reading> apply(List<Fact> clean, Intent intent, NoiseProfile noise) {
        Random random = new Random(noise.seed());
        List<Reading> readings = new ArrayList<>();
        for (Fact fact : clean) {
            if (random.nextDouble() >= noise.dropout()) {
                readings.add(new Reading(fact, random.nextDouble() < noise.mislabel() ? Source.UNCONFIRMED : Source.CURATED));
            }
        }
        List<Fact> pool = falseFactsAbout(intent.subject(), clean);
        Collections.shuffle(pool, random);
        for (Fact fact : pool.subList(0, Math.min(noise.spurious(), pool.size()))) {
            readings.add(new Reading(fact, random.nextDouble() < noise.mislabel() ? Source.CURATED : Source.UNCONFIRMED));
        }
        readings.sort(Comparator.comparing((Reading r) -> r.fact().subject())
                .thenComparing(r -> r.fact().predicate())
                .thenComparing(r -> r.fact().object()));
        return readings;
    }

    /** Plausible but undeclared facts with the subject in subject position, typed by what the subject is. */
    private static List<Fact> falseFactsAbout(String subject, List<Fact> clean) {
        if (subject == null) {
            return new ArrayList<>();
        }
        Set<String> plants = new LinkedHashSet<>();
        Set<String> pests = new LinkedHashSet<>();
        Set<String> seasons = new LinkedHashSet<>();
        for (Fact f : clean) {
            switch (f.predicate()) {
                case "activeInSeason" -> { pests.add(f.subject()); seasons.add(f.object()); }
                case "repels" -> { plants.add(f.subject()); pests.add(f.object()); }
                case "growsInSeason" -> { plants.add(f.subject()); seasons.add(f.object()); }
                default -> plants.add(f.subject());
            }
        }
        List<Fact> pool = new ArrayList<>();
        if (pests.contains(subject)) {
            seasons.forEach(s -> pool.add(new Fact(subject, "activeInSeason", s)));
        } else {
            plants.stream().filter(p -> !p.equals(subject))
                    .forEach(p -> pool.add(new Fact(subject, "companionOf", p)));
            pests.forEach(p -> pool.add(new Fact(subject, "repels", p)));
        }
        pool.removeAll(clean);
        return pool;
    }

    private NoisyFacts() {
    }
}
