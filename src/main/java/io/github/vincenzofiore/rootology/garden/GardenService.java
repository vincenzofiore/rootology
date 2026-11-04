package io.github.vincenzofiore.rootology.garden;

import io.github.vincenzofiore.rootology.facts.Crop;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotSummary;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Gardens, plots and plantings of a shared space. Every method takes the space and the person
 * asking, and refuses anyone who is not a member; ids from another space are treated as unknown.
 */
@Service
public class GardenService {

    private final SpaceService spaces;
    private final CropCatalog crops;
    private final GardenRepository gardens;
    private final PlotRepository plots;
    private final PlantingRepository plantings;
    private final RotationRule rotation;

    GardenService(SpaceService spaces, CropCatalog crops, GardenRepository gardens, PlotRepository plots,
                  PlantingRepository plantings, RotationRule rotation) {
        this.spaces = spaces;
        this.crops = crops;
        this.gardens = gardens;
        this.plots = plots;
        this.plantings = plantings;
        this.rotation = rotation;
    }

    @Transactional(readOnly = true)
    public List<GardenSummary> gardens(long spaceId, String login) {
        requireMember(spaceId, login);
        return gardens.findBySpaceIdOrderById(spaceId).stream().map(GardenService::summary).toList();
    }

    /** Coordinates are optional and rounded to 0.01 degrees, so only a coarse location is stored. */
    @Transactional
    public GardenSummary saveGarden(long spaceId, String login, Long gardenId, String name, String town,
                                    BigDecimal latitude, BigDecimal longitude) {
        return saveGarden(spaceId, login, gardenId, name, town, latitude, longitude, null);
    }

    /** countryCode: ISO 3166-1 alpha-2 (for example "IT"), or null when not known. */
    @Transactional
    public GardenSummary saveGarden(long spaceId, String login, Long gardenId, String name, String town,
                                    BigDecimal latitude, BigDecimal longitude, String countryCode) {
        requireMember(spaceId, login);
        String country = blankToNull(countryCode);
        if (country != null && !country.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("A country is a two-letter ISO code, not " + countryCode);
        }
        String cleanName = required(name, "A garden needs a name");
        BigDecimal lat = coarse(latitude, 90, "Latitude");
        BigDecimal lon = coarse(longitude, 180, "Longitude");
        Garden garden = gardenId == null
                ? new Garden(spaceId, cleanName, blankToNull(town), lat, lon, country, Instant.now())
                : garden(spaceId, gardenId);
        garden.update(cleanName, blankToNull(town), lat, lon, country);
        return summary(gardens.save(garden));
    }

    @Transactional
    public void deleteGarden(long spaceId, String login, long gardenId) {
        requireMember(spaceId, login);
        gardens.delete(garden(spaceId, gardenId));
    }

    @Transactional(readOnly = true)
    public List<PlotSummary> plots(long spaceId, String login, long gardenId) {
        requireMember(spaceId, login);
        return plots.findByGardenIdOrderByName(garden(spaceId, gardenId).getId()).stream()
                .map(GardenService::summary).toList();
    }

    @Transactional
    public PlotSummary savePlot(long spaceId, String login, long gardenId, Long plotId, String name,
                                PlotKind kind, Soil soil, BigDecimal areaM2) {
        requireMember(spaceId, login);
        String cleanName = required(name, "A plot needs a name");
        if (kind == null || soil == null) {
            throw new IllegalArgumentException("A plot needs a kind and a soil");
        }
        if (areaM2 != null && areaM2.signum() <= 0) {
            throw new IllegalArgumentException("The area must be positive");
        }
        Garden garden = garden(spaceId, gardenId);
        Plot plot = plotId == null ? new Plot(garden, cleanName, kind, soil, areaM2) : plot(spaceId, plotId);
        plot.update(cleanName, kind, soil, areaM2);
        return summary(plots.save(plot));
    }

    @Transactional
    public void deletePlot(long spaceId, String login, long plotId) {
        requireMember(spaceId, login);
        plots.delete(plot(spaceId, plotId));
    }

    @Transactional(readOnly = true)
    public List<PlantingSummary> plantings(long spaceId, String login, long plotId) {
        requireMember(spaceId, login);
        return plantings.findByPlotIdOrderByPlantedOnDesc(plot(spaceId, plotId).getId()).stream()
                .map(this::summary).toList();
    }

    /** The crop must be one of the knowledge base's crops. */
    @Transactional
    public PlantingSummary plant(long spaceId, String login, long plotId, String cropId, PlantingMethod method,
                                 LocalDate plantedOn, String notes) {
        requireMember(spaceId, login);
        if (cropId == null || crops.byId(cropId).isEmpty()) {
            throw new IllegalArgumentException("Unknown crop: " + cropId);
        }
        if (method == null || plantedOn == null) {
            throw new IllegalArgumentException("A planting needs a method and a date");
        }
        return summary(plantings.save(new Planting(plot(spaceId, plotId), cropId, method, plantedOn,
                blankToNull(notes))));
    }

    /** A rotation warning if this crop would follow one of the same family in the plot (rule family-rotation-v1). */
    @Transactional(readOnly = true)
    public Optional<RotationRule.RotationWarning> rotationWarning(long spaceId, String login, long plotId,
                                                                  String cropId, LocalDate on) {
        requireMember(spaceId, login);
        List<PlantingSummary> history = plantings.findByPlotIdOrderByPlantedOnDesc(plot(spaceId, plotId).getId())
                .stream().map(this::summary).toList();
        return rotation.check(history, cropId, on);
    }

    /** Harvested or removed on that day. */
    @Transactional
    public void endPlanting(long spaceId, String login, long plantingId, LocalDate endedOn) {
        requireMember(spaceId, login);
        planting(spaceId, plantingId).end(endedOn);
    }

    @Transactional
    public void deletePlanting(long spaceId, String login, long plantingId) {
        requireMember(spaceId, login);
        plantings.delete(planting(spaceId, plantingId));
    }

    /** What grows in the space's gardens on a given day: the context for questions about my garden. */
    @Transactional(readOnly = true)
    public List<PlantingSummary> growingOn(long spaceId, String login, LocalDate day) {
        requireMember(spaceId, login);
        return plantings.findByPlotGardenSpaceIdOrderByPlantedOnDesc(spaceId).stream()
                .filter(p -> p.isGrowingOn(day))
                .map(this::summary)
                .toList();
    }

    /**
     * The zone of Italy the space's first located garden lies in (NORD, CENTRO or SUD), for the
     * knowledge base's sowing windows. Coarse on purpose: north of about 44° N is NORD (Emilia-Romagna
     * and up), south of about 41.5° N is SUD (Campania, Apulia and down, the islands), CENTRO in between.
     */
    @Transactional(readOnly = true)
    public Optional<String> zone(long spaceId, String login) {
        requireMember(spaceId, login);
        return gardens.findBySpaceIdOrderById(spaceId).stream()
                .filter(g -> g.getLatitude() != null)
                .findFirst()
                .map(g -> g.inItaly() ? zoneOf(g.getLatitude().doubleValue()) : OUTSIDE_ITALY);
    }

    /** The zone of a garden abroad: the knowledge base's calendars cover Italy only. */
    public static final String OUTSIDE_ITALY = "OUTSIDE_ITALY";

    static String zoneOf(double latitude) {
        return latitude >= 44.0 ? "NORD" : latitude >= 41.5 ? "CENTRO" : "SUD";
    }

    /** A plot of the space by name, ignoring case; a unique partial match ("B" for "Bed B") also works. */
    @Transactional(readOnly = true)
    public Optional<PlotSummary> plotByName(long spaceId, String login, String name) {
        requireMember(spaceId, login);
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String wanted = name.trim().toLowerCase(java.util.Locale.ROOT);
        List<PlotSummary> all = gardens.findBySpaceIdOrderById(spaceId).stream()
                .flatMap(g -> plots.findByGardenIdOrderByName(g.getId()).stream())
                .map(GardenService::summary).toList();
        Optional<PlotSummary> exact = all.stream()
                .filter(p -> p.name().toLowerCase(java.util.Locale.ROOT).equals(wanted)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        List<PlotSummary> partial = all.stream().filter(p -> java.util.Arrays.asList(
                p.name().toLowerCase(java.util.Locale.ROOT).split("\\s+")).contains(wanted)).toList();
        return partial.size() == 1 ? Optional.of(partial.getFirst()) : Optional.empty();
    }

    private void requireMember(long spaceId, String login) {
        if (!spaces.isMember(spaceId, login)) {
            throw new SpaceAccessException(login + " is not a member of space " + spaceId);
        }
    }

    private Garden garden(long spaceId, long gardenId) {
        return gardens.findById(gardenId).filter(g -> g.getSpaceId() == spaceId)
                .orElseThrow(() -> new IllegalArgumentException("No such garden in this space"));
    }

    private Plot plot(long spaceId, long plotId) {
        return plots.findById(plotId).filter(p -> p.getGarden().getSpaceId() == spaceId)
                .orElseThrow(() -> new IllegalArgumentException("No such plot in this space"));
    }

    private Planting planting(long spaceId, long plantingId) {
        return plantings.findById(plantingId).filter(p -> p.getPlot().getGarden().getSpaceId() == spaceId)
                .orElseThrow(() -> new IllegalArgumentException("No such planting in this space"));
    }

    private static BigDecimal coarse(BigDecimal value, int limit, String what) {
        if (value == null) {
            return null;
        }
        if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw new IllegalArgumentException(what + " must be between -" + limit + " and " + limit);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static GardenSummary summary(Garden g) {
        return new GardenSummary(g.getId(), g.getName(), g.getTown(), g.getLatitude(), g.getLongitude(),
                g.getCountryCode());
    }

    private static PlotSummary summary(Plot p) {
        return new PlotSummary(p.getId(), p.getGarden().getId(), p.getName(), p.getKind(), p.getSoil(), p.getAreaM2());
    }

    private PlantingSummary summary(Planting p) {
        String name = crops.byId(p.getCropId()).map(Crop::italianName).orElse(p.getCropId());
        return new PlantingSummary(p.getId(), p.getPlot().getId(), p.getPlot().getName(), p.getCropId(), name,
                p.getMethod(), p.getPlantedOn(), p.getEndedOn(), p.getNotes());
    }
}
