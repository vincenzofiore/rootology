package app.rootology.history;

import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.Decision;
import app.rootology.decision.Note;
import app.rootology.pipeline.NoiseProfile;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.PipelineRunFinished;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A past answer is read back as the pipeline gave it (#110): its notes and sources too, so the history shows
 * the same card as a fresh answer. Rows stored before have none, and notes that can no longer be read are
 * left out instead of failing the page.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StoredAnswerTest {

    @Autowired
    RunHistory history;

    @Autowired
    SpaceService spaces;

    @Autowired
    JdbcClient jdbc;

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return app.rootology.TestSpaces.personal(spaces, login);
    }

    @Test
    void theNotesAndSourcesOfAnAnswerComeBackWithIt() {
        long space = spaceOf("stored-answer");
        List<Note> notes = List.of(new Note(Note.Kind.FROST_UNKNOWN, List.of("basil")),
                new Note(Note.Kind.FORECAST_MINIMUM, List.of("3.5", "2026-11-16", "")));
        PipelineAnswer given = new PipelineAnswer("ask", List.of("tomato"), 0.82, false, false, null,
                "Oggi sì: pomodoro.", List.of("rootology-editorial", "agera5"), notes, null);
        history.on(new PipelineRunFinished("stored-answer-1", new RunContext(space, 0, "stored-answer"),
                new Question("ask", "Cosa semino?"), NoiseProfile.NONE,
                new Decision(List.of("tomato"), 0.82, false, "certain"), given, false, Instant.now()));

        PipelineAnswer read = history.answerOf(history.recent(space, 0, "stored-answer").getFirst());

        assertThat(read.notes()).isEqualTo(notes);
        assertThat(read.sources()).containsExactly("rootology-editorial", "agera5");
        assertThat(read.answer()).containsExactly("tomato");
        assertThat(read.text()).isEqualTo("Oggi sì: pomodoro.");
        assertThat(read.confidence()).isEqualTo(0.82);
    }

    @Test
    void aRowStoredBeforeHasNoNotesAndNoSources() {
        long space = spaceOf("stored-answer-old");
        store("stored-answer-2", space, "stored-answer-old", null);

        PipelineAnswer read = history.answerOf(history.recent(space, 0, "stored-answer-old").getFirst());

        assertThat(read.notes()).isEmpty();
        assertThat(read.sources()).isEmpty();
        assertThat(read.text()).isEqualTo("an answer");
    }

    @Test
    void notesThatCanNoLongerBeReadAreLeftOut() {
        long space = spaceOf("stored-answer-renamed");
        store("stored-answer-3", space, "stored-answer-renamed", "[{\"kind\":\"A_KIND_RENAMED_SINCE\",\"args\":[]}]");

        PipelineAnswer read = history.answerOf(history.recent(space, 0, "stored-answer-renamed").getFirst());

        assertThat(read.notes()).isEmpty();
    }

    private void store(String processId, long space, String login, String notesJson) {
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, "
                        + "answer, answered_by_human, answer_text, notes_json, finished_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, 0, false, 0, '{}', 1, false, 'certain', '{}', false, "
                        + "'an answer', ?, now())")
                .params(processId, space, login, "ask", "Cosa cresce?", notesJson)
                .update();
    }
}
