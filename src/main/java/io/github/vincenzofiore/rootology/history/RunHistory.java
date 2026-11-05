package io.github.vincenzofiore.rootology.history;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.experiments.ExperimentCase;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.pipeline.NoiseProfile;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.PipelineRunFinished;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Stores every finished run with the arbiter's answer, when the question is an experiment case. */
@Service
public class RunHistory {

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
        repository.save(new PipelineRunRecord(run.processId(), run.context().spaceId(), run.context().startedBy(),
                run.question().id(), run.question().text(),
                noise.dropout(), noise.spurious(), noise.mislabel(), noise.nearSubject(), noise.seed(),
                decision.answer(), decision.confidence(), decision.escalate(), decision.reason(),
                answer.answer(), answer.answeredByHuman(), answer.reviewer(), answer.text(),
                jena, matches, run.finishedAt()));
    }

    /** The most recent runs of one space, newest first; only its members may read them (#41). */
    @Transactional(readOnly = true)
    public List<PipelineRunRecord> recent(long spaceId, String login) {
        if (!spaces.isMember(spaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + spaceId);
        }
        return repository.findTop100BySpaceIdOrderByFinishedAtDesc(spaceId);
    }

    private static Optional<ExperimentCase> experimentCase(String questionId) {
        return Arrays.stream(ExperimentCase.values()).filter(c -> c.id().equals(questionId)).findFirst();
    }
}
