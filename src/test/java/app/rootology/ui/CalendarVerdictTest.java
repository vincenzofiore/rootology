package app.rootology.ui;

import com.vaadin.flow.component.html.Div;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.ContextSnapshot.MonthFrom;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verdict of a calendar answer (#116): worked out from its months and the month it was asked in, never
 * from the sentence, and only for an answer whose values are all months.
 */
class CalendarVerdictTest {

    private static final Instant NOVEMBER_15 = Instant.parse("2026-11-15T15:31:00Z");
    private static final List<String> TOMATO = List.of("FEBRUARY", "MARCH", "APRIL", "MAY");
    private static final Map<String, List<String>> TOMATO_WAYS = Map.of("FEBRUARY", List.of("SOW_SEEDBED"),
            "MARCH", List.of("SOW_SEEDBED"), "APRIL", List.of("TRANSPLANT"), "MAY", List.of("TRANSPLANT"));

    @Test
    void inNovemberATomatoIsNotNowButFromFebruaryInASeedbed() {
        CalendarVerdict.Verdict verdict = CalendarVerdict.of(TOMATO, TOMATO_WAYS, asked("NOVEMBER", MonthFrom.DEFAULT),
                NOVEMBER_15, new Div()).orElseThrow();

        assertThat(verdict.now()).isFalse();
        assertThat(verdict.month()).isEqualTo(Month.FEBRUARY);
        assertThat(verdict.words()).isEqualTo("!{verdict.now.no.doing}!");
    }

    @Test
    void inAprilItIsNowAndSaysTheTransplant() {
        CalendarVerdict.Verdict verdict = CalendarVerdict.of(TOMATO, TOMATO_WAYS, asked("APRIL", MonthFrom.DEFAULT),
                NOVEMBER_15, new Div()).orElseThrow();

        assertThat(verdict.now()).isTrue();
        assertThat(verdict.month()).isEqualTo(Month.APRIL);
        assertThat(verdict.words()).isEqualTo("!{verdict.now.yes.doing}!");
    }

    @Test
    void theNextMonthWrapsAroundTheYearAndAHarvestHasNoActivity() {
        CalendarVerdict.Verdict verdict = CalendarVerdict.of(List.of("JANUARY", "OCTOBER"), Map.of(),
                asked("NOVEMBER", MonthFrom.DEFAULT), NOVEMBER_15, new Div()).orElseThrow();

        assertThat(verdict.month()).isEqualTo(Month.JANUARY);
        assertThat(verdict.words()).isEqualTo("!{verdict.now.no}!");
    }

    @Test
    void aCalendarQuestionWithNoMonthInItsContextUsesTheMonthItWasAskedIn() {
        // The calendar questions store no month (#116): the row's date says November.
        CalendarVerdict.Verdict verdict = CalendarVerdict.of(TOMATO, TOMATO_WAYS, asked(null, null), NOVEMBER_15,
                new Div()).orElseThrow();
        CalendarVerdict.Verdict noContext = CalendarVerdict.of(TOMATO, TOMATO_WAYS, null, NOVEMBER_15, new Div())
                .orElseThrow();

        assertThat(verdict.month()).isEqualTo(Month.FEBRUARY);
        assertThat(noContext.words()).isEqualTo("!{verdict.now.no.doing}!");
    }

    @Test
    void aMonthTheQuestionNamedIsSaidAsThatMonth() {
        CalendarVerdict.Verdict verdict = CalendarVerdict.of(TOMATO, Map.of(), asked("JUNE", MonthFrom.QUESTION),
                NOVEMBER_15, new Div()).orElseThrow();

        assertThat(verdict.words()).isEqualTo("!{verdict.month.no}!");
    }

    @Test
    void noVerdictForCropsOrForAnAnswerWithNoValues() {
        assertThat(CalendarVerdict.of(List.of("Basilico", "MAY"), Map.of(), asked("MAY", MonthFrom.DEFAULT),
                NOVEMBER_15, new Div())).isEmpty();
        assertThat(CalendarVerdict.of(List.of(), Map.of(), asked("MAY", MonthFrom.DEFAULT), NOVEMBER_15, new Div())).isEmpty();
    }

    private static ContextSnapshot asked(String month, MonthFrom from) {
        return new ContextSnapshot("Casa", "Terrazzo", null, null, month, from, null);
    }
}
