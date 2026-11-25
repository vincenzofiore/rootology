package app.rootology.history;

import app.rootology.decision.Decision;
import app.rootology.decision.Note;
import app.rootology.experiments.ExperimentCase;
import app.rootology.experiments.ExperimentRunner;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.NoiseProfile;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.PipelineRunFinished;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.spaces.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores every finished run with the arbiter's answer, when the question is an experiment case. */
@Service
public class RunHistory {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(RunHistory.class);

    private final PipelineRunRepository repository;
    private final ExperimentRunner runner;
    private final SpaceService spaces;

    RunHistory(PipelineRunRepository repository, ExperimentRunner runner, SpaceService spaces) {
        this.repository = repository;
        this.runner = runner;
        this.spaces = spaces;
    }

    @EventListener
    @Transactional
    void on(PipelineRunFinished run) {
        Decision decision = run.decision();
        PipelineAnswer answer = run.answer();
        NoiseProfile noise = run.noise();
        List<String> jena = experimentCase(run.question().id()).map(runner::jenaAnswer).orElse(null);
        Boolean matches = jena == null ? null : new HashSet<>(jena).equals(new HashSet<>(answer.answer()));
        repository.save(new PipelineRunRecord(run.processId(), run.context().spaceId(),
                run.context().hasGarden() ? run.context().gardenId() : null, run.context().startedBy(),
                run.question().id(), run.question().text(),
                noise.dropout(), noise.spurious(), noise.mislabel(), noise.nearSubject(), noise.seed(),
                decision.answer(), decision.confidence(), decision.escalate(), decision.reason(),
                answer.answer(), answer.answeredByHuman(), answer.reviewer(), answer.text(),
                answer.context() == null ? null : JSON.writeValueAsString(answer.context()),
                answer.notes() == null ? null : JSON.writeValueAsString(answer.notes()),
                answer.sources() == null ? null : List.copyOf(answer.sources()),
                answer.activities().isEmpty() ? null : JSON.writeValueAsString(answer.activities()),
                run.offDomain(), run.domainUnjudged(), jena, matches, run.finishedAt()));
    }

    /**
     * The last questions of one person that the domain filter judged (#165), newest first: a system read, with
     * no space and no role, for the nightly signal that flags an account creating only useless traffic. A run
     * where the filter could not judge (#161) is left out, and so is one answered from a waiting question: only
     * a question the filter saw on its own says what the person asks.
     */
    @Transactional(readOnly = true)
    public JudgedQuestions judgedQuestions(String login, int limit) {
        if (limit < 1) {
            return new JudgedQuestions(0, 0);
        }
        Pageable newest = PageRequest.of(0, limit);
        List<PipelineRunRecord> runs = repository.judgedQuestions(login, newest);
        return new JudgedQuestions(runs.size(), (int) runs.stream().filter(PipelineRunRecord::isOffDomain).count());
    }

    /** How many of a person's last questions the filter judged, and how many of those were off-domain (#165). */
    public record JudgedQuestions(int judged, int offDomain) {
    }

    /**
     * The most recent runs of one garden (#56), newest first, or the ones without a garden when gardenId is 0;
     * only the space's members may read them (#41).
     */
    @Transactional(readOnly = true)
    public List<PipelineRunRecord> recent(long spaceId, long gardenId, String login) {
        if (!spaces.isMember(spaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + spaceId);
        }
        return gardenId > 0
                ? repository.findTop100BySpaceIdAndGardenIdOrderByFinishedAtDesc(spaceId, gardenId)
                : repository.findTop100BySpaceIdAndGardenIdIsNullOrderByFinishedAtDesc(spaceId);
    }

    /** The context the run was answered in (#55); empty for a row stored before it, or for a run without a space. */
    public Optional<ContextSnapshot> contextOf(PipelineRunRecord run) {
        return run.getContextJson() == null ? Optional.empty()
                : Optional.of(JSON.readValue(run.getContextJson(), ContextSnapshot.class));
    }

    /**
     * The answer of a past run as the pipeline gave it (#110), so it shows the same card as a fresh one: values,
     * confidence, notes, sources and context. A row stored before #110 has no notes and no sources, and notes
     * that can no longer be read (a kind renamed since) are left out rather than failing the page. The
     * activities of a sowing or planting answer come back too (#112), none for a row stored before.
     */
    public PipelineAnswer answerOf(PipelineRunRecord run) {
        return new PipelineAnswer(run.getQuestionId(), run.getAnswer() == null ? List.of() : run.getAnswer(),
                run.getDecisionConfidence(), run.isDecisionEscalated(), run.isAnsweredByHuman(), run.getReviewer(),
                run.getAnswerText() == null ? "" : run.getAnswerText(),
                run.getSources() == null ? List.of() : run.getSources(), notesOf(run), contextOf(run).orElse(null),
                activitiesOf(run));
    }

    private static Map<String, List<String>> activitiesOf(PipelineRunRecord run) {
        if (run.getActivitiesJson() == null) {
            return Map.of();
        }
        try {
            return JSON.readValue(run.getActivitiesJson(), new tools.jackson.core.type.TypeReference<
                    java.util.LinkedHashMap<String, List<String>>>() { });
        } catch (RuntimeException e) {
            log.warn("Stored activities of run {} could not be read: {}", run.getId(), e.getClass().getSimpleName());
            return Map.of();
        }
    }

    /**
     * The crops and organisms asked about since then and missing from the knowledge base (#180), for curators:
     * each name once, ignoring case, with how many questions named it and the last time, the most asked first.
     * A system read across spaces: only the names leave it, never a question, a person or a space.
     */
    @Transactional(readOnly = true)
    public List<UnknownName> unknownNames(java.time.Instant since) {
        return count(repository.withUnknownSubject(since).stream().flatMap(run -> notesOf(run).stream()
                .filter(n -> n.kind() == Note.Kind.UNKNOWN_SUBJECT)
                .map(n -> new UnknownName(n.arg(0), 1, run.getFinishedAt()))));
    }

    /** Each name once, ignoring case and keeping its first spelling, the most asked first, then the latest. */
    static List<UnknownName> count(java.util.stream.Stream<UnknownName> asked) {
        Map<String, UnknownName> byName = new java.util.LinkedHashMap<>();
        asked.forEach(one -> byName.merge(one.name().toLowerCase(java.util.Locale.ROOT), one,
                (seen, again) -> new UnknownName(seen.name(), seen.questions() + again.questions(),
                        seen.last().isAfter(again.last()) ? seen.last() : again.last())));
        return byName.values().stream()
                .sorted(java.util.Comparator.comparingInt(UnknownName::questions).reversed()
                        .thenComparing(UnknownName::last, java.util.Comparator.reverseOrder()))
                .toList();
    }

    /** A name asked and not found (#180), as first written, with its count and the last time it was asked. */
    public record UnknownName(String name, int questions, java.time.Instant last) {
    }

    private static List<Note> notesOf(PipelineRunRecord run) {
        if (run.getNotesJson() == null) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(run.getNotesJson(), Note[].class));
        } catch (RuntimeException e) {
            log.warn("Stored notes of run {} could not be read: {}", run.getId(), e.getClass().getSimpleName());
            return List.of();
        }
    }

    /**
     * Every question of a space, in any garden and without one (#109), newest first; only the space's members
     * may read them (#41).
     */
    @Transactional(readOnly = true)
    public List<PipelineRunRecord> recentInSpace(long spaceId, String login) {
        if (!spaces.isMember(spaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + spaceId);
        }
        return repository.findTop100BySpaceIdOrderByFinishedAtDesc(spaceId);
    }

    private static Optional<ExperimentCase> experimentCase(String questionId) {
        return Arrays.stream(ExperimentCase.values()).filter(c -> c.id().equals(questionId)).findFirst();
    }
}
