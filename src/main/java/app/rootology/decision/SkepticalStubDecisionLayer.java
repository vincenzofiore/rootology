package app.rootology.decision;

/**
 * Stronger baseline than {@link StubDecisionLayer}: the same rules, but UNCONFIRMED readings
 * are dropped first. Used to separate what Jev adds by judgment from what the source tag alone gives.
 */
public class SkepticalStubDecisionLayer implements DecisionLayer {

    private final StubDecisionLayer stub = new StubDecisionLayer();

    @Override
    public Decision decide(Intent intent, GraphFacts facts) {
        GraphFacts curatedOnly = new GraphFacts(facts.readings().stream()
                .filter(r -> r.source() == Reading.Source.CURATED)
                .toList());
        return stub.decide(intent, curatedOnly);
    }
}
