package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.inference.facts.Fact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decision layer backed by TypeSafe Jev (System One API, Noul questions).
 * Code lists the candidate values; Jev judges, from the raw facts and the stated rule,
 * whether the question is answerable and whether each candidate belongs to the answer.
 */
public class JevDecisionLayer implements DecisionLayer {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionLayer.class);

    /** Below this, a judgment is treated as uncertain and sent to a human. A starting point, not tuned. */
    static final double MIN_CONFIDENCE = 0.5;

    private final RestClient restClient;
    private final String model;

    public JevDecisionLayer(RestClient restClient, String model) {
        this.restClient = restClient;
        this.model = model;
    }

    @Override
    public Decision decide(Intent intent, GraphFacts facts) {
        if (intent.kind() == QueryKind.OTHER) {
            return Decision.escalated("the question does not fit any supported shape");
        }
        List<String> candidates = candidates(intent.kind(), facts);

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("answerable", noul("""
                Using only `facts`, can the question described by `intent` be answered? \
                Answer no if `intent.subject` does not appear in any fact, or if no fact declares \
                the relation the question asks about for `intent.subject`."""));
        for (int i = 0; i < candidates.size(); i++) {
            questions.put("candidate_" + i, noul("""
                    Using only `facts` and applying `rule`, is "%s" part of the answer \
                    to the question described by `intent`?""".formatted(candidates.get(i))));
        }
        Map<String, Object> request = Map.of(
                "model", model,
                "state", Map.of("intent", intent, "rule", rule(intent.kind()), "facts", facts.facts()),
                "questions", questions);

        JevResponse response = restClient.post()
                .uri("/v1/systemone")
                .body(request)
                .retrieve()
                .body(JevResponse.class);

        double answerable = response.noul("answerable");
        log.info("Jev {} {}: answerable={}", intent.kind(), intent.subject(), answerable);
        if (answerable < 0.5) {
            return new Decision(List.of(), 1 - answerable, true,
                    "Jev: the facts do not answer this (p=%.2f)".formatted(answerable));
        }
        List<String> answer = new ArrayList<>();
        double confidence = confidence(answerable);
        for (int i = 0; i < candidates.size(); i++) {
            double p = response.noul("candidate_" + i);
            log.info("Jev   {}={}", candidates.get(i), p);
            if (p > 0.5) {
                answer.add(candidates.get(i));
            }
            confidence = Math.min(confidence, confidence(p));
        }
        boolean escalate = confidence < MIN_CONFIDENCE;
        return new Decision(answer, confidence, escalate,
                (escalate ? "Jev: low confidence" : "Jev: confident") + " (%.2f)".formatted(confidence));
    }

    /** Noul confidence-style value suggested by TypeSafe: distance from an even 0.5. */
    private static double confidence(double p) {
        return Math.abs(2 * p - 1);
    }

    private static Map<String, String> noul(String instructions) {
        return Map.of("type", "noul", "instructions", instructions);
    }

    /** Exact lookup in code: every entity of the type the answer must have. */
    static List<String> candidates(QueryKind kind, GraphFacts facts) {
        Set<String> result = new LinkedHashSet<>();
        for (Fact f : facts.facts()) {
            switch (kind) {
                case COMPANIONS, COMPANIONS_OF_SHARED_REPELLERS -> {
                    if (!f.predicate().equals("activeInSeason")) {
                        result.add(f.subject());
                    }
                    if (f.predicate().equals("companionOf")) {
                        result.add(f.object());
                    }
                }
                case RELEVANT_PESTS -> {
                    if (f.predicate().equals("repels")) {
                        result.add(f.object());
                    }
                    if (f.predicate().equals("activeInSeason")) {
                        result.add(f.subject());
                    }
                }
                case ACTIVE_SEASONS -> {
                    if (f.predicate().equals("activeInSeason") || f.predicate().equals("growsInSeason")) {
                        result.add(f.object());
                    }
                }
                case OTHER -> { }
            }
        }
        return List.copyOf(result);
    }

    /** The domain rule for each question shape, stated in plain words for Jev. */
    static String rule(QueryKind kind) {
        return switch (kind) {
            case COMPANIONS -> "The answer is every plant X such that the fact (subject, companionOf, X) "
                    + "is declared. Companions are never inferred from shared soil or season.";
            case RELEVANT_PESTS -> "The answer is every pest active in `intent.season` (activeInSeason) "
                    + "that `intent.subject` does not repel (repels).";
            case COMPANIONS_OF_SHARED_REPELLERS -> "Take every pest P that `intent.subject` repels. Take every "
                    + "plant Q, other than `intent.subject`, that also repels P. The answer is every plant "
                    + "declared as companionOf of such a Q.";
            case ACTIVE_SEASONS -> "The answer is every season S such that the fact (subject, activeInSeason, S) "
                    + "is declared. Seasons are never guessed.";
            case OTHER -> "";
        };
    }

    record JevResponse(Map<String, JevAnswer> answers) {

        double noul(String question) {
            return answers.get(question).noul();
        }
    }

    record JevAnswer(String type, double noul) {
    }
}
