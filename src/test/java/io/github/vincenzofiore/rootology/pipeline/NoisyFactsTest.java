package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.decision.Reading;
import io.github.vincenzofiore.rootology.inference.JenaModelService;
import io.github.vincenzofiore.rootology.inference.facts.Fact;
import io.github.vincenzofiore.rootology.inference.facts.FactSource;
import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NoisyFactsTest {

    private final List<Fact> clean = new FactSource(new JenaModelService(new OntologyDefinition())).allFacts();
    private final Intent mosquito = Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito");

    @Test
    void noNoiseKeepsEveryFactAsCurated() {
        List<Reading> readings = NoisyFacts.apply(clean, mosquito, NoiseProfile.NONE);

        assertThat(readings).extracting(Reading::fact).containsExactlyElementsOf(clean);
        assertThat(readings).extracting(Reading::source).containsOnly(Reading.Source.CURATED);
    }

    @Test
    void sameSeedGivesSameReadings() {
        var noise = new NoiseProfile(0.2, 2, 0.2, 42);

        assertThat(NoisyFacts.apply(clean, mosquito, noise)).isEqualTo(NoisyFacts.apply(clean, mosquito, noise));
    }

    @Test
    void spuriousFactsAreFalseAndAboutTheSubject() {
        List<Reading> readings = NoisyFacts.apply(clean, mosquito, new NoiseProfile(0, 2, 0, 7));

        List<Fact> added = readings.stream().map(Reading::fact).filter(f -> !clean.contains(f)).toList();
        assertThat(added).hasSize(2).allSatisfy(f -> {
            assertThat(f.subject()).isEqualTo("Mosquito");
            assertThat(f.predicate()).isEqualTo("activeInSeason");
        });
    }
}
