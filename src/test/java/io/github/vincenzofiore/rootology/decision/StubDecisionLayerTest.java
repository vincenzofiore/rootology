package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubDecisionLayerTest {

    @Test
    void frostInTheForecastRulesACropOutForNow() {
        GraphFacts facts = new GraphFacts(List.of(
                Reading.curated(new Fact("Aglio", "plantIn", "NOVEMBER")),
                Reading.curated(new Fact("Lattuga", "sowIn", "NOVEMBER")),
                new Reading(new Fact("Forecast", "minimumAirTemperatureNext7Days", "-2.0"), Reading.Source.FORECAST,
                        List.of("met-norway"), null),
                Reading.inferred(new Fact("Lattuga", "avoidNow", "frost"), "frost-risk-v1",
                        List.of("met-norway", "fao-ecocrop"))));

        Decision decision = new StubDecisionLayer().decide(
                new Intent(QueryKind.SOW_NOW, null, null, "it", "CENTRO", "NOVEMBER"), facts);

        assertThat(decision.answer()).containsExactly("Aglio");
        assertThat(decision.reason()).contains("frost");
    }
}
