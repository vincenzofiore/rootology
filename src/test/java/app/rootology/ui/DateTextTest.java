package app.rootology.ui;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** Dates as a person reads them (#79), in the page's language. */
class DateTextTest {

    private final Translations translations = new Translations();
    private final NoteText.Texts italian = (key, args) -> translations.getTranslation(key, Locale.ITALIAN, args);
    private final NoteText.Texts english = (key, args) -> translations.getTranslation(key, Locale.ENGLISH, args);
    private final LocalDate today = LocalDate.of(2026, 5, 20);

    @Test
    void aShortDateIsLocalizedAndTellsTheYearOnlyWhenItIsNotTheCurrentOne() {
        assertThat(DateText.date(LocalDate.of(2026, 5, 3), today, Locale.ITALIAN)).isEqualTo("3 mag");
        assertThat(DateText.date(LocalDate.of(2026, 5, 3), today, Locale.ENGLISH)).isEqualTo("3 May");
        assertThat(DateText.date(LocalDate.of(2025, 5, 3), today, Locale.ITALIAN)).isEqualTo("3 mag 2025");
    }

    @Test
    void aDateCloseToTodayIsSaidInWords() {
        assertThat(DateText.relative(today, today, italian)).isEqualTo("oggi");
        assertThat(DateText.relative(today.minusDays(1), today, italian)).isEqualTo("ieri");
        assertThat(DateText.relative(LocalDate.of(2026, 5, 8), today, italian)).isEqualTo("12 giorni fa");
        assertThat(DateText.relative(today.plusDays(3), today, english)).isEqualTo("in 3 days");
    }

    @Test
    void aTimeIsTheLocalShortDateWithTheHour() {
        Instant at = LocalDate.of(2026, 5, 3).atTime(14, 5).atZone(DateText.ITALY).toInstant();

        assertThat(DateText.dateTime(at, today, DateText.ITALY, Locale.ITALIAN)).isEqualTo("3 mag, 14:05");
        assertThat(DateText.fullDateTime(at, DateText.ITALY, Locale.ITALIAN)).isEqualTo("3 maggio 2026, 14:05");
    }

    @Test
    void aGardenDateIsShownInTheGardenZoneAndAnItalianOneInItaly() {
        // 01:30 on 4 May in Rome, still 19:30 on 3 May in New York.
        Instant at = Instant.parse("2026-05-03T23:30:00Z");
        Div page = new Div();

        assertThat(DateText.dateTime(at, Clock.fixed(at, DateText.ITALY), page)).isEqualTo("4 mag, 01:30");
        assertThat(DateText.dateTime(at, Clock.fixed(at, ZoneId.of("America/New_York")), page))
                .as("the garden's own day and hour (#158)").isEqualTo("3 mag, 19:30");
    }

    @Test
    void theFullDateIsSpelledOut() {
        assertThat(DateText.fullDate(LocalDate.of(2026, 5, 3), Locale.ITALIAN)).isEqualTo("3 maggio 2026");
        assertThat(DateText.fullDate(LocalDate.of(2026, 5, 3), Locale.ENGLISH)).isEqualTo("3 May 2026");
    }
}
