package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Note;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The context of a question, resolved in one place (#55): what the question says first, then the run's
 * garden, then the defaults; and the ambiguity that escalates instead of picking a garden or a plot.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ContextResolverTest {

    /** "Today" is 4 November 2026, 10:00 in Italy, as for the quality suite. */
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), GardenPipelineAgent.ITALY);

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    private ContextResolver resolver() {
        return new ContextResolver(spaces, gardens, CLOCK);
    }

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).getFirst().id();
    }

    /** A garden of the person's space, with two plots and, optionally, coordinates. */
    private long garden(String login, String name, String latitude, String longitude) {
        long space = spaceOf(login);
        var garden = gardens.saveGarden(space, login, null, name, null,
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude));
        gardens.savePlot(space, login, garden.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.savePlot(space, login, garden.id(), null, "Aiuola B", PlotKind.RAISED_BED, Soil.LOAMY, null);
        return garden.id();
    }

    /** A garden outside Italy: no zone of the knowledge base, and weather still from its coordinates. */
    private long gardenAbroad(String login, String name) {
        long space = spaceOf(login);
        return gardens.saveGarden(space, login, null, name, "Lyon", new BigDecimal("45.76"),
                new BigDecimal("4.84"), "FR").id();
    }

    private static Intent sow(String zone, String month) {
        return new Intent(QueryKind.SOW_NOW, null, null, "it", zone, month);
    }

    private static Intent pestRisk(String zone) {
        return new Intent(QueryKind.PEST_RISK, null, null, "it", zone, null);
    }

    @Test
    void theQuestionsZoneWinsOverTheGardensAndSaysBoth() {
        long space = spaceOf("ctx-anna");
        long garden = garden("ctx-anna", "Orto di Torino", "45.07", "7.68");

        var context = resolver().resolve(sow("SUD", null), new RunContext(space, garden, "ctx-anna"));

        assertThat(context.intent().zone()).isEqualTo("SUD");
        assertThat(context.snapshot().zone()).isEqualTo("SUD");
        assertThat(context.snapshot().zoneFrom()).isEqualTo(ContextSnapshot.ZoneFrom.QUESTION);
        assertThat(context.notes()).extracting(Note::kind).containsExactly(Note.Kind.ZONE_OF_QUESTION);
        assertThat(context.notes().getFirst().english()).contains("SUD", "NORD");
    }

    @Test
    void withoutAZoneInTheQuestionTheGardensZoneIsUsed() {
        long space = spaceOf("ctx-bea");
        long garden = garden("ctx-bea", "Orto di Torino", "45.07", "7.68");

        var context = resolver().resolve(sow(null, null), new RunContext(space, garden, "ctx-bea"));

        assertThat(context.intent().zone()).isEqualTo("NORD");
        assertThat(context.snapshot().zoneFrom()).isEqualTo(ContextSnapshot.ZoneFrom.GARDEN);
        assertThat(context.notes()).isEmpty();
        assertThat(context.snapshot().gardenName()).isEqualTo("Orto di Torino");
        assertThat(context.snapshot().spaceName()).isNotBlank();
    }

    @Test
    void theMonthIsTheQuestionsOrTheCurrentOneInItaly() {
        long space = spaceOf("ctx-carla");
        long garden = garden("ctx-carla", "Orto", "41.90", "12.50");

        var asked = resolver().resolve(sow(null, "MARCH"), new RunContext(space, garden, "ctx-carla"));
        var byDefault = resolver().resolve(sow(null, null), new RunContext(space, garden, "ctx-carla"));

        assertThat(asked.snapshot().month()).isEqualTo("MARCH");
        assertThat(asked.snapshot().monthFrom()).isEqualTo(ContextSnapshot.MonthFrom.QUESTION);
        assertThat(byDefault.snapshot().month()).isEqualTo("NOVEMBER");
        assertThat(byDefault.snapshot().monthFrom()).isEqualTo(ContextSnapshot.MonthFrom.DEFAULT);
    }

    @Test
    void theForecastIsTheRunsGardensAndNothingWhenItHasNoCoordinates() {
        long space = spaceOf("ctx-dino");
        long located = garden("ctx-dino", "Con coordinate", "45.46", "9.19");
        long unlocated = garden("ctx-dino", "Senza coordinate", null, null);

        var withPlace = resolver().resolve(sow(null, null), new RunContext(space, located, "ctx-dino"));
        var withoutPlace = resolver().resolve(sow(null, null), new RunContext(space, unlocated, "ctx-dino"));

        assertThat(withPlace.weather().latitude()).isEqualByComparingTo("45.46");
        assertThat(withPlace.weather().longitude()).isEqualByComparingTo("9.19");
        assertThat(withoutPlace.weather()).as("no coordinates, no weather").isNull();
        assertThat(withoutPlace.snapshot().zone()).as("no latitude, the default zone")
                .isEqualTo("CENTRO");
        assertThat(withoutPlace.snapshot().zoneFrom()).isEqualTo(ContextSnapshot.ZoneFrom.DEFAULT);
    }

    @Test
    void aPlotIsLookedUpInTheRunsGardenAndThePivotIsItsRealName() {
        long space = spaceOf("ctx-elio");
        long garden = garden("ctx-elio", "Orto", null, null);

        var context = resolver().resolve(new Intent(QueryKind.SOW_IN_PLOT, "aiuola b", null, "it", null, null),
                new RunContext(space, garden, "ctx-elio"));

        assertThat(context.plot().outcome()).isEqualTo(QuestionContext.Plot.Outcome.FOUND);
        assertThat(context.refusal()).isNull();
        assertThat(context.intent().subject()).as("the plot as it is really called")
                .isEqualTo("Aiuola B");
        assertThat(context.snapshot().plotName()).isEqualTo("Aiuola B");
    }

    @Test
    void aPlotNotFoundOrMatchingSeveralEscalatesWithANote() {
        long space = spaceOf("ctx-fabio");
        long garden = garden("ctx-fabio", "Orto", null, null);

        var missing = resolver().resolve(new Intent(QueryKind.SOW_IN_PLOT, "Aiuola Z", null, "it", null, null),
                new RunContext(space, garden, "ctx-fabio"));
        var ambiguous = resolver().resolve(new Intent(QueryKind.SOW_IN_PLOT, "Aiuola", null, "it", null, null),
                new RunContext(space, garden, "ctx-fabio"));

        assertThat(missing.refusal()).isEqualTo(ContextResolver.PLOT_REASON);
        assertThat(missing.notes()).extracting(Note::kind).containsExactly(Note.Kind.PLOT_NOT_FOUND);
        assertThat(missing.notes().getFirst().english()).contains("Aiuola Z", "Orto");
        assertThat(ambiguous.refusal()).isEqualTo(ContextResolver.PLOT_REASON);
        assertThat(ambiguous.notes()).extracting(Note::kind).containsExactly(Note.Kind.PLOT_AMBIGUOUS);
        assertThat(ambiguous.notes().getFirst().english()).contains("Aiuola A", "Aiuola B");
    }

    @Test
    void aGardenDeletedBetweenThePageAndTheQuestionIsNotTheSpaceLevel() {
        long space = spaceOf("ctx-gino");
        long garden = garden("ctx-gino", "Orto sparito", "45.46", "9.19");
        gardens.deleteGarden(space, "ctx-gino", garden);

        var context = resolver().resolve(sow(null, null), new RunContext(space, garden, "ctx-gino"));

        assertThat(context.refusal()).isEqualTo(ContextResolver.GARDEN_GONE_REASON);
        assertThat(context.notes()).extracting(Note::kind).containsExactly(Note.Kind.GARDEN_GONE);
        assertThat(context.gardenId()).as("no garden is used at all").isZero();
        assertThat(context.snapshot()).as("nothing true to show").isNull();
    }

    @Test
    void atTheSpaceLevelAGeneralQuestionUsesTheDefaultsAndSaysSo() {
        long space = spaceOf("ctx-ida");
        garden("ctx-ida", "Orto", "45.46", "9.19");

        var context = resolver().resolve(sow(null, null), new RunContext(space, 0, "ctx-ida"));

        assertThat(context.refusal()).isNull();
        assertThat(context.intent().zone()).isEqualTo("CENTRO");
        assertThat(context.snapshot().gardenName()).isNull();
        assertThat(context.snapshot().zoneFrom()).isEqualTo(ContextSnapshot.ZoneFrom.DEFAULT);
        assertThat(context.weather()).as("no garden, no forecast").isNull();
        assertThat(context.notes()).extracting(Note::kind).containsExactly(Note.Kind.NO_GARDEN_DEFAULTS);
    }

    @Test
    void atTheSpaceLevelAQuestionAboutTheGardenEscalates() {
        long space = spaceOf("ctx-luca");
        garden("ctx-luca", "Orto", "45.46", "9.19");

        var context = resolver().resolve(new Intent(QueryKind.GROWING_NOW, null, null, "it"), 
                new RunContext(space, 0, "ctx-luca"));

        assertThat(context.refusal()).isEqualTo(ContextResolver.NEEDS_GARDEN_REASON);
        assertThat(context.notes()).extracting(Note::kind).containsExactly(Note.Kind.NO_GARDEN_FOR_QUESTION);
    }

    @Test
    void aRunWithoutASpaceGetsNoNoteAndNoSnapshot() {
        var context = resolver().resolve(sow(null, null), RunContext.NONE);

        assertThat(context.snapshot()).isNull();
        assertThat(context.notes()).isEmpty();
        assertThat(context.refusal()).isNull();
        assertThat(context.intent().zone()).as("the defaults still reach the facts").isEqualTo("CENTRO");
        assertThat(context.intent().month()).isEqualTo("NOVEMBER");
    }

    @Test
    void aKindThatNeedsNoZoneKeepsTheIntentAsItIs() {
        var context = resolver().resolve(new Intent(QueryKind.COMPANIONS, "pomodoro", null, "it"),
                RunContext.NONE);

        assertThat(context.intent().zone()).isNull();
        assertThat(context.intent().month()).isNull();
        assertThat(context.snapshot()).isNull();
    }

    @Test
    void aPestQuestionGetsTheMonthAndNoZone() {
        long space = spaceOf("ctx-mara");
        long garden = garden("ctx-mara", "Orto di Torino", "45.07", "7.68");

        var context = resolver().resolve(pestRisk(null), new RunContext(space, garden, "ctx-mara"));

        // The pest months apply in every zone, so no zone plays a part (#58).
        assertThat(context.intent().zone()).isNull();
        assertThat(context.intent().month()).isEqualTo("NOVEMBER");
        assertThat(context.snapshot().zone()).isNull();
        assertThat(context.snapshot().zoneFrom()).isNull();
        assertThat(context.snapshot().month()).isEqualTo("NOVEMBER");
        assertThat(context.snapshot().monthFrom()).isEqualTo(ContextSnapshot.MonthFrom.DEFAULT);
        assertThat(context.notes()).isEmpty();
    }

    @Test
    void aPestQuestionAbroadGetsTheMonthAndNoZone() {
        long space = spaceOf("ctx-nino");
        long garden = gardenAbroad("ctx-nino", "Jardin");

        var context = resolver().resolve(pestRisk(null), new RunContext(space, garden, "ctx-nino"));

        assertThat(context.intent().zone()).isNull();
        assertThat(context.intent().month()).isEqualTo("NOVEMBER");
        assertThat(context.snapshot().zone()).isNull();
        assertThat(context.notes()).isEmpty();
        assertThat(context.weather()).as("the garden's own forecast still applies").isNotNull();
    }

    @Test
    void aPestQuestionNamingAZoneKeepsNoZoneAndSaysNothing() {
        long space = spaceOf("ctx-olga");
        long garden = garden("ctx-olga", "Orto di Torino", "45.07", "7.68");

        var context = resolver().resolve(pestRisk("SUD"), new RunContext(space, garden, "ctx-olga"));

        assertThat(context.intent().zone()).isNull();
        assertThat(context.snapshot().zone()).isNull();
        assertThat(context.notes()).as("no zone, so nothing to tell about it").isEmpty();
    }

    @Test
    void aSowingQuestionAbroadStillGetsTheOutsideItalyZone() {
        long space = spaceOf("ctx-paolo");
        long garden = gardenAbroad("ctx-paolo", "Jardin");

        var context = resolver().resolve(new Intent(QueryKind.SOWING_TIME, "pomodoro", null, "it"),
                new RunContext(space, garden, "ctx-paolo"));

        assertThat(context.intent().zone()).isEqualTo(GardenService.OUTSIDE_ITALY);
        assertThat(context.snapshot().zone()).isEqualTo(GardenService.OUTSIDE_ITALY);
        assertThat(context.snapshot().zoneFrom()).isEqualTo(ContextSnapshot.ZoneFrom.GARDEN);
    }
}
