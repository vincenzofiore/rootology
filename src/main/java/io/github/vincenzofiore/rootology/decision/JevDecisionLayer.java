package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;
import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
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

    /** Sent only when some reading is inferred, so requests without inferred facts stay exactly as recorded. */
    static final Map<String, Object> SOURCES_WITH_INFERRED = ordered(
            "CURATED", "From the curated garden dataset.",
            "UNCONFIRMED", "From a single unverified report. It may be wrong.",
            "INFERRED", "Derived from curated facts by the rule named in `derivedBy`. As reliable as that rule.");

    /** Added only when some reading is proposed (#24), so other requests stay exactly as they were. */
    static final Map<String, Object> PROPOSED = ordered(
            "PROPOSED", "Added by a member of the asking space and not reviewed by a curator. Plausible, but it may be wrong.");

    /** Added only when some reading comes from the weather forecast (#18). */
    static final Map<String, Object> FORECAST = ordered(
            "FORECAST", "From the weather forecast for the garden's location. Likely, but less certain further ahead.");

    /** Added only when some reading is declared by the gardener (#54), such as a plot's sun and wind. */
    static final Map<String, Object> DECLARED = ordered(
            "DECLARED", "Declared by the person who grows, not measured. Plausible, but less reliable than a measurement.");

    private final RestClient restClient;
    private final String model;
    private final CircuitBreaker breaker;

    public JevDecisionLayer(RestClient restClient, String model) {
        this(restClient, model, new CircuitBreaker("Jev"));
    }

    public JevDecisionLayer(RestClient restClient, String model, CircuitBreaker breaker) {
        this.restClient = restClient;
        this.model = model;
        this.breaker = breaker;
    }

    static Map<String, Object> sources(boolean anyInferred, boolean anyProposed, boolean anyForecast,
                                       boolean anyDeclared) {
        Map<String, Object> sources = anyInferred ? SOURCES_WITH_INFERRED : SOURCES;
        if (!anyProposed && !anyForecast && !anyDeclared) {
            return sources;
        }
        Map<String, Object> extended = new LinkedHashMap<>(sources);
        if (anyProposed) {
            extended.putAll(PROPOSED);
        }
        if (anyForecast) {
            extended.putAll(FORECAST);
        }
        if (anyDeclared) {
            extended.putAll(DECLARED);
        }
        return extended;
    }

    /** Jev's raw yes-probabilities, before any threshold is applied. */
    public record Judgments(double answerable, Map<String, Double> candidates) {
    }

    @Override
    public Decision decide(Intent intent, GraphFacts facts) {
        if (intent.kind() == QueryKind.OTHER) {
            return Decision.escalated("the question does not fit any supported shape");
        }
        // When Jev cannot be reached, a person decides: the human in the loop is the fallback (#29).
        if (!breaker.allowsCall()) {
            return Decision.escalated("the decision layer is not available right now");
        }
        Judgments judgments;
        Timer.Sample call = breaker.startCall();
        try {
            judgments = judge(intent, facts);
            breaker.success(call);
        } catch (RuntimeException e) {
            breaker.failure(call);
            // The exception's message may carry the response body, which can repeat the request's facts (#27).
            log.warn("Jev call failed: {}", e.getClass().getSimpleName());
            return Decision.escalated("the decision layer did not answer");
        }
        return decide(judgments, MIN_CONFIDENCE);
    }

    /** One call to Jev: is the question answerable, and is each candidate part of the answer. */
    public Judgments judge(Intent intent, GraphFacts facts) {
        // Facts of the knowledge base carry their sources; the suite's do not. KB-only wording is sent only
        // then, so the suite's requests stay exactly as recorded (#35).
        boolean knowledgeBase = facts.readings().stream().anyMatch(r -> !r.sourceIds().isEmpty());
        List<String> candidates = candidates(intent.kind(), facts);
        if (knowledgeBase && NONE_IS_AN_ANSWER.contains(intent.kind()) && intent.subject() != null) {
            // In the knowledge base no crop pairs with itself (KnowledgeValidator): not a candidate.
            candidates = candidates.stream().filter(c -> !c.equalsIgnoreCase(intent.subject().trim())).toList();
        }
        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("answerable", noul(knowledgeBase && NONE_IS_AN_ANSWER.contains(intent.kind()) ? """
                Using only `facts`, and weighing each fact by its `source` as described in `sources`, \
                can the question described by `intent` be answered? Answer no if `intent.subject` \
                does not appear in any fact. If it appears but no fact declares the relation the \
                question asks about, answer yes: the answer is that there is none."""
                : knowledgeBase && intent.kind() == QueryKind.SOW_IN_PLOT ? """
                Using only `facts`, and weighing each fact by its `source` as described in `sources`, \
                can the question described by `intent` be answered? `intent.subject` is a plot of the \
                garden. Answer no if it does not appear in any fact, or if no crop can be sown or \
                planted in `intent.month`."""
                : SUBJECTLESS.contains(intent.kind()) ? """
                Using only `facts`, and weighing each fact by its `source` as described in `sources`, \
                can the question described by `intent` be answered? Answer no if the facts do not \
                contain the relation the question asks about.""" : """
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
                .map(r -> {
                    Map<String, Object> fact = ordered(
                            "subject", r.fact().subject(),
                            "predicate", r.fact().predicate(),
                            "object", r.fact().object(),
                            "source", r.source().name());
                    if (r.derivedBy() != null) {
                        fact.put("derivedBy", r.derivedBy());
                    }
                    return fact;
                })
                .toList();
        boolean anyInferred = facts.readings().stream().anyMatch(r -> r.source() == Reading.Source.INFERRED);
        boolean anyProposed = facts.readings().stream().anyMatch(r -> r.source() == Reading.Source.PROPOSED);
        boolean anyForecast = facts.readings().stream().anyMatch(r -> r.source() == Reading.Source.FORECAST);
        boolean anyDeclared = facts.readings().stream().anyMatch(r -> r.source() == Reading.Source.DECLARED);
        Map<String, Object> state = ordered(
                // Field by field, so the language (not needed to decide) never changes what Jev sees.
                "intent", intent(intent),
                "rule", knowledgeBase ? knowledgeBaseRule(intent.kind()) : rule(intent.kind()),
                "sources", sources(anyInferred, anyProposed, anyForecast, anyDeclared),
                "facts", stateFacts);
        Map<String, Object> request = ordered("model", model, "state", state, "questions", questions);

        JevResponse response = restClient.post()
                .uri("/v1/systemone")
                .body(request)
                .retrieve()
                .body(JevResponse.class);

        double answerable = response.noul("answerable");
        // Debug only: the subject may be a plot name a person chose (#40).
        log.debug("Jev {} {}: answerable={}", intent.kind(), intent.subject(), answerable);
        Map<String, Double> judged = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            double p = response.noul("candidate_" + i);
            log.debug("Jev   {}={}", candidates.get(i), p);
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
    static Map<String, Object> ordered(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** Kinds whose question has no subject to look for in the facts. */
    static final Set<QueryKind> SUBJECTLESS = Set.of(QueryKind.SOW_NOW, QueryKind.GROWING_NOW, QueryKind.PEST_RISK);

    /** Kind, subject and season as always; zone and month only when set, so suite requests are unchanged. */
    private static Map<String, Object> intent(Intent intent) {
        Map<String, Object> map = ordered("kind", intent.kind(), "subject", intent.subject(), "season", intent.season());
        if (intent.zone() != null) {
            map.put("zone", intent.zone());
        }
        if (intent.month() != null) {
            map.put("month", intent.month());
        }
        return map;
    }

    /** Exact lookup in code: every entity of the type the answer must have. */
    static List<String> candidates(QueryKind kind, GraphFacts facts) {
        Set<String> result = new LinkedHashSet<>();
        for (Reading reading : facts.readings()) {
            Fact f = reading.fact();
            switch (kind) {
                case COMPANIONS, COMPANIONS_OF_SHARED_REPELLERS, ANTAGONISTS -> {
                    if (!f.predicate().equals("activeInSeason") && !f.predicate().equals("affects")) {
                        result.add(f.subject());
                    }
                    if (f.predicate().equals("companionOf") || f.predicate().equals("antagonistOf")) {
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
                case SOWING_TIME -> {
                    if (f.predicate().equals("sowIn") || f.predicate().equals("plantIn")) {
                        result.add(f.object());
                    }
                }
                case HARVEST_TIME -> {
                    if (f.predicate().equals("harvestIn")) {
                        result.add(f.object());
                    }
                }
                case SOW_NOW, SOW_IN_PLOT -> {
                    if (f.predicate().equals("sowIn") || f.predicate().equals("plantIn")) {
                        result.add(f.subject());
                    }
                }
                case GROWING_NOW -> {
                    if (f.predicate().equals("grows")) {
                        result.add(f.object());
                    }
                }
                case PEST_RISK -> {
                    if (f.predicate().equals("threatens")) {
                        result.add(f.subject());
                    }
                }
                case OTHER -> { }
            }
        }
        return List.copyOf(result);
    }

    /** On the knowledge base, a declared relation that has no value means "none", not "unknown" (#35). */
    static final Set<QueryKind> NONE_IS_AN_ANSWER = Set.of(QueryKind.COMPANIONS, QueryKind.ANTAGONISTS);

    /**
     * The rule as stated for facts of the knowledge base, where it differs from the suite's: there are no
     * repels facts, and sowing facts are already those of the asked zone (#35).
     */
    static String knowledgeBaseRule(QueryKind kind) {
        return switch (kind) {
            case RELEVANT_PESTS -> "The answer is every pest or disease X such that (X, activeInSeason, "
                    + "`intent.season`) and (X, affects, `intent.subject`) are facts. The knowledge base declares "
                    + "no repels facts, so no pest is excluded for being repelled.";
            case SOW_NOW -> "The answer is every crop X such that (X, sowIn, `intent.month`) or "
                    + "(X, plantIn, `intent.month`) is a fact, unless (X, avoidNow, C) is a fact for a condition C "
                    + "such as frost or too little light. " + SITE;
            case SOW_IN_PLOT -> "The answer is every crop X such that (X, sowIn, `intent.month`) or "
                    + "(X, plantIn, `intent.month`) is a fact, unless (X, avoidIn, `intent.subject`) is a fact or "
                    + "(X, avoidNow, C) is a fact for a condition C such as frost or too little light. " + SITE;
            case SOWING_TIME -> "The answer is every month M such that (subject, sowIn, M) or (subject, plantIn, M) "
                    + "is a fact. " + SITE;
            case HARVEST_TIME -> "The answer is every month M such that (subject, harvestIn, M) is a fact. " + SITE;
            default -> rule(kind);
        };
    }

    /**
     * Where the knowledge base's calendar facts apply (#62): the site of the question, a garden's climate
     * profile or a zone's reference site, and they are complete for it, so a missing crop or month is a no.
     */
    static final String SITE = "The calendar facts are already those of the question's site, with its frost "
            + "dates taken into account.";

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
            case ANTAGONISTS -> "The answer is every plant X such that the fact (subject, antagonistOf, X) is "
                    + "declared. Antagonists are never inferred, and companions are not antagonists.";
            case SOWING_TIME -> "The answer is every month M such that (subject, sowIn, M) or (subject, plantIn, M) "
                    + "is a fact for `intent.zone`.";
            case HARVEST_TIME -> "The answer is every month M such that (subject, harvestIn, M) is a fact for "
                    + "`intent.zone`.";
            case SOW_NOW -> "The answer is every crop X such that (X, sowIn, `intent.month`) or "
                    + "(X, plantIn, `intent.month`) is a fact for `intent.zone`, unless (X, avoidNow, frost) is a fact.";
            case GROWING_NOW -> "The answer is every crop X such that (P, grows, X) is a fact for some plot P.";
            case PEST_RISK -> "The answer is every pest or disease X such that (X, threatens, C) is a fact for some "
                    + "crop C growing now.";
            case SOW_IN_PLOT -> "The answer is every crop X such that (X, sowIn, `intent.month`) or "
                    + "(X, plantIn, `intent.month`) is a fact, unless (X, avoidIn, `intent.subject`) or "
                    + "(X, avoidNow, frost) is a fact.";
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
