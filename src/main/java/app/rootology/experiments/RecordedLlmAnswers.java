package app.rootology.experiments;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "Claude alone" answers recorded by hand, for display. Copied from ExperimentRecordingTest,
 * which stays the record: change them there first. Only the cases recorded so far are present.
 */
public final class RecordedLlmAnswers {

    private static final Map<ExperimentCase, List<String>> WITH_SKILL = Map.of(
            ExperimentCase.HARD_1, List.of("Basil"),
            ExperimentCase.HARD_2, List.of());

    private static final Map<ExperimentCase, List<String>> WITHOUT_SKILL = Map.of(
            ExperimentCase.HARD_2, List.of("SPRING", "SUMMER", "AUTUMN"));

    public static Optional<List<String>> withSkill(ExperimentCase experimentCase) {
        return Optional.ofNullable(WITH_SKILL.get(experimentCase));
    }

    public static Optional<List<String>> withoutSkill(ExperimentCase experimentCase) {
        return Optional.ofNullable(WITHOUT_SKILL.get(experimentCase));
    }

    private RecordedLlmAnswers() {
    }
}
