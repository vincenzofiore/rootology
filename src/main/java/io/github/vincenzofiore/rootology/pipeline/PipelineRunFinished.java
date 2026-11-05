package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Decision;

import java.time.Instant;

/**
 * Published when a run started with {@link GardenPipeline#start} finishes, including after a
 * person answered an escalation. The history module stores it; the pipeline does not know about it.
 */
public record PipelineRunFinished(String processId, RunContext context, Question question, NoiseProfile noise,
                                  Decision decision, PipelineAnswer answer, Instant finishedAt) {
}
