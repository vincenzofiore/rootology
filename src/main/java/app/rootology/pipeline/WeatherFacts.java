package app.rootology.pipeline;

import app.rootology.decision.Note;
import app.rootology.decision.Reading;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.Fact;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourcedFact;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.PlotKind;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.FrostRule;
import app.rootology.weather.SmithPeriodRule;
import app.rootology.weather.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The local forecast (#18) as readings for the decision layer, plus notes for the person. Used only for
 * questions about the current month, at the run's garden (#55). Without a garden or without a usable
 * forecast, the answer is given without weather and a note says so.
 */
@Component
class WeatherFacts {

    /** Readings to add and notes to show; both empty when the weather does not apply. */
    record Result(List<Reading> readings, List<Note> notes) {
        static final Result NONE = new Result(List.of(), List.of());
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final GardenService gardens;
    private final WeatherService weather;
    private final KnowledgeGraph knowledge;
    private final CropCatalog crops;
    private final Clock clock;

    @Autowired
    WeatherFacts(GardenService gardens, WeatherService weather, KnowledgeGraph knowledge, CropCatalog crops,
                 Clock clock) {
        this.gardens = gardens;
        this.weather = weather;
        this.knowledge = knowledge;
        this.crops = crops;
        this.clock = clock;
    }

    /** frost-risk-v1 on the crops that could be sown or planted now: (crop, avoidNow, frost) when too cold. */
    Result frost(QuestionContext context, List<SourcedFact> sowable) {
        if (!aboutNow(context)) {
            return Result.NONE;
        }
        if (context.weather() == null) {
            return context.gardenId() > 0
                    ? new Result(List.of(), List.of(Note.of(Note.Kind.NO_COORDINATES_FROST))) : Result.NONE;
        }
        Optional<Forecast> found = forecast(context);
        if (found.isEmpty()) {
            return new Result(List.of(), List.of(Note.of(Note.Kind.FORECAST_UNAVAILABLE_FROST)));
        }
        Forecast forecast = found.get();
        Optional<ForecastHour> coldest = FrostRule.coldest(forecast, clock.instant());
        if (coldest.isEmpty()) {
            return new Result(List.of(), List.of(Note.of(Note.Kind.NO_FORECAST_DAYS)));
        }
        double minimum = coldest.get().airTemperature();
        List<Reading> readings = new ArrayList<>();
        readings.add(new Reading(new Fact("Forecast", "minimumAirTemperatureNext7Days", String.format(Locale.ROOT, "%.1f", minimum)),
                Reading.Source.FORECAST, List.of(Forecast.SOURCE), null));
        Map<String, Double> killing = knowledge.killingTemperatures();
        List<String> avoided = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String crop : sowable.stream().map(f -> f.fact().subject()).distinct().toList()) {
            Optional<String> id = knowledge.resolve(crop).map(KnowledgeGraph.Entity::id);
            Double kill = id.map(killing::get).orElse(null);
            if (kill == null) {
                unknown.add(crop);
            } else if (FrostRule.tooCold(minimum, kill)) {
                avoided.add(crop);
                readings.add(Reading.inferred(new Fact(crop, "avoidNow", "frost"), FrostRule.ID,
                        List.of(Forecast.SOURCE, "fao-ecocrop")));
            }
        }
        List<Note> notes = new ArrayList<>();
        LocalDate day = LocalDate.ofInstant(coldest.get().time(), GardenPipelineAgent.ITALY);
        notes.add(Note.of(Note.Kind.FORECAST_MINIMUM, String.format(Locale.ROOT, "%.1f", minimum), day.toString(),
                staleSince(forecast)));
        if (inGreenhouse(context)) {
            notes.add(Note.of(Note.Kind.GREENHOUSE_OUTDOOR_MINIMUM, context.plot().found().name()));
        }
        if (!avoided.isEmpty()) {
            notes.add(Note.of(Note.Kind.FROST_EXCLUDED, FrostRule.ID, String.join(", ", avoided)));
        }
        if (minimum <= 2.0 && !unknown.isEmpty()) {
            notes.add(Note.of(Note.Kind.FROST_UNKNOWN, String.join(", ", unknown)));
        }
        return new Result(readings, notes);
    }

    /** smith-period-v1 on the crops growing now in the run's garden: (Late blight, favouredByWeather, crop). */
    Result blight(QuestionContext context, RunContext run, String language) {
        if (!aboutNow(context)) {
            return Result.NONE;
        }
        if (context.weather() == null) {
            return context.gardenId() > 0
                    ? new Result(List.of(), List.of(Note.of(Note.Kind.NO_COORDINATES_RISK))) : Result.NONE;
        }
        Optional<Forecast> found = forecast(context);
        if (found.isEmpty()) {
            return new Result(List.of(), List.of(Note.of(Note.Kind.FORECAST_UNAVAILABLE_RISK)));
        }
        Forecast forecast = found.get();
        LocalDate today = LocalDate.now(clock);
        Optional<LocalDate> period = SmithPeriodRule.firstPeriod(forecast, today.minusDays(2), today.plusDays(3),
                GardenPipelineAgent.ITALY);
        List<String> hosts = gardens.growingOn(context.spaceId(), run.startedBy(), context.gardenId(), today).stream()
                .map(PlantingSummary::cropId).filter(SmithPeriodRule.HOSTS::contains).distinct().toList();
        if (period.isEmpty() || hosts.isEmpty()) {
            return new Result(List.of(), List.of(Note.of(Note.Kind.NO_SMITH_PERIOD, staleSince(forecast))));
        }
        String blight = knowledge.organisms().stream().filter(o -> o.id().equals(SmithPeriodRule.LATE_BLIGHT))
                .map(o -> "it".equals(language) ? o.italianName() : o.englishName()).findFirst().orElse("Late blight");
        List<Reading> readings = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String cropId : hosts) {
            String name = crops.byId(cropId).map(c -> "it".equals(language) ? c.italianName() : c.englishName())
                    .orElse(cropId);
            names.add(name);
            readings.add(Reading.inferred(new Fact(blight, "favouredByWeather", name), SmithPeriodRule.ID,
                    List.of(Forecast.SOURCE)));
        }
        return new Result(readings, List.of(Note.of(Note.Kind.SMITH_PERIOD, period.get().toString(),
                String.join(", ", names), SmithPeriodRule.ID, staleSince(forecast))));
    }

    /**
     * A SOW_IN_PLOT question about a greenhouse or tunnel: frost-risk-v1 stays on the outdoor minimum, since no
     * source quantifies the protection, and the person is told so (#85).
     */
    private static boolean inGreenhouse(QuestionContext context) {
        return context.plot() != null && context.plot().found() != null
                && context.plot().found().kind() == PlotKind.GREENHOUSE;
    }

    /** The questions the forecast applies to: a real space, a garden, and the current month. */
    private boolean aboutNow(QuestionContext context) {
        if (!context.hasSpace() || context.gardenId() == 0 || context.intent() == null
                || context.intent().month() == null) {
            return false;
        }
        return LocalDate.now(clock).getMonth().name().equals(context.intent().month());
    }

    /** The run's garden's forecast; empty when the weather service gives nothing usable. */
    private Optional<Forecast> forecast(QuestionContext context) {
        return weather.around(context.weather().latitude(), context.weather().longitude());
    }

    /** When the stored copy of a stale forecast was fetched, as an ISO local date-time in Italy; empty if fresh. */
    private static String staleSince(Forecast forecast) {
        return forecast.stale() ? java.time.LocalDateTime.ofInstant(forecast.fetchedAt(), GardenPipelineAgent.ITALY)
                .withNano(0).toString() : "";
    }
}
