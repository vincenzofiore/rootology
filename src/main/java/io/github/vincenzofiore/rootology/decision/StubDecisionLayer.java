package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;


import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic stand-in for Jev: plain rules over the raw facts, confidence is either 1.0 or 0.0.
 * Trusts every reading, whatever its source.
 * Escalates when the question shape is unsupported, the subject is outside the dataset,
 * or the asked fact is not declared.
 */
public class StubDecisionLayer implements DecisionLayer {

    @Override
    public Decision decide(Intent intent, GraphFacts facts) {
        if (intent.kind() == QueryKind.OTHER) {
            return Decision.escalated("the question does not fit any supported shape");
        }
        if (intent.kind() == QueryKind.SOW_NOW) {
            return sowNow(intent.month(), facts);
        }
        if (intent.kind() == QueryKind.GROWING_NOW) {
            List<String> growing = facts.facts().filter(f -> f.predicate().equals("grows"))
                    .map(Fact::object).distinct().toList();
            return growing.isEmpty() ? Decision.escalated("nothing is recorded as growing in this space's gardens")
                    : Decision.certain(growing, "crops growing now in this space's gardens");
        }
        if (intent.kind() == QueryKind.PEST_RISK) {
            List<String> threats = facts.facts().filter(f -> f.predicate().equals("threatens"))
                    .map(Fact::subject).distinct().toList();
            if (facts.facts().noneMatch(f -> f.predicate().equals("grows"))) {
                return Decision.escalated("nothing is recorded as growing in this space's gardens");
            }
            return Decision.certain(threats, "pests and diseases active in " + intent.month()
                    + " on the crops growing now");
        }
        String subject = intent.subject();
        if (!facts.mentions(subject)) {
            return Decision.escalated(subject + " is not in the dataset");
        }
        return switch (intent.kind()) {
            case COMPANIONS -> Decision.certain(
                    facts.objects(subject, "companionOf"),
                    "declared companions of " + subject);
            case RELEVANT_PESTS -> relevantPests(subject, intent.season(), facts);
            case COMPANIONS_OF_SHARED_REPELLERS -> companionsOfSharedRepellers(subject, facts);
            case ACTIVE_SEASONS -> activeSeasons(subject, facts);
            case ANTAGONISTS -> Decision.certain(facts.objects(subject, "antagonistOf"),
                    "declared antagonists of " + subject);
            case SOWING_TIME -> months(subject, facts, List.of("sowIn", "plantIn"), "sowing or planting");
            case HARVEST_TIME -> months(subject, facts, List.of("harvestIn"), "harvest");
            case SOW_IN_PLOT -> sowInPlot(subject, intent.month(), facts);
            case SOW_NOW, GROWING_NOW, PEST_RISK, OTHER -> throw new IllegalStateException("handled above");
        };
    }

    private Decision relevantPests(String plant, String season, GraphFacts facts) {
        List<String> repelled = facts.objects(plant, "repels");
        List<String> relevant = facts.subjects("activeInSeason", season).stream()
                .filter(pest -> !repelled.contains(pest))
                .toList();
        return Decision.certain(relevant, "pests active in " + season + " minus those " + plant + " repels");
    }

    private Decision companionsOfSharedRepellers(String plant, GraphFacts facts) {
        // Without any declared repels fact the honest answer is "unknown", not "no companions" (#20).
        if (facts.objects(plant, "repels").isEmpty()) {
            return Decision.escalated("what " + plant + " repels is not declared");
        }
        Set<String> companions = new LinkedHashSet<>();
        for (String pest : facts.objects(plant, "repels")) {
            for (String other : facts.subjects("repels", pest)) {
                if (!other.equals(plant)) {
                    companions.addAll(facts.objects(other, "companionOf"));
                }
            }
        }
        return Decision.certain(List.copyOf(companions),
                "companions of other plants repelling a pest " + plant + " repels");
    }

    private Decision months(String crop, GraphFacts facts, List<String> predicates, String what) {
        List<String> months = predicates.stream().flatMap(p -> facts.objects(crop, p).stream()).distinct().toList();
        if (months.isEmpty()) {
            return Decision.escalated(what + " months of " + crop + " are not declared");
        }
        return Decision.certain(months, what + " months of " + crop);
    }

    private Decision sowInPlot(String plot, String month, GraphFacts facts) {
        Decision sowable = sowNow(month, facts);
        if (sowable.escalate()) {
            return sowable;
        }
        List<String> avoided = facts.subjects("avoidIn", plot);
        return Decision.certain(sowable.answer().stream().filter(c -> !avoided.contains(c)).toList(),
                "crops to sow or plant in " + month + " in " + plot + ", minus those against rotation");
    }

    private Decision sowNow(String month, GraphFacts facts) {
        List<String> crops = java.util.stream.Stream.concat(facts.subjects("sowIn", month).stream(),
                facts.subjects("plantIn", month).stream()).distinct().toList();
        if (crops.isEmpty()) {
            return Decision.escalated("nothing is declared to sow or plant in " + month);
        }
        // Frost in the forecast (frost-risk-v1) rules a crop out for now.
        List<String> frost = facts.subjects("avoidNow", "frost");
        return Decision.certain(crops.stream().filter(c -> !frost.contains(c)).toList(),
                frost.isEmpty() ? "crops to sow or plant in " + month
                        : "crops to sow or plant in " + month + ", minus those at risk of frost");
    }

    private Decision activeSeasons(String pest, GraphFacts facts) {
        List<String> seasons = facts.objects(pest, "activeInSeason");
        if (seasons.isEmpty()) {
            return Decision.escalated("active season of " + pest + " is not declared");
        }
        return Decision.certain(seasons, "declared active seasons of " + pest);
    }
}
