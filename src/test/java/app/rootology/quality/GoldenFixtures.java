package app.rootology.quality;

import app.rootology.experiments.GoldenQuestions;
import app.rootology.facts.Contribution;
import app.rootology.facts.KnowledgeContributions;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenService;
import app.rootology.garden.Orientation;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.garden.Wind;
import app.rootology.pipeline.RunContext;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;

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
                        new BigDecimal("12.50"), "IT", 25);
                var a = gardens.savePlot(space, login, created.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
                var b = gardens.savePlot(space, login, created.id(), null, "Aiuola B", PlotKind.RAISED_BED, Soil.LOAMY, null);
                gardens.plant(space, login, a.id(), "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 9, 1), null);
                gardens.plant(space, login, a.id(), "lettuce", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
                var pea = gardens.plant(space, login, b.id(), "pea", PlantingMethod.SOWN, LocalDate.of(2026, 3, 1), null);
                gardens.endPlanting(space, login, pea.id(), LocalDate.of(2026, 6, 30));
                yield created.id();
            }
            case "lyon" -> {
                var created = gardens.saveGarden(space, login, null, "Jardin", "Lyon", new BigDecimal("45.76"),
                        new BigDecimal("4.84"), "FR", 171);
                var plot = gardens.savePlot(space, login, created.id(), null, "Planche", PlotKind.GROUND,
                        Soil.LOAMY, null);
                gardens.plant(space, login, plot.id(), "lettuce", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
                yield created.id();
            }
            // Two gardens in one space (#55): the question is asked in one of them, or outside both.
            case "two-gardens-north", "two-gardens-south", "two-gardens-space" -> {
                var turin = gardens.saveGarden(space, login, null, "Orto di Torino", "Torino", new BigDecimal("45.07"),
                        new BigDecimal("7.69"), "IT", 205);
                var palermo = gardens.saveGarden(space, login, null, "Orto di Palermo", "Palermo",
                        new BigDecimal("38.12"), new BigDecimal("13.36"), "IT", 13);
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
                        new BigDecimal("13.36"), "IT", 13);
                gardens.savePlot(space, login, balcony.id(), null, "Davanzale", PlotKind.CONTAINER, Soil.POTTING_MIX,
                        null, Orientation.N, DirectSun.PART_SHADE, Wind.EXPOSED, 3);
                yield balcony.id();
            }
            // Two gardens with similar names in Vasto (#57): lettuce on the big terrace, garlic on the small one.
            case "two-terraces-small", "two-terraces-space" -> {
                var big = gardens.saveGarden(space, login, null, "Terrazzo grande", "Vasto", new BigDecimal("42.11"),
                        new BigDecimal("14.71"), "IT", 144);
                var small = gardens.saveGarden(space, login, null, "Terrazzo piccolo", "Vasto",
                        new BigDecimal("42.11"), new BigDecimal("14.71"), "IT", 144);
                var tub = gardens.savePlot(space, login, big.id(), null, "Vasca", PlotKind.CONTAINER, Soil.POTTING_MIX, null);
                var pot = gardens.savePlot(space, login, small.id(), null, "Vaso", PlotKind.CONTAINER, Soil.POTTING_MIX, null);
                gardens.plant(space, login, tub.id(), "lettuce", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
                gardens.plant(space, login, pot.id(), "garlic", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 10, 15), null);
                yield c.garden().equals("two-terraces-small") ? small.id() : 0L;
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
