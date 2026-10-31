package io.github.vincenzofiore.rootology.decision;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deterministic stand-in for Jev: plain rules over the raw facts, confidence is either 1.0 or 0.0.
 * Escalates when the subject is outside the dataset or the asked fact is not declared.
 */
@Component
public class StubDecisionLayer implements DecisionLayer {

    @Override
    public Decision decide(Intent intent, GraphFacts facts) {
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

    private Decision activeSeasons(String pest, GraphFacts facts) {
        List<String> seasons = facts.objects(pest, "activeInSeason");
        if (seasons.isEmpty()) {
            return Decision.escalated("active season of " + pest + " is not declared");
        }
        return Decision.certain(seasons, "declared active seasons of " + pest);
    }
}
