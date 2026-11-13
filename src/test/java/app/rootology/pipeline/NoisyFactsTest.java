package app.rootology.pipeline;

import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.decision.Reading;
import app.rootology.facts.Fact;
import app.rootology.facts.OntologyFacts;
import app.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NoisyFactsTest {

    private final List<Fact> clean = OntologyFacts.from(new OntologyDefinition());
    private final Intent mosquito = Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito");

    @Test
    void noNoiseKeepsEveryFactAsCurated() {
        List<Reading> readings = NoisyFacts.apply(clean, mosquito, NoiseProfile.NONE);

        assertThat(readings).extracting(Reading::fact).containsExactlyElementsOf(clean);
        assertThat(readings).extracting(Reading::source).containsOnly(Reading.Source.CURATED);
    }

    @Test
    void sameSeedGivesSameReadings() {
        var noise = new NoiseProfile(0.2, 2, 0.2, true, 42);

        assertThat(NoisyFacts.apply(clean, mosquito, noise)).isEqualTo(NoisyFacts.apply(clean, mosquito, noise));
    }

    @Test
    void nearSubjectVariesTheInjectedFactsAcrossSeedsForEveryCase() {
        List<Intent> intents = List.of(
                Intent.of(QueryKind.COMPANIONS, "Tomato"),
                new Intent(QueryKind.RELEVANT_PESTS, "Basil", "SUMMER"),
                Intent.of(QueryKind.COMPANIONS_OF_SHARED_REPELLERS, "Tomato"),
                mosquito,
                Intent.of(QueryKind.COMPANIONS, "Lavender"));
        for (Intent intent : intents) {
            Set<List<Fact>> injectedPerSeed = new HashSet<>();
            for (long seed = 1; seed <= 5; seed++) {
                injectedPerSeed.add(injected(intent, new NoiseProfile(0, 2, 0, true, seed)));
            }
            assertThat(injectedPerSeed).as(intent.subject()).hasSizeGreaterThan(1);
        }
    }

    @Test
    void withoutNearSubjectBasilAlwaysGetsTheSameTwoFalseFacts() {
        Intent basil = new Intent(QueryKind.RELEVANT_PESTS, "Basil", "SUMMER");
        Set<List<Fact>> injectedPerSeed = new HashSet<>();
        for (long seed = 1; seed <= 5; seed++) {
            injectedPerSeed.add(injected(basil, new NoiseProfile(0, 2, 0, false, seed)));
        }
        assertThat(injectedPerSeed).hasSize(1);
    }

    private List<Fact> injected(Intent intent, NoiseProfile noise) {
        return NoisyFacts.apply(clean, intent, noise).stream()
                .map(Reading::fact)
                .filter(f -> !clean.contains(f))
                .toList();
    }

    @Test
    void spuriousFactsAreFalseAndAboutTheSubject() {
        List<Reading> readings = NoisyFacts.apply(clean, mosquito, new NoiseProfile(0, 2, 0, false, 7));

        List<Fact> added = readings.stream().map(Reading::fact).filter(f -> !clean.contains(f)).toList();
        assertThat(added).hasSize(2).allSatisfy(f -> {
            assertThat(f.subject()).isEqualTo("Mosquito");
            assertThat(f.predicate()).isEqualTo("activeInSeason");
        });
    }
}
