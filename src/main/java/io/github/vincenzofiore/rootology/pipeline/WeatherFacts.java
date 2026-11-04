package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Reading;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.facts.Fact;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;
import io.github.vincenzofiore.rootology.facts.SourcedFact;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.weather.Forecast;
import io.github.vincenzofiore.rootology.weather.ForecastHour;
import io.github.vincenzofiore.rootology.weather.FrostRule;
import io.github.vincenzofiore.rootology.weather.SmithPeriodRule;
import io.github.vincenzofiore.rootology.weather.WeatherService;
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
 * questions about the current month, from the space's first garden with coordinates. Without coordinates
 * or without a usable forecast, the answer is given without weather and a note says so.
 */
@Component
class WeatherFacts {

    /** Readings to add and notes to show; both empty when the weather does not apply. */
    record Result(List<Reading> readings, List<String> notes) {
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
    Result frost(RunContext run, String month, List<SourcedFact> sowable) {
        Optional<Optional<Forecast>> located = forecast(run, month);
        if (located.isEmpty()) {
            return noLocation(run, month, "frost was not checked");
        }
        if (located.get().isEmpty()) {
            return new Result(List.of(), List.of("The weather forecast is not available right now: frost was not checked."));
        }
        Forecast forecast = located.get().get();
        Optional<ForecastHour> coldest = FrostRule.coldest(forecast, clock.instant());
        if (coldest.isEmpty()) {
            return new Result(List.of(), List.of("The weather forecast has no coming days: frost was not checked."));
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
        List<String> notes = new ArrayList<>();
        String when = DAY.format(coldest.get().time().atZone(GardenPipelineAgent.ITALY));
        notes.add(String.format(Locale.ROOT, "Forecast minimum in the next 7 days: %.1f °C on %s (MET Norway%s).",
                minimum, when, staleNote(forecast)));
        if (!avoided.isEmpty()) {
            notes.add("Not advised now because of frost risk (" + FrostRule.ID + "): " + String.join(", ", avoided) + ".");
        }
        if (minimum <= 2.0 && !unknown.isEmpty()) {
            notes.add("Frost tolerance not known, not checked: " + String.join(", ", unknown) + ".");
        }
        return new Result(readings, notes);
    }

    /** smith-period-v1 on the crops growing now: (Late blight, favouredByWeather, crop) around today. */
    Result blight(RunContext run, String month, String language) {
        Optional<Optional<Forecast>> located = forecast(run, month);
        if (located.isEmpty()) {
            return noLocation(run, month, "weather-driven risk was not checked");
        }
        if (located.get().isEmpty()) {
            return new Result(List.of(), List.of("The weather forecast is not available right now: "
                    + "weather-driven risk was not checked."));
        }
        Forecast forecast = located.get().get();
        LocalDate today = LocalDate.now(clock);
        Optional<LocalDate> period = SmithPeriodRule.firstPeriod(forecast, today.minusDays(2), today.plusDays(3),
                GardenPipelineAgent.ITALY);
        List<String> hosts = gardens.growingOn(run.spaceId(), run.startedBy(), today).stream()
                .map(PlantingSummary::cropId).filter(SmithPeriodRule.HOSTS::contains).distinct().toList();
        if (period.isEmpty() || hosts.isEmpty()) {
            return new Result(List.of(), List.of("No Smith period around today (MET Norway" + staleNote(forecast) + ")."));
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
        return new Result(readings, List.of("Smith period ending %s: the weather favours late blight on %s (%s, MET Norway%s)."
                .formatted(DAY.format(period.get()), String.join(", ", names), SmithPeriodRule.ID, staleNote(forecast))));
    }

    /** Empty when the question is not about now or the space has no garden with coordinates. */
    private Optional<Optional<Forecast>> forecast(RunContext run, String month) {
        if (!run.hasSpace() || !LocalDate.now(clock).getMonth().name().equals(month)) {
            return Optional.empty();
        }
        return located(run).map(g -> weather.around(g.latitude(), g.longitude()));
    }

    private Optional<GardenSummary> located(RunContext run) {
        return gardens.gardens(run.spaceId(), run.startedBy()).stream()
                .filter(g -> g.latitude() != null && g.longitude() != null).findFirst();
    }

    private Result noLocation(RunContext run, String month, String what) {
        boolean now = LocalDate.now(clock).getMonth().name().equals(month);
        if (!now || !run.hasSpace()) {
            return Result.NONE;
        }
        return new Result(List.of(), List.of("Add your garden's coordinates to use the local forecast: " + what + "."));
    }

    private static String staleNote(Forecast forecast) {
        return forecast.stale() ? ", from " + DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH)
                .format(forecast.fetchedAt().atZone(GardenPipelineAgent.ITALY)) + ": the service is not reachable" : "";
    }
}
