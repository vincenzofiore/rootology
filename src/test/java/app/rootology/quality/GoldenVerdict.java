package app.rootology.quality;

import app.rootology.experiments.GoldenQuestions;
import app.rootology.decision.Intent;
import app.rootology.decision.Note;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.PipelineAnswer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** How the pipeline did on one golden question (#20); SILENT_WRONG is the error the suite exists for. */
enum GoldenVerdict {
    /** Answered with exactly the expected values and notes. */
    CORRECT,
    /** Escalated where it had to, for the expected reason, with the expected notes. */
    CORRECT_ESCALATION,
    /** Answered, but with other values, or answered where it had to escalate. */
    SILENT_WRONG,
    /** Escalated where an answer was expected: safe, but a regression. */
    UNNECESSARY_ESCALATION,
    /** Escalated as expected, but for another reason (checked offline only: Jev words its reasons its own way). */
    WRONG_ESCALATION_REASON,
    /** The right values (or the right escalation), without an expected note. */
    MISSING_NOTE,
    /** Refused as off-domain where it had to be (#53). */
    CORRECT_REFUSAL;

    static GoldenVerdict of(GoldenQuestions.Case c, GardenPipeline.Run run) {
        return of(c, run, true);
    }

    /** checkReason: compare the escalation reason too (offline, where the stub's reasons are known). */
    static GoldenVerdict of(GoldenQuestions.Case c, GardenPipeline.Run run, boolean checkReason) {
        PipelineAnswer answer = run.answer();
        // The domain filter (#53): refusing an in-domain question is a silent error, and so is answering or
        // escalating an off-domain one (a reviewer's time is what the filter exists to save).
        if (c.offDomain()) {
            return run.offDomain() ? CORRECT_REFUSAL : SILENT_WRONG;
        }
        if (run.offDomain()) {
            return SILENT_WRONG;
        }
        if (c.answer() == null) {
            if (answer != null) {
                return SILENT_WRONG;
            }
            if (checkReason && !run.pending().decision().reason().contains(c.escalate())) {
                return WRONG_ESCALATION_REASON;
            }
            // An escalation's notes are checked too (#60): whoever answers sees them.
            return hasNotes(c, notesOf(run.pending().notes())) ? CORRECT_ESCALATION : MISSING_NOTE;
        }
        if (answer == null) {
            return UNNECESSARY_ESCALATION;
        }
        if (answer.answer().size() != c.answer().size()
                || !new HashSet<>(answer.answer()).equals(new HashSet<>(c.answer()))) {
            return SILENT_WRONG;
        }
        return hasNotes(c, notesOf(answer.notes())) ? CORRECT : MISSING_NOTE;
    }

    /** Every expected note, as English text, is part of one of the notes the run produced. */
    private static boolean hasNotes(GoldenQuestions.Case c, List<String> produced) {
        return c.notes().stream().allMatch(expected -> produced.stream().anyMatch(note -> note.contains(expected)));
    }

    private static List<String> notesOf(List<Note> notes) {
        return notes.stream().map(Note::english).toList();
    }

    /**
     * Whether Claude understood the question as expected: kind, subject (any case) and what the case sets.
     * The garden always counts (#57): naming a garden the question does not name changes the answer.
     */
    static boolean intentMatches(Intent expected, Intent understood) {
        if (understood == null || expected.kind() != understood.kind()) {
            return false;
        }
        return same(expected.subject(), understood.subject()) && same(expected.season(), understood.season())
                && (expected.month() == null || same(expected.month(), understood.month()))
                && (expected.zone() == null || same(expected.zone(), understood.zone()))
                && (expected.language() == null || same(expected.language(), understood.language()))
                && same(expected.garden(), understood.garden());
    }

    private static boolean same(String a, String b) {
        return Objects.equals(a == null ? null : a.trim().toLowerCase(Locale.ROOT),
                b == null ? null : b.trim().toLowerCase(Locale.ROOT));
    }
}
