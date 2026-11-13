package app.rootology.pipeline;

import app.rootology.decision.Decision;

import java.time.Instant;

/**
 * Published when a run started with {@link GardenPipeline#start} finishes, including after a
 * person answered an escalation. The history module stores it; the pipeline does not know about it.
 * offDomain marks a question the domain filter refused (#53), answered with a fixed text and no run.
 */
public record PipelineRunFinished(String processId, RunContext context, Question question, NoiseProfile noise,
                                  Decision decision, PipelineAnswer answer, boolean offDomain, Instant finishedAt) {
}
