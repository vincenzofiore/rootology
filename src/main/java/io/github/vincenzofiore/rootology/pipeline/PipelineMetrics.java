package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Domain metrics of the questions people ask (#27), with low-cardinality tags only: never a person, a space
 * or the question. {@code rootology.questions} times each question from start to answer or escalation,
 * tagged with the graph (scope) and the outcome (answered, escalated: waiting for a person, or failed), so
 * the escalation rate is escalated over answered plus escalated. {@code rootology.questions.confidence} is
 * the decision layer's confidence. Answers given later by a person are not questions and are not counted.
 */
@Component
class PipelineMetrics {

    private final MeterRegistry meters;

    PipelineMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    Timer.Sample start() {
        return Timer.start(meters);
    }

    void finished(Timer.Sample timing, FactScope scope, GardenPipeline.Run run) {
        boolean escalated = run.pending() != null || run.answer().escalated();
        stop(timing, scope, escalated ? "escalated" : "answered");
        Decision decision = run.pending() != null ? run.pending().decision() : null;
        double confidence = decision != null ? decision.confidence() : run.answer().confidence();
        DistributionSummary.builder("rootology.questions.confidence").description("Confidence of the decision layer")
                .tag("scope", tag(scope)).register(meters).record(confidence);
    }

    void failed(Timer.Sample timing, FactScope scope) {
        stop(timing, scope, "failed");
    }

    private void stop(Timer.Sample timing, FactScope scope, String outcome) {
        timing.stop(Timer.builder("rootology.questions").description("Questions asked, from start to answer or escalation")
                .tag("scope", tag(scope)).tag("outcome", outcome).register(meters));
    }

    private static String tag(FactScope scope) {
        return scope.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
