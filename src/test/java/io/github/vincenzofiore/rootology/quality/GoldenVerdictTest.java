package io.github.vincenzofiore.rootology.quality;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Note;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.experiments.GoldenQuestions;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.Question;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The golden questions' verdict on an escalation checks its notes too (#60), as they do on an answer. */
class GoldenVerdictTest {

    /** An escalation expected with `notes` (parts of the note's English text). */
    private static GoldenQuestions.Case expected(List<String> notes) {
        return new GoldenQuestions.Case("case", "Cosa cresce nel mio orto?",
                new Intent(QueryKind.GROWING_NOW, null, null, "it", null, null), "none", "missing", List.of(),
                null, "needs a garden", notes, false);
    }

    private static GardenPipeline.Run escalated(List<Note> notes) {
        GardenPipeline.PendingReview review = new GardenPipeline.PendingReview("process", RunContext.NONE,
                new Question("case", "Cosa cresce nel mio orto?"),
                Decision.escalated("the question needs a garden"), null, notes);
        return new GardenPipeline.Run(null, review, null, false);
    }

    @Test
    void anEscalationWithTheExpectedNoteIsCorrect() {
        assertThat(GoldenVerdict.of(expected(List.of("inside one of the space's gardens")),
                escalated(List.of(Note.of(Note.Kind.NO_GARDEN_FOR_QUESTION)))))
                .isEqualTo(GoldenVerdict.CORRECT_ESCALATION);
    }

    /** An off-domain case: refused, with no values and no escalation expected (#53). */
    private static GoldenQuestions.Case offDomain() {
        return new GoldenQuestions.Case("off-1", "Come si fa il punto zig-zag a macchina?",
                new Intent(QueryKind.OTHER, null, null, "it", null, null), "none", "missing", List.of(),
                null, null, List.of(), true);
    }

    private static GardenPipeline.Run refused() {
        return new GardenPipeline.Run(new io.github.vincenzofiore.rootology.pipeline.PipelineAnswer("off-1",
                List.of(), 0.02, false, false, null, null, List.of(), List.of(), null), null, null, true);
    }

    @Test
    void aRefusalOfAnOffDomainQuestionIsCorrect() {
        assertThat(GoldenVerdict.of(offDomain(), refused())).isEqualTo(GoldenVerdict.CORRECT_REFUSAL);
    }

    @Test
    void answeringAnOffDomainQuestionIsASilentError() {
        GardenPipeline.Run answered = new GardenPipeline.Run(new io.github.vincenzofiore.rootology.pipeline.PipelineAnswer(
                "off-1", List.of("Basilico"), 1.0, false, false, null, "text", List.of(), List.of(), null), null, null, false);

        assertThat(GoldenVerdict.of(offDomain(), answered)).isEqualTo(GoldenVerdict.SILENT_WRONG);
    }

    @Test
    void escalatingAnOffDomainQuestionIsASilentError() {
        GardenPipeline.PendingReview review = new GardenPipeline.PendingReview("process", RunContext.NONE,
                new Question("off-1", "Come si fa il punto zig-zag a macchina?"),
                Decision.escalated("the question does not fit any supported shape"), null, List.of());
        GardenPipeline.Run escalated = new GardenPipeline.Run(null, review, null, false);

        assertThat(GoldenVerdict.of(offDomain(), escalated)).isEqualTo(GoldenVerdict.SILENT_WRONG);
    }

    @Test
    void refusingAnInDomainQuestionIsASilentError() {
        assertThat(GoldenVerdict.of(expected(List.of("inside one of the space's gardens")), refused()))
                .isEqualTo(GoldenVerdict.SILENT_WRONG);
    }

    @Test
    void anEscalationWithoutTheExpectedNoteIsMissingNote() {
        assertThat(GoldenVerdict.of(expected(List.of("inside one of the space's gardens")), escalated(List.of())))
                .isEqualTo(GoldenVerdict.MISSING_NOTE);
    }
}
