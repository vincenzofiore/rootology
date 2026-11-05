package io.github.vincenzofiore.rootology.experiments;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The QA view's data: the golden questions and the latest recorded live run (#20). */
class GoldenRunsTest {

    private final GoldenRuns runs = new GoldenRuns();

    @Test
    void readsTheGoldenQuestionsAndTheLatestLiveRun() {
        assertThat(runs.golden().cases()).hasSizeGreaterThanOrEqualTo(25)
                .extracting(GoldenQuestions.Case::id).doesNotHaveDuplicates();

        GoldenRuns.LiveRun latest = runs.latest().orElseThrow();

        assertThat(latest.file()).startsWith("golden-").endsWith(".json");
        assertThat(latest.verdicts()).containsKey("CORRECT").doesNotContainKey("SILENT_WRONG");
        assertThat(latest.results()).containsKey("companions-tomato-it");
        assertThat(latest.results().get("companions-tomato-it").answer())
                .containsExactlyInAnyOrder("Basilico", "Cipolla", "Prezzemolo", "Carota");
    }
}
