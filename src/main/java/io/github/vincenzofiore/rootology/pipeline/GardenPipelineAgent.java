package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.hitl.AwaitableResponseException;
import com.embabel.agent.core.hitl.TypeRequest;
import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.DecisionLayer;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Reading;
import io.github.vincenzofiore.rootology.facts.FactSource;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;
import io.github.vincenzofiore.rootology.facts.SourcedFact;

import io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException;
import io.github.vincenzofiore.rootology.weather.Forecast;

import java.util.List;
import java.util.Set;

/**
 * Third contestant. Blackboard flow:
 * Question (+ NoiseProfile, ReviewMode) -> Intent -> GraphFacts -> Decision -> Resolution -> PipelineAnswer.
 * In HUMAN review mode an escalated decision pauses the agent until a person answers.
 * Claude only reads the question and phrases the answer; it never sees the facts and never
 * chooses the answer values, which come from the decision layer.
 */
@Agent(description = "Answers closed-domain companion-planting questions from raw graph facts")
public class GardenPipelineAgent {

    // No dataset content here on purpose: Claude must parse the question, not answer it.
    static final String UNDERSTAND_PROMPT = """
            Read a gardening question and classify it into a structured intent.

            kind is one of:
            - COMPANIONS: which plants are companions of a plant
            - RELEVANT_PESTS: which pests are relevant for a plant in a given season
            - COMPANIONS_OF_SHARED_REPELLERS: companions of the plants that repel the same pest as a plant
            - ACTIVE_SEASONS: in which seasons a pest is active
            - ANTAGONISTS: which plants should not be planted next to a plant
            - SOWING_TIME: when to sow or plant a plant
            - HARVEST_TIME: when to harvest a plant
            - SOW_NOW: what can be sown or planted now, or in a given month (no subject)
            - GROWING_NOW: what is growing in my garden now (no subject)
            - PEST_RISK: which pests or diseases threaten what I grow, now or in a given month (no subject)
            - SOW_IN_PLOT: what can I sow or plant in one of my plots (subject: the plot's name, as written)
            - OTHER: anything else

            subject: the plant or pest the question is about, exactly as named in the question and in
            the question's language (e.g. "Tomato" in an English question, "pomodorino" in an Italian
            one). Do not translate, correct or replace it: local and dialect names are looked up as written.
            season: SPRING, SUMMER, AUTUMN or WINTER for RELEVANT_PESTS, otherwise null.
            language: the ISO 639-1 code of the language the question is written in (for example "it" or "en").
            zone: NORD, CENTRO or SUD if the question names the north, centre or south of Italy (or a place
            there), otherwise null.
            month: JANUARY to DECEMBER if the question names a month, otherwise null.
            Do not answer the question.

            Question: %s
            """;

    static final String PHRASE_PROMPT = """
            Write a one or two sentence answer to a gardening question, in the language of the question.
            Use only the values given below. Do not add, remove or rename any plant, pest or
            season (season names may be translated), and do not add gardening advice.
            If "escalated" is true, say that the question is being handed to a human and why.

            Question: %s
            Values: %s
            Escalated: %s
            Reason: %s
            """;

    static final String NOTES_PROMPT = """
            Notes: %s
            Mention the notes briefly, in the language of the question, without adding anything to them.
            """;

    static final String HUMAN_PHRASE_PROMPT = """
            Write a one or two sentence answer to a gardening question, in the language of the question.
            Say that a human reviewer answered it, and use only the values given below.
            Do not add, remove or rename any plant, pest or season, and do not add gardening advice.

            Question: %s
            Values: %s
            Reviewer's note: %s
            """;

    private final FactSource factSource;
    private final KnowledgeGraph knowledge;
    private final GardenFacts garden;
    private final WeatherFacts weather;
    private final DecisionLayer decisionLayer;
    private final ClaudeCircuit claude;
    private final java.time.Clock clock;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GardenPipelineAgent.class);

    GardenPipelineAgent(FactSource factSource, KnowledgeGraph knowledge, GardenFacts garden, WeatherFacts weather,
                        DecisionLayer decisionLayer, ClaudeCircuit claude, java.time.Clock clock) {
        this.clock = clock;
        this.factSource = factSource;
        this.knowledge = knowledge;
        this.garden = garden;
        this.weather = weather;
        this.decisionLayer = decisionLayer;
        this.claude = claude;
    }

    /** Italy is where the knowledge base's windows apply; zones and the current month follow it. */
    static final java.time.ZoneId ITALY = java.time.ZoneId.of("Europe/Rome");

    @Action(description = "Turn the question text into a structured intent")
    public Intent understand(Question question, RunContext run, OperationContext context) {
        // Without Claude the question cannot be understood: fail at once and clearly (#29).
        if (!claude.breaker.allowsCall()) {
            throw new ServiceUnavailableException(ClaudeCircuit.UNAVAILABLE);
        }
        Intent intent;
        try {
            intent = context.ai().withDefaultLlm()
                    .createObject(UNDERSTAND_PROMPT.formatted(question.text()), Intent.class);
            claude.breaker.success();
        } catch (RuntimeException e) {
            claude.breaker.failure();
            throw new ServiceUnavailableException(ClaudeCircuit.UNAVAILABLE, e);
        }
        // The question's zone first, then the zone of the asking space's garden, then CENTRO.
        if (intent.zone() == null && run.hasSpace() && NEEDS_ZONE.contains(intent.kind())) {
            String gardenZone = garden.zone(run.spaceId(), run.startedBy()).orElse(null);
            intent = intent.withZoneAndMonth(gardenZone, intent.month());
        }
        return withDefaults(intent);
    }

    static final java.util.Set<QueryKind> NEEDS_ZONE = java.util.Set.of(QueryKind.SOWING_TIME, QueryKind.HARVEST_TIME,
            QueryKind.SOW_NOW, QueryKind.SOW_IN_PLOT, QueryKind.PEST_RISK);

    private Intent withDefaults(Intent intent) {
        return withDefaults(intent, java.time.LocalDate.now(clock).getMonth());
    }

    /** Zone CENTRO unless already set; for the kinds about a month, the current month in Italy. */
    public static Intent withDefaults(Intent intent, java.time.Month currentMonth) {
        if (!NEEDS_ZONE.contains(intent.kind())) {
            return intent;
        }
        String zone = intent.zone() == null ? "CENTRO" : intent.zone();
        String month = intent.month() != null || intent.kind() == QueryKind.SOWING_TIME
                || intent.kind() == QueryKind.HARVEST_TIME ? intent.month()
                : currentMonth.name();
        return intent.withZoneAndMonth(zone, month);
    }

    /**
     * SUITE: the quality suite's dataset, degraded by the noise profile. KNOWLEDGE_BASE: the curated
     * facts about the question's subject, each with its sources (no noise: they are real data).
     */
    @Action(description = "Load the raw declared facts from the chosen graph")
    public GraphFacts gatherFacts(Intent intent, NoiseProfile noise, FactScope scope, RunContext run) {
        if (scope == FactScope.KNOWLEDGE_BASE) {
            Intent full = withDefaults(intent);
            // The asking space also sees the facts its members added (#24), tagged PROPOSED.
            Long space = run.hasSpace() ? run.spaceId() : null;
            List<SourcedFact> facts = switch (full.kind()) {
                case SOWING_TIME, HARVEST_TIME -> knowledge.windowFacts(full.subject(), full.zone(), space);
                case SOW_NOW -> knowledge.sowingFacts(full.month(), full.zone(), full.languageOrDefault());
                case GROWING_NOW -> garden.growing(run, full.languageOrDefault());
                case PEST_RISK -> garden.threats(run, full.month(), full.languageOrDefault());
                case SOW_IN_PLOT -> garden.sowableIn(run, full.subject(), full.month(), full.zone(),
                        full.languageOrDefault());
                default -> knowledge.factsAbout(full.subject(), full.languageOrDefault(), space);
            };
            // The local forecast (#18) for questions about now: frost for sowing, blight for pest risk.
            WeatherFacts.Result local = switch (full.kind()) {
                case SOW_NOW, SOW_IN_PLOT -> weather.frost(run, full.month(), facts.stream()
                        .filter(f -> Set.of("sowIn", "plantIn").contains(f.fact().predicate())).toList());
                case PEST_RISK -> weather.blight(run, full.month(), full.languageOrDefault());
                default -> WeatherFacts.Result.NONE;
            };
            List<Reading> readings = new java.util.ArrayList<>(facts.stream()
                    .map(f -> new Reading(f.fact(), f.derivedBy() != null ? Reading.Source.INFERRED
                            : f.proposed() ? Reading.Source.PROPOSED : Reading.Source.CURATED,
                            f.sourceIds(), f.derivedBy()))
                    .toList());
            readings.addAll(local.readings());
            List<io.github.vincenzofiore.rootology.decision.Note> notes = new java.util.ArrayList<>(local.notes());
            if (io.github.vincenzofiore.rootology.garden.GardenService.OUTSIDE_ITALY.equals(full.zone())) {
                // No window matches this zone, so the question escalates; say why (#30).
                notes.add(0, io.github.vincenzofiore.rootology.decision.Note.of(
                        io.github.vincenzofiore.rootology.decision.Note.Kind.OUTSIDE_ITALY));
            }
            return new GraphFacts(readings, notes);
        }
        return new GraphFacts(NoisyFacts.apply(factSource.allFacts(), intent, noise));
    }

    @Action(description = "Decide the answer and its confidence from the raw facts")
    public Decision decide(Intent intent, GraphFacts facts) {
        return decisionLayer.decide(withDefaults(intent), facts);
    }

    @Action(cost = 1.0, description = "Settle the decision; in HUMAN mode an escalation waits for a person")
    public Resolution resolve(Decision decision, ReviewMode mode) {
        if (decision.escalate() && mode == ReviewMode.HUMAN) {
            // Pauses the agent: the platform puts the request on the blackboard and the process waits.
            throw new AwaitableResponseException(new TypeRequest<>(HumanAnswer.class,
                    "Escalated: " + decision.reason() + ". Proposed values: " + decision.answer()));
        }
        return Resolution.of(decision);
    }

    @Action(cost = 0.0, description = "Settle an escalated decision with the person's answer")
    public Resolution resolveWithHuman(Decision decision, HumanAnswer human) {
        return Resolution.byHuman(decision, human);
    }

    @AchievesGoal(description = "The question is answered, or escalated to a human")
    @Action(description = "Phrase the resolution as the pipeline's answer")
    public PipelineAnswer answer(Question question, Resolution resolution, GraphFacts facts, OperationContext context) {
        String prompt = resolution.answeredByHuman()
                ? HUMAN_PHRASE_PROMPT.formatted(question.text(), resolution.answer(), resolution.reason())
                : PHRASE_PROMPT.formatted(question.text(), resolution.answer(), resolution.escalated(),
                        resolution.reason());
        if (!facts.notes().isEmpty()) {
            // Only when present, so the suite's prompts stay exactly as recorded.
            prompt += NOTES_PROMPT.formatted(facts.notes().stream()
                    .map(io.github.vincenzofiore.rootology.decision.Note::english)
                    .collect(java.util.stream.Collectors.joining(" ")));
        }
        String text = phrase(prompt, resolution, facts, context);
        return new PipelineAnswer(question.id(), resolution.answer(), resolution.confidence(),
                resolution.escalated(), resolution.answeredByHuman(), resolution.reviewer(), text,
                sourcesOf(resolution.answer(), facts), facts.notes());
    }

    /** Claude's phrasing, or a plain sentence from the values when Claude is not available (#29). */
    private String phrase(String prompt, Resolution resolution, GraphFacts facts, OperationContext context) {
        if (claude.breaker.allowsCall()) {
            try {
                String text = context.ai().withDefaultLlm().generateText(prompt);
                claude.breaker.success();
                return text;
            } catch (RuntimeException e) {
                claude.breaker.failure();
                log.warn("Claude could not phrase the answer: {}", e.getMessage());
            }
        }
        return plainText(resolution, facts);
    }

    static String plainText(Resolution resolution, GraphFacts facts) {
        String main = resolution.escalated() ? "Not sure enough to answer: " + resolution.reason() + "."
                : resolution.answer().isEmpty() ? "No values found." : "Answer: " + String.join(", ", resolution.answer()) + ".";
        return String.join(" ", java.util.stream.Stream.concat(java.util.stream.Stream.of(main),
                facts.notes().stream().map(io.github.vincenzofiore.rootology.decision.Note::english)).toList());
    }

    /**
     * The sources of the facts that mention an answer value, in first-seen order, plus the weather
     * service whenever the forecast was used (it may have removed values rather than added them).
     */
    static List<String> sourcesOf(List<String> answer, GraphFacts facts) {
        return java.util.stream.Stream.concat(facts.readings().stream()
                                .filter(r -> answer.contains(r.fact().subject()) || answer.contains(r.fact().object()))
                                .flatMap(r -> r.sourceIds().stream()),
                        facts.readings().stream().flatMap(r -> r.sourceIds().stream())
                                .filter(Forecast.SOURCE::equals))
                .distinct()
                .toList();
    }
}
