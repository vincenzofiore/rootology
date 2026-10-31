package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.inference.facts.Fact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkepticalStubDecisionLayerTest {

    private final DecisionLayer layer = new SkepticalStubDecisionLayer();

    @Test
    void ignoresUnconfirmedReadings() {
        var facts = new GraphFacts(List.of(
                Reading.curated(new Fact("Tomato", "companionOf", "Basil")),
                new Reading(new Fact("Tomato", "companionOf", "Marigold"), Reading.Source.UNCONFIRMED)));

        Decision decision = layer.decide(Intent.of(QueryKind.COMPANIONS, "Tomato"), facts);

        assertThat(decision.answer()).containsExactly("Basil");
    }

    @Test
    void escalatesWhenOnlyUnconfirmedReadingsMentionTheSubject() {
        var facts = new GraphFacts(List.of(
                Reading.curated(new Fact("Basil", "repels", "Mosquito")),
                new Reading(new Fact("Mosquito", "activeInSeason", "SUMMER"), Reading.Source.UNCONFIRMED)));

        Decision decision = layer.decide(Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"), facts);

        assertThat(decision.escalate()).isTrue();
    }
}
