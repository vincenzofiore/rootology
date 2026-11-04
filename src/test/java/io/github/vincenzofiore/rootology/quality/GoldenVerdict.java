package io.github.vincenzofiore.rootology.quality;

import io.github.vincenzofiore.rootology.experiments.GoldenQuestions;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.pipeline.GardenPipeline;
import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

/** How the pipeline did on one golden question (#20); SILENT_WRONG is the error the suite exists for. */
enum GoldenVerdict {
    /** Answered with exactly the expected values and notes. */
    CORRECT,
    /** Escalated where it had to, for the expected reason. */
    CORRECT_ESCALATION,
    /** Answered, but with other values, or answered where it had to escalate. */
    SILENT_WRONG,
    /** Escalated where an answer was expected: safe, but a regression. */
    UNNECESSARY_ESCALATION,
    /** Escalated as expected, but for another reason (checked offline only: Jev words its reasons its own way). */
    WRONG_ESCALATION_REASON,
    /** The right values, without an expected note. */
    MISSING_NOTE;

    static GoldenVerdict of(GoldenQuestions.Case c, GardenPipeline.Run run) {
        return of(c, run, true);
    }

    /** checkReason: compare the escalation reason too (offline, where the stub's reasons are known). */
    static GoldenVerdict of(GoldenQuestions.Case c, GardenPipeline.Run run, boolean checkReason) {
        PipelineAnswer answer = run.answer();
        if (c.answer() == null) {
            if (answer != null) {
                return SILENT_WRONG;
            }
            return !checkReason || run.pending().decision().reason().contains(c.escalate())
                    ? CORRECT_ESCALATION : WRONG_ESCALATION_REASON;
        }
        if (answer == null) {
            return UNNECESSARY_ESCALATION;
        }
        if (answer.answer().size() != c.answer().size()
                || !new HashSet<>(answer.answer()).equals(new HashSet<>(c.answer()))) {
            return SILENT_WRONG;
        }
        boolean notes = c.notes().stream().allMatch(note -> answer.notes().stream().anyMatch(n -> n.english().contains(note)));
        return notes ? CORRECT : MISSING_NOTE;
    }

    /** Whether Claude understood the question as expected: kind, subject (any case) and what the case sets. */
    static boolean intentMatches(Intent expected, Intent understood) {
        if (understood == null || expected.kind() != understood.kind()) {
            return false;
        }
        return same(expected.subject(), understood.subject()) && same(expected.season(), understood.season())
                && (expected.month() == null || same(expected.month(), understood.month()))
                && (expected.zone() == null || same(expected.zone(), understood.zone()))
                && (expected.language() == null || same(expected.language(), understood.language()));
    }

    private static boolean same(String a, String b) {
        return Objects.equals(a == null ? null : a.trim().toLowerCase(Locale.ROOT),
                b == null ? null : b.trim().toLowerCase(Locale.ROOT));
    }
}
