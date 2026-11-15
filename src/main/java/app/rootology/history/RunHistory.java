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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
                run.offDomain(), jena, matches, run.finishedAt()));
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
     * that can no longer be read (a kind renamed since) are left out rather than failing the page.
     */
    public PipelineAnswer answerOf(PipelineRunRecord run) {
        return new PipelineAnswer(run.getQuestionId(), run.getAnswer() == null ? List.of() : run.getAnswer(),
                run.getDecisionConfidence(), run.isDecisionEscalated(), run.isAnsweredByHuman(), run.getReviewer(),
                run.getAnswerText() == null ? "" : run.getAnswerText(),
                run.getSources() == null ? List.of() : run.getSources(), notesOf(run), contextOf(run).orElse(null));
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
