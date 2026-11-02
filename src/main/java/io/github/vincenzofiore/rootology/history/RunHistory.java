package io.github.vincenzofiore.rootology.history;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.experiments.ExperimentCase;
import io.github.vincenzofiore.rootology.experiments.ExperimentRunner;
import io.github.vincenzofiore.rootology.pipeline.NoiseProfile;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.PipelineRunFinished;
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

    RunHistory(PipelineRunRepository repository, ExperimentRunner runner) {
        this.repository = repository;
        this.runner = runner;
    }

    @EventListener
    @Transactional
    void on(PipelineRunFinished run) {
        Decision decision = run.decision();
        PipelineAnswer answer = run.answer();
        NoiseProfile noise = run.noise();
        List<String> jena = experimentCase(run.question().id()).map(runner::jenaAnswer).orElse(null);
        Boolean matches = jena == null ? null : new HashSet<>(jena).equals(new HashSet<>(answer.answer()));
        repository.save(new PipelineRunRecord(run.processId(), run.question().id(), run.question().text(),
                noise.dropout(), noise.spurious(), noise.mislabel(), noise.nearSubject(), noise.seed(),
                decision.answer(), decision.confidence(), decision.escalate(), decision.reason(),
                answer.answer(), answer.answeredByHuman(), answer.reviewer(), answer.text(),
                jena, matches, run.finishedAt()));
    }

    /** The most recent runs, newest first. */
    @Transactional(readOnly = true)
    public List<PipelineRunRecord> recent() {
        return repository.findTop100ByOrderByFinishedAtDesc();
    }

    private static Optional<ExperimentCase> experimentCase(String questionId) {
        return Arrays.stream(ExperimentCase.values()).filter(c -> c.id().equals(questionId)).findFirst();
    }
}
