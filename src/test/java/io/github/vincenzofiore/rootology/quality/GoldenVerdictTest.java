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
                null, "needs a garden", notes);
    }

    private static GardenPipeline.Run escalated(List<Note> notes) {
        GardenPipeline.PendingReview review = new GardenPipeline.PendingReview("process", RunContext.NONE,
                new Question("case", "Cosa cresce nel mio orto?"),
                Decision.escalated("the question needs a garden"), null, notes);
        return new GardenPipeline.Run(null, review, null);
    }

    @Test
    void anEscalationWithTheExpectedNoteIsCorrect() {
        assertThat(GoldenVerdict.of(expected(List.of("inside one of the space's gardens")),
                escalated(List.of(Note.of(Note.Kind.NO_GARDEN_FOR_QUESTION)))))
                .isEqualTo(GoldenVerdict.CORRECT_ESCALATION);
    }

    @Test
    void anEscalationWithoutTheExpectedNoteIsMissingNote() {
        assertThat(GoldenVerdict.of(expected(List.of("inside one of the space's gardens")), escalated(List.of())))
                .isEqualTo(GoldenVerdict.MISSING_NOTE);
    }
}
