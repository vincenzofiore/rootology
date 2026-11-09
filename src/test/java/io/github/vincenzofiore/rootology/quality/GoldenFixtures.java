package io.github.vincenzofiore.rootology.quality;

import io.github.vincenzofiore.rootology.experiments.GoldenQuestions;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.garden.DirectSun;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.Orientation;
import io.github.vincenzofiore.rootology.garden.PlantingMethod;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
import io.github.vincenzofiore.rootology.garden.Wind;
import io.github.vincenzofiore.rootology.pipeline.RunContext;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import io.github.vincenzofiore.rootology.weather.Forecast;
import io.github.vincenzofiore.rootology.weather.ForecastHour;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** The context of each golden question (#20): a new space, its garden, its facts, and the forecast. */
final class GoldenFixtures {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");

    private final SpaceService spaces;
    private final GardenService gardens;
    private final KnowledgeContributions contributions;
    private final AtomicLong proposalIds = new AtomicLong(900_000);

    GoldenFixtures(SpaceService spaces, GardenService gardens, KnowledgeContributions contributions) {
        this.spaces = spaces;
        this.gardens = gardens;
        this.contributions = contributions;
    }

    /** "Today" of the suite, at 10:00 in Italy. */
    static Clock clock(GoldenQuestions golden) {
        return Clock.fixed(golden.today().atTime(10, 0).atZone(ITALY).toInstant(), ITALY);
    }

    /**
     * A new space per case (prefix keeps runs apart), so cases never see each other's garden or facts.
     * The run is inside the case's garden (#55), and at the space level only when the case has none.
     */
    RunContext space(String prefix, GoldenQuestions.Case c) {
        String login = prefix + "-" + c.id();
        spaces.signedIn(login);
        long space = spaces.spacesOf(login).getFirst().id();
        long garden = switch (c.garden()) {
            case "rome" -> {
                var created = gardens.saveGarden(space, login, null, "Orto", "Roma", new BigDecimal("41.90"),
                        new BigDecimal("12.50"), "IT");
                var a = gardens.savePlot(space, login, created.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
                var b = gardens.savePlot(space, login, created.id(), null, "Aiuola B", PlotKind.RAISED_BED, Soil.LOAMY, null);
                gardens.plant(space, login, a.id(), "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 9, 1), null);
                gardens.plant(space, login, a.id(), "lettuce", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
                var pea = gardens.plant(space, login, b.id(), "pea", PlantingMethod.SOWN, LocalDate.of(2026, 3, 1), null);
                gardens.endPlanting(space, login, pea.id(), LocalDate.of(2026, 6, 30));
                yield created.id();
            }
            case "lyon" -> gardens.saveGarden(space, login, null, "Jardin", "Lyon", new BigDecimal("45.76"),
                    new BigDecimal("4.84"), "FR").id();
            // Two gardens in one space (#55): the question is asked in one of them, or outside both.
            case "two-gardens-north", "two-gardens-south", "two-gardens-space" -> {
                var turin = gardens.saveGarden(space, login, null, "Orto di Torino", "Torino", new BigDecimal("45.07"),
                        new BigDecimal("7.69"), "IT");
                var palermo = gardens.saveGarden(space, login, null, "Orto di Palermo", "Palermo",
                        new BigDecimal("38.12"), new BigDecimal("13.36"), "IT");
                var north = gardens.savePlot(space, login, turin.id(), null, "Aiuola", PlotKind.GROUND, Soil.LOAMY, null);
                var south = gardens.savePlot(space, login, palermo.id(), null, "Aiuola", PlotKind.GROUND, Soil.LOAMY, null);
                gardens.plant(space, login, north.id(), "garlic", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 10, 15), null);
                gardens.plant(space, login, south.id(), "broad-bean", PlantingMethod.SOWN, LocalDate.of(2026, 10, 20), null);
                yield switch (c.garden()) {
                    case "two-gardens-north" -> turin.id();
                    case "two-gardens-south" -> palermo.id();
                    default -> 0L;
                };
            }
            // A balcony in Palermo whose only plot is declared in part shade (#54), nothing planted.
            case "balcony" -> {
                var balcony = gardens.saveGarden(space, login, null, "Balcone", "Palermo", new BigDecimal("38.12"),
                        new BigDecimal("13.36"), "IT");
                gardens.savePlot(space, login, balcony.id(), null, "Davanzale", PlotKind.CONTAINER, Soil.POTTING_MIX,
                        null, Orientation.N, DirectSun.PART_SHADE, Wind.EXPOSED, 3);
                yield balcony.id();
            }
            case "none" -> 0L;
            default -> throw new IllegalArgumentException("Unknown garden fixture " + c.garden());
        };
        for (GoldenQuestions.SpaceAlias alias : c.spaceFacts()) {
            contributions.apply(proposalIds.incrementAndGet(), space,
                    new Contribution.Alias(alias.targetId(), alias.name(), alias.language()));
        }
        return new RunContext(space, garden, login);
    }

    static Optional<Forecast> forecast(String fixture, Clock clock) {
        return switch (fixture) {
            case "missing" -> Optional.empty();
            case "frost" -> {
                Instant start = clock.instant().truncatedTo(ChronoUnit.HOURS);
                List<ForecastHour> hours = new ArrayList<>();
                for (int i = 0; i < 24 * 7; i++) {
                    hours.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), -2.0, 80.0, 0.0, 1));
                }
                yield Optional.of(new Forecast(hours, start, false));
            }
            default -> throw new IllegalArgumentException("Unknown weather fixture " + fixture);
        };
    }
}
