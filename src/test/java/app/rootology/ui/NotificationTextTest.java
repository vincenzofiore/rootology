package app.rootology.ui;

import app.rootology.notifications.NotificationView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The details of a notification in the person’s language (#94), and the rows stored before. */
class NotificationTextTest {

    private final Translations translations = new Translations();
    private final NoteText.Texts italian = (key, args) -> translations.getTranslation(key, Locale.ITALIAN, args);
    private final NoteText.Texts english = (key, args) -> translations.getTranslation(key, Locale.ENGLISH, args);

    private static NotificationView notification(String kind, String processId, String title, String body,
                                                 String resolvedBy, String askedBy) {
        Instant now = Instant.parse("2026-11-12T09:00:00Z");
        return new NotificationView(1, 1, 2, kind, processId, title, body, now, null, null, resolvedBy, null, askedBy);
    }

    @Test
    void aWaitingQuestionSaysWhoAskedAndTheQuestion() {
        NotificationView waiting = notification("ESCALATION_WAITING", "p-1", "A question waits for an answer",
                "Quali sono le compagne della lavanda?", null, "mia");

        assertThat(NotificationText.details(waiting, italian))
                .isEqualTo("mia ha chiesto: Quali sono le compagne della lavanda?");
        assertThat(NotificationText.details(waiting, english))
                .isEqualTo("mia asked: Quali sono le compagne della lavanda?");
    }

    @Test
    void anAnswerSaysWhoAnswered() {
        NotificationView answered = notification("ESCALATION_ANSWERED", "p-1", "Your question has an answer",
                null, "owen", null);

        assertThat(NotificationText.details(answered, italian)).isEqualTo("owen ha risposto alla tua domanda");
        assertThat(NotificationText.details(answered, english)).isEqualTo("owen answered your question");
    }

    @Test
    void aWeatherAlertKeepsItsOwnTextWhichIsAlreadyInItalian() {
        NotificationView alert = notification("WEATHER_ALERT", null, "Rischio gelo", "Minima prevista -2 °C",
                null, null);

        assertThat(NotificationText.details(alert, italian)).isEqualTo("Minima prevista -2 °C");
        assertThat(NotificationText.details(alert, english)).as("written by the alert, not translated here")
                .isEqualTo("Minima prevista -2 °C");
    }

    @Test
    void theRowsStoredBeforeShowTheSentenceTheyKept() {
        NotificationView old = notification("ESCALATION_WAITING", "p-1", "A question waits for an answer",
                "mia asked: Quali sono le compagne della lavanda? (lavanda is not in the dataset)", null, null);
        NotificationView oldAnswer = notification("ESCALATION_ANSWERED", "p-2", "Your question has an answer",
                "owen answered your question", "owen", null);

        assertThat(NotificationText.details(old, italian)).isEqualTo(old.body());
        assertThat(NotificationText.details(oldAnswer, italian)).isEqualTo("owen answered your question");
    }
}
