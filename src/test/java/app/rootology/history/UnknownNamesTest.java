package app.rootology.history;

import app.rootology.history.RunHistory.UnknownName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The names asked and not found, for curators (#180): each once ignoring case, the most asked first. */
class UnknownNamesTest {

    private static final Instant MONDAY = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void eachNameOnceWithItsCountAndItsLastTimeTheMostAskedFirst() {
        assertThat(RunHistory.count(Stream.of(
                new UnknownName("papaya", 1, MONDAY),
                new UnknownName("Aglione", 1, MONDAY),
                new UnknownName("aglione", 1, TUESDAY))))
                .containsExactly(new UnknownName("Aglione", 2, TUESDAY), new UnknownName("papaya", 1, MONDAY));
    }
}
