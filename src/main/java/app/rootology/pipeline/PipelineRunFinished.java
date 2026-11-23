package app.rootology.pipeline;

import app.rootology.decision.Decision;

import java.time.Instant;

/**
 * Published when a run started with {@link GardenPipeline#start} finishes, including after a
 * person answered an escalation. The history module stores it; the pipeline does not know about it.
 * offDomain marks a question the domain filter refused (#53), answered with a fixed text and no run;
 * domainUnjudged marks one the filter could not judge and let through (#161), so the nightly signal of #165
 * can leave it out.
 */
public record PipelineRunFinished(String processId, RunContext context, Question question, NoiseProfile noise,
                                  Decision decision, PipelineAnswer answer, boolean offDomain,
                                  boolean domainUnjudged, Instant finishedAt) {
}
