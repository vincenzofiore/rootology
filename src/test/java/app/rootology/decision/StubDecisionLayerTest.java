package app.rootology.decision;

import app.rootology.facts.Fact;
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

    @Test
    void controlMethodsAreTheDeclaredOnesOrGoToAPerson() {
        GraphFacts facts = GraphFacts.curated(List.of(new Fact("afidi", "controlledBy", "Spray with water"),
                new Fact("Spray with water", "controlKind", "PRACTICE"), new Fact("cavolaia", "affects", "Cavolo")));

        assertThat(new StubDecisionLayer().decide(Intent.of(QueryKind.CONTROL_METHODS, "afidi"), facts).answer())
                .containsExactly("Spray with water");
        assertThat(new StubDecisionLayer().decide(Intent.of(QueryKind.CONTROL_METHODS, "cavolaia"), facts).escalate())
                .isTrue();
    }

    @Test
    void jevGetsTheMethodsAsCandidatesAndTheirRule() {
        GraphFacts facts = GraphFacts.curated(List.of(new Fact("afidi", "controlledBy", "Spray with water"),
                new Fact("Spray with water", "evidence", "traditional")));

        assertThat(JevDecisionLayer.candidates(QueryKind.CONTROL_METHODS, facts)).containsExactly("Spray with water");
        assertThat(JevDecisionLayer.knowledgeBaseRule(QueryKind.CONTROL_METHODS)).contains("controlledBy");
    }

    @Test
    void aProposalAnswersWithTheCropsProposedForTheArea() {
        GraphFacts facts = GraphFacts.curated(List.of(new Fact("Aglio", "proposedFor", "Aiuola B"),
                new Fact("Aglio", "meetsGoal", "VARIETY")));

        assertThat(new StubDecisionLayer().decide(Intent.of(QueryKind.PROPOSE_IN_AREA, "Aiuola B"), facts).answer())
                .containsExactly("Aglio");
        assertThat(new StubDecisionLayer().decide(Intent.of(QueryKind.PROPOSE_IN_AREA, "Aiuola A"), facts).escalate())
                .isTrue();
    }

    @Test
    void jevGetsTheProposedCropsTheRuleAndTheGoals() {
        GraphFacts facts = GraphFacts.curated(List.of(new Fact("Aglio", "proposedFor", "Aiuola B"),
                new Fact("Aglio", "uncertainty", "LIGHT_UNKNOWN")));

        assertThat(JevDecisionLayer.candidates(QueryKind.PROPOSE_IN_AREA, facts)).containsExactly("Aglio");
        assertThat(JevDecisionLayer.knowledgeBaseRule(QueryKind.PROPOSE_IN_AREA)).contains("proposedFor", "uncertainty");
        assertThat(Intent.of(QueryKind.PROPOSE_IN_AREA, "Aiuola B").withGoals(List.of("SOIL")).bounded().goals())
                .containsExactly("SOIL");
        assertThat(Intent.of(QueryKind.SOW_NOW, null).goals()).as("no goals outside a proposal").isEmpty();
        assertThat(JevDecisionLayer.intent(Intent.of(QueryKind.PROPOSE_IN_AREA, "Aiuola B").withGoals(List.of("SOIL"))))
                .containsEntry("goals", List.of("SOIL"));
        assertThat(JevDecisionLayer.intent(Intent.of(QueryKind.SOW_NOW, null)))
                .as("the suite's requests keep no goals field").doesNotContainKey("goals");
    }
}
