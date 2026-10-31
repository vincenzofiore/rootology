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
 * Code lists the candidate values; Jev judges, from the readings (facts tagged with their source)
 * and the stated rule, whether the question is answerable and whether each candidate belongs to the answer.
 */
public class JevDecisionLayer implements DecisionLayer {

    private static final Logger log = LoggerFactory.getLogger(JevDecisionLayer.class);

    /** Below this, a judgment is treated as uncertain and sent to a human. A starting point, not tuned. */
    public static final double MIN_CONFIDENCE = 0.5;

    static final Map<String, Object> SOURCES = ordered(
            "CURATED", "From the curated garden dataset.",
            "UNCONFIRMED", "From a single unverified report. It may be wrong.");

    private final RestClient restClient;
    private final String model;

    public JevDecisionLayer(RestClient restClient, String model) {
        this.restClient = restClient;
        this.model = model;
    }

    /** Jev's raw yes-probabilities, before any threshold is applied. */
    public record Judgments(double answerable, Map<String, Double> candidates) {
    }

    @Override
    public Decision decide(Intent intent, GraphFacts facts) {
        if (intent.kind() == QueryKind.OTHER) {
            return Decision.escalated("the question does not fit any supported shape");
        }
        return decide(judge(intent, facts), MIN_CONFIDENCE);
    }

    /** One call to Jev: is the question answerable, and is each candidate part of the answer. */
    public Judgments judge(Intent intent, GraphFacts facts) {
        List<String> candidates = candidates(intent.kind(), facts);

        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("answerable", noul("""
                Using only `facts`, and weighing each fact by its `source` as described in `sources`, \
                can the question described by `intent` be answered? Answer no if `intent.subject` \
                does not appear in any fact, or if no fact declares the relation the question asks \
                about for `intent.subject`."""));
        for (int i = 0; i < candidates.size(); i++) {
            questions.put("candidate_" + i, noul("""
                    Using only `facts`, weighing each fact by its `source` as described in `sources`, \
                    and applying `rule`, is "%s" part of the answer to the question described \
                    by `intent`?""".formatted(candidates.get(i))));
        }
        List<Map<String, Object>> stateFacts = facts.readings().stream()
                .map(r -> ordered(
                        "subject", r.fact().subject(),
                        "predicate", r.fact().predicate(),
                        "object", r.fact().object(),
                        "source", r.source().name()))
                .toList();
        Map<String, Object> request = ordered(
                "model", model,
                "state", ordered(
                        "intent", intent,
                        "rule", rule(intent.kind()),
                        "sources", SOURCES,
                        "facts", stateFacts),
                "questions", questions);

        JevResponse response = restClient.post()
                .uri("/v1/systemone")
                .body(request)
                .retrieve()
                .body(JevResponse.class);

        double answerable = response.noul("answerable");
        log.info("Jev {} {}: answerable={}", intent.kind(), intent.subject(), answerable);
        Map<String, Double> judged = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            double p = response.noul("candidate_" + i);
            log.info("Jev   {}={}", candidates.get(i), p);
            judged.put(candidates.get(i), p);
        }
        return new Judgments(answerable, judged);
    }

    /**
     * The policy, kept apart from the call so thresholds can be compared on the same judgments:
     * escalate when the facts do not answer, or when any judgment is less confident than minConfidence.
     */
    public static Decision decide(Judgments judgments, double minConfidence) {
        double answerable = judgments.answerable();
        if (answerable < 0.5) {
            return new Decision(List.of(), 1 - answerable, true,
                    "Jev: the facts do not answer this (p=%.2f)".formatted(answerable));
        }
        List<String> answer = new ArrayList<>();
        double confidence = confidence(answerable);
        for (Map.Entry<String, Double> candidate : judgments.candidates().entrySet()) {
            if (candidate.getValue() > 0.5) {
                answer.add(candidate.getKey());
            }
            confidence = Math.min(confidence, confidence(candidate.getValue()));
        }
        boolean escalate = confidence < minConfidence;
        return new Decision(answer, confidence, escalate,
                (escalate ? "Jev: low confidence" : "Jev: confident") + " (%.2f)".formatted(confidence));
    }

    /** Noul confidence-style value suggested by TypeSafe: distance from an even 0.5. */
    private static double confidence(double p) {
        return Math.abs(2 * p - 1);
    }

    private static Map<String, Object> noul(String instructions) {
        return ordered("type", "noul", "instructions", instructions);
    }

    /**
     * A map that keeps the order its keys are written in. Jev reads fields in the order they
     * are sent and the order can change an answer, while Map.of iterates in a different
     * order on every JVM run.
     */
    private static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** Exact lookup in code: every entity of the type the answer must have. */
    static List<String> candidates(QueryKind kind, GraphFacts facts) {
        Set<String> result = new LinkedHashSet<>();
        for (Reading reading : facts.readings()) {
            Fact f = reading.fact();
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
