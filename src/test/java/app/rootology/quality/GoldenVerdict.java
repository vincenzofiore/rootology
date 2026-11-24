package app.rootology.quality;

import app.rootology.experiments.GoldenQuestions;
import app.rootology.decision.Intent;
import app.rootology.decision.Note;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.QuestionContext;

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
    CORRECT_REFUSAL,
    /** Asked the person to choose the place or garden the case expects (#107). */
    CORRECT_CHOICE,
    /**
     * An off-domain question handed to a person because the domain filter could not judge it (Jev
     * unreachable, #161): the safe failure of an outage, not an error of the pipeline.
     */
    FILTER_UNAVAILABLE;

    static GoldenVerdict of(GoldenQuestions.Case c, GardenPipeline.Run run) {
        return of(c, run, true);
    }

    /** checkReason: compare the escalation reason too (offline, where the stub's reasons are known). */
    static GoldenVerdict of(GoldenQuestions.Case c, GardenPipeline.Run run, boolean checkReason) {
        PipelineAnswer answer = run.answer();
        // The domain filter (#53): refusing an in-domain question is a silent error, and so is answering or
        // escalating an off-domain one (a reviewer's time is what the filter exists to save).
        if (c.offDomain()) {
            if (run.offDomain()) {
                return CORRECT_REFUSAL;
            }
            // Escalated unscreened during an outage is safe; answered, or escalated with the filter working, is not.
            return run.domainUnjudged() && run.answer() == null && run.pending() != null
                    ? FILTER_UNAVAILABLE : SILENT_WRONG;
        }
        if (run.offDomain()) {
            return SILENT_WRONG;
        }
        if (c.choice() != null) {
            // A choice is the whole run (#107): the right kind, and the options the case names.
            if (run.choice() == null) {
                return SILENT_WRONG;
            }
            boolean kind = c.choice().kind().equalsIgnoreCase(run.choice().kind().name());
            return kind && hasOptions(c.choice().options(), optionsOf(run.choice())) ? CORRECT_CHOICE : SILENT_WRONG;
        }
        if (run.choice() != null) {
            // The run asked for a choice where an answer or an escalation was expected: a regression, no error.
            return UNNECESSARY_ESCALATION;
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
        if (!c.activities().isEmpty() && !sameActivities(c.activities(), answer.activities())) {
            // A seedbed month told as a month to plant out is the error #112 is about: as wrong as a wrong value.
            return SILENT_WRONG;
        }
        return hasNotes(c, notesOf(answer.notes())) ? CORRECT : MISSING_NOTE;
    }

    /** The same values with the same activities, ignoring the order of each value's activities (#112). */
    private static boolean sameActivities(java.util.Map<String, List<String>> expected,
                                          java.util.Map<String, List<String>> produced) {
        return expected.keySet().equals(produced.keySet()) && expected.entrySet().stream()
                .allMatch(e -> new HashSet<>(e.getValue()).equals(new HashSet<>(produced.get(e.getKey()))));
    }

    /** The places' labels or the gardens' names the choice offers. */
    private static List<String> optionsOf(QuestionContext.Choice choice) {
        return choice.kind() == QuestionContext.Choice.Kind.PLACE
                ? choice.places().stream().map(QuestionContext.Choice.Place::label).toList()
                : choice.gardens().stream().map(QuestionContext.Choice.Garden::name).toList();
    }

    /** Every expected option is one of the choice's, ignoring case. */
    private static boolean hasOptions(List<String> expected, List<String> produced) {
        List<String> offered = produced.stream().map(o -> o.toLowerCase(Locale.ROOT)).toList();
        return expected.stream().allMatch(e -> offered.contains(e.toLowerCase(Locale.ROOT)));
    }

    /** Every expected note, as English text and ignoring case, is part of one of the notes the run produced (#159). */
    private static boolean hasNotes(GoldenQuestions.Case c, List<String> produced) {
        return c.notes().stream().allMatch(expected -> produced.stream().anyMatch(note ->
                note.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))));
    }

    private static List<String> notesOf(List<Note> notes) {
        return notes.stream().map(Note::english).toList();
    }

    /**
     * Whether Claude understood the question as expected: kind, subject (any case) and what the case sets.
     * The garden always counts (#57), and so does the place (#107): naming one the question does not changes
     * the answer.
     */
    static boolean intentMatches(Intent expected, Intent understood) {
        if (understood == null || expected.kind() != understood.kind()) {
            return false;
        }
        return same(expected.subject(), understood.subject()) && same(expected.season(), understood.season())
                && (expected.month() == null || same(expected.month(), understood.month()))
                && (expected.zone() == null || same(expected.zone(), understood.zone()))
                && (expected.language() == null || same(expected.language(), understood.language()))
                && same(expected.garden(), understood.garden()) && same(expected.place(), understood.place());
    }

    private static boolean same(String a, String b) {
        return Objects.equals(a == null ? null : a.trim().toLowerCase(Locale.ROOT),
                b == null ? null : b.trim().toLowerCase(Locale.ROOT));
    }
}
