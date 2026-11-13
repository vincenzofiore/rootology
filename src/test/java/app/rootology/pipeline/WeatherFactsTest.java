package app.rootology.pipeline;

import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.Reading;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.Fact;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourcedFact;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The forecast as readings and notes, on a fixed day (4 November 2026) with a mocked weather service. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class WeatherFactsTest {

    static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Autowired
    GardenService gardens;

    @Autowired
    app.rootology.facts.ClimateCells cells;

    @Autowired
    SpaceService spaces;

    @Autowired
    KnowledgeGraph knowledge;

    @Autowired
    CropCatalog crops;

    private final WeatherService weather = mock(WeatherService.class);

    static final Clock CLOCK = Clock.fixed(NOW, GardenPipelineAgent.ITALY);

    private WeatherFacts facts() {
        return new WeatherFacts(gardens, weather, knowledge, crops, CLOCK);
    }

    /** A person with one garden, asked in it: the forecast is that garden's. */
    private RunContext space(String login, boolean withCoordinates) {
        spaces.signedIn(login);
        long id = app.rootology.TestSpaces.personal(spaces, login);
        var garden = gardens.saveGarden(id, login, null, "Terrace", "Roma",
                withCoordinates ? new BigDecimal("41.90") : null, withCoordinates ? new BigDecimal("12.50") : null);
        var bed = gardens.savePlot(id, login, garden.id(), null, "Bed", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.plant(id, login, bed.id(), "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 9, 1), null);
        return new RunContext(id, garden.id(), login);
    }

    /** The context the pipeline resolves for that run, before the facts are gathered (#55). */
    private QuestionContext context(RunContext run, app.rootology.decision.QueryKind kind,
                                    String month) {
        ContextResolver resolver = new ContextResolver(spaces, gardens, cells, CLOCK);
        return resolver.resolve(new app.rootology.decision.Intent(kind, null, null, "it", null, month),
                run);
    }

    /** Hourly from two days ago for a week, at this temperature and humidity. */
    private static Forecast steady(double temperature, double humidity) {
        List<ForecastHour> hours = new ArrayList<>();
        Instant start = NOW.minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        for (int i = 0; i < 24 * 7; i++) {
            hours.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), temperature, humidity, 0.0, 1));
        }
        return new Forecast(hours, NOW, false);
    }

    private static final List<SourcedFact> SOWABLE = List.of(
            new SourcedFact(new Fact("Aglio", "plantIn", "NOVEMBER"), List.of("rootology-editorial")),
            new SourcedFact(new Fact("Lattuga", "sowIn", "NOVEMBER"), List.of("rootology-editorial")),
            new SourcedFact(new Fact("Pomodoro", "sowIn", "NOVEMBER"), List.of("rootology-editorial")));

    @Test
    void frostRulesOutTenderCropsAndSaysWhatItCouldNotCheck() {
        when(weather.around(any(), any())).thenReturn(Optional.of(steady(-2.0, 80.0)));

        WeatherFacts.Result result = facts().frost(context(space("wanda", true),
                app.rootology.decision.QueryKind.SOW_NOW, "NOVEMBER"), SOWABLE);

        // Lettuce dies at 0 degrees (within the margin of -2), garlic stands -5, tomato has no ECOCROP value.
        assertThat(result.readings()).extracting(Reading::fact).containsExactly(
                new Fact("Forecast", "minimumAirTemperatureNext7Days", "-2.0"),
                new Fact("Lattuga", "avoidNow", "frost"));
        assertThat(result.readings()).extracting(Reading::source)
                .containsExactly(Reading.Source.FORECAST, Reading.Source.INFERRED);
        assertThat(result.notes()).extracting(app.rootology.decision.Note::english).anyMatch(n -> n.contains("-2.0 °C"))
                .anyMatch(n -> n.contains("frost-risk-v1") && n.contains("Lattuga"))
                .anyMatch(n -> n.startsWith("Frost tolerance not known") && n.contains("Pomodoro"));
    }

    @Test
    void aGreenhouseKeepsTheOutdoorMinimumAndSaysSo() {
        when(weather.around(any(), any())).thenReturn(Optional.of(steady(-2.0, 80.0)));
        RunContext run = space("bruna", true);
        gardens.savePlot(run.spaceId(), "bruna", run.gardenId(), null, "Tunnel", PlotKind.GREENHOUSE, Soil.LOAMY, null);
        ContextResolver resolver = new ContextResolver(spaces, gardens, cells, CLOCK);

        WeatherFacts.Result tunnel = facts().frost(resolver.resolve(new app.rootology.decision.Intent(
                app.rootology.decision.QueryKind.SOW_IN_PLOT, "Tunnel", null, "it", null, "NOVEMBER"), run), SOWABLE);
        WeatherFacts.Result bed = facts().frost(resolver.resolve(new app.rootology.decision.Intent(
                app.rootology.decision.QueryKind.SOW_IN_PLOT, "Bed", null, "it", null, "NOVEMBER"), run), SOWABLE);

        // Conservative (#85): the same exclusion as outdoors, with a note that the minimum is the outdoor one.
        assertThat(tunnel.readings()).extracting(Reading::fact).contains(new Fact("Lattuga", "avoidNow", "frost"));
        assertThat(tunnel.notes()).extracting(app.rootology.decision.Note::kind)
                .contains(app.rootology.decision.Note.Kind.GREENHOUSE_OUTDOOR_MINIMUM);
        assertThat(tunnel.notes()).extracting(app.rootology.decision.Note::english)
                .anyMatch(n -> n.startsWith("Tunnel is a greenhouse or tunnel"));
        assertThat(bed.notes()).extracting(app.rootology.decision.Note::kind)
                .doesNotContain(app.rootology.decision.Note.Kind.GREENHOUSE_OUTDOOR_MINIMUM);
    }

    @Test
    void anotherMonthOrNoCoordinatesMeansNoWeather() {
        when(weather.around(any(), any())).thenReturn(Optional.of(steady(-2.0, 80.0)));

        assertThat(facts().frost(context(space("xavi", true),
                app.rootology.decision.QueryKind.SOW_NOW, "MARCH"), SOWABLE))
                .isEqualTo(WeatherFacts.Result.NONE);
        WeatherFacts.Result noCoordinates = facts().frost(context(space("yuri", false),
                app.rootology.decision.QueryKind.SOW_NOW, "NOVEMBER"), SOWABLE);
        assertThat(noCoordinates.readings()).isEmpty();
        assertThat(noCoordinates.notes()).singleElement().extracting(app.rootology.decision.Note::english).asString().startsWith("Add your garden's coordinates");
    }

    @Test
    void aMissingForecastIsSaidAndChangesNothing() {
        when(weather.around(any(), any())).thenReturn(Optional.empty());

        WeatherFacts.Result result = facts().frost(context(space("zoe", true),
                app.rootology.decision.QueryKind.SOW_NOW, "NOVEMBER"), SOWABLE);

        assertThat(result.readings()).isEmpty();
        assertThat(result.notes()).singleElement().extracting(app.rootology.decision.Note::english).asString().contains("not available");
    }

    @Test
    void warmHumidDaysFavourLateBlightOnTheTomatoGrowingNow() {
        when(weather.around(any(), any())).thenReturn(Optional.of(steady(14.0, 95.0)));

        RunContext abel = space("abel", true);
        WeatherFacts.Result result = facts().blight(context(abel,
                app.rootology.decision.QueryKind.PEST_RISK, "NOVEMBER"), abel, "it");

        assertThat(result.readings()).extracting(Reading::fact).containsExactly(
                new Fact("Peronospora del pomodoro e della patata", "favouredByWeather", "Pomodoro"));
        assertThat(result.readings()).allMatch(r -> "smith-period-v1".equals(r.derivedBy()));
        assertThat(result.notes()).singleElement().extracting(app.rootology.decision.Note::english).asString().contains("Smith period").contains("Pomodoro");
    }

    @Test
    void dryWeatherGivesNoBlightReading() {
        when(weather.around(any(), any())).thenReturn(Optional.of(steady(14.0, 60.0)));

        RunContext bice = space("bice", true);
        WeatherFacts.Result result = facts().blight(context(bice,
                app.rootology.decision.QueryKind.PEST_RISK, "NOVEMBER"), bice, "it");

        assertThat(result.readings()).isEmpty();
        assertThat(result.notes()).singleElement().extracting(app.rootology.decision.Note::english).asString().startsWith("No Smith period");
    }
}
