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
    private final org.springframework.context.ApplicationEventPublisher events;

    GardenService(SpaceService spaces, CropCatalog crops, GardenRepository gardens, PlotRepository plots,
                  PlantingRepository plantings, RotationRule rotation,
                  org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
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
        return saveGarden(spaceId, login, gardenId, name, town, latitude, longitude, countryCode, null);
    }

    /**
     * As above, with the elevation from GeoNames GTOPO30 (#62). Changing or clearing the coordinates
     * clears an elevation not given: it belonged to the old place. Without new coordinates, a saved
     * elevation is kept, so saving never depends on GeoNames.
     */
    @Transactional
    public GardenSummary saveGarden(long spaceId, String login, Long gardenId, String name, String town,
                                    BigDecimal latitude, BigDecimal longitude, String countryCode,
                                    Integer elevationM) {
        requireContributor(spaceId, login);
        String country = blankToNull(countryCode);
        if (country != null && !country.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("A country is a two-letter ISO code, not " + countryCode);
        }
        String cleanName = required(name, "A garden needs a name");
        BigDecimal lat = coarse(latitude, 90, "Latitude");
        BigDecimal lon = coarse(longitude, 180, "Longitude");
        Garden existing = gardenId == null ? null : garden(spaceId, gardenId);
        Integer elevation = elevation(lat, lon, elevationM, existing);
        Garden garden = existing == null
                ? new Garden(spaceId, cleanName, blankToNull(town), lat, lon, country, elevation, Instant.now())
                : existing;
        boolean moved = lat != null && lon != null && (existing == null
                || !lat.equals(existing.getLatitude()) || !lon.equals(existing.getLongitude()));
        garden.update(cleanName, blankToNull(town), lat, lon, country, elevation);
        GardenSummary saved = summary(gardens.save(garden));
        if (moved) {
            // A new place has no stored forecast yet: the map's popups would show no weather (#67).
            events.publishEvent(new GardenLocated(saved.id(), lat, lon));
        }
        return saved;
    }

    /** The elevation to store: none without coordinates; the old one kept only when the place did not change. */
    private static Integer elevation(BigDecimal latitude, BigDecimal longitude, Integer given, Garden existing) {
        if (latitude == null || longitude == null) {
            return null;
        }
        if (given != null) {
            return given;
        }
        boolean samePlace = existing != null && latitude.equals(existing.getLatitude())
                && longitude.equals(existing.getLongitude());
        return samePlace ? existing.getElevationM() : null;
    }

    @Transactional
    public void deleteGarden(long spaceId, String login, long gardenId) {
        requireContributor(spaceId, login);
        gardens.delete(garden(spaceId, gardenId));
    }

    @Transactional(readOnly = true)
    public List<PlotSummary> plots(long spaceId, String login, long gardenId) {
        requireMember(spaceId, login);
        return plots.findByGardenIdOrderByName(garden(spaceId, gardenId).getId()).stream()
                .map(GardenService::summary).toList();
    }

    /** Without the declared exposure (#54): use the other overload to set it. */
    @Transactional
    public PlotSummary savePlot(long spaceId, String login, long gardenId, Long plotId, String name,
                                PlotKind kind, Soil soil, BigDecimal areaM2) {
        return savePlot(spaceId, login, gardenId, plotId, name, kind, soil, areaM2, null, null, null, null);
    }

    /** orientation, directSun, wind and floor are declared by the gardener; all optional (#54). */
    @Transactional
    public PlotSummary savePlot(long spaceId, String login, long gardenId, Long plotId, String name,
                                PlotKind kind, Soil soil, BigDecimal areaM2, Orientation orientation,
                                DirectSun directSun, Wind wind, Integer floor) {
        requireContributor(spaceId, login);
        String cleanName = required(name, "A plot needs a name");
        if (kind == null || soil == null) {
            throw new IllegalArgumentException("A plot needs a kind and a soil");
        }
        if (areaM2 != null && areaM2.signum() <= 0) {
            throw new IllegalArgumentException("The area must be positive");
        }
        if (floor != null && (floor < 0 || floor > 200)) {
            throw new IllegalArgumentException("The floor must be between 0 and 200");
        }
        Garden garden = garden(spaceId, gardenId);
        Plot plot = plotId == null ? new Plot(garden, cleanName, kind, soil, areaM2) : plot(spaceId, plotId);
        plot.update(cleanName, kind, soil, areaM2, orientation, directSun, wind, floor);
        return summary(plots.save(plot));
    }

    @Transactional
    public void deletePlot(long spaceId, String login, long plotId) {
        requireContributor(spaceId, login);
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
        requireContributor(spaceId, login);
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
        requireContributor(spaceId, login);
        planting(spaceId, plantingId).end(endedOn);
    }

    @Transactional
    public void deletePlanting(long spaceId, String login, long plantingId) {
        requireContributor(spaceId, login);
        plantings.delete(planting(spaceId, plantingId));
    }

    /** One of the space's gardens by id (#56), or nothing when it is not this space's: another space's garden is unknown. */
    @Transactional(readOnly = true)
    public Optional<GardenSummary> garden(long spaceId, String login, long gardenId) {
        requireMember(spaceId, login);
        return gardens.findById(gardenId).filter(g -> g.getSpaceId() == spaceId).map(GardenService::summary);
    }

    /** What grows in one garden of the space on a given day, for the garden's home page (#56). */
    @Transactional(readOnly = true)
    public List<PlantingSummary> growingOn(long spaceId, String login, long gardenId, LocalDate day) {
        requireMember(spaceId, login);
        return plantings.findByPlotGardenSpaceIdOrderByPlantedOnDesc(spaceId).stream()
                .filter(p -> p.getPlot().getGarden().getId() == gardenId)
                .filter(p -> p.isGrowingOn(day))
                .map(this::summary)
                .toList();
    }

    /**
     * For system jobs, not for people (weather alerts, #37): every garden with coordinates and what grows
     * in it on that day, across all spaces. No person acts here, so there is no membership check; never
     * shown as such in the UI.
     */
    @Transactional(readOnly = true)
    public List<GardenViews.GrowingGarden> locatedGardensGrowing(LocalDate day) {
        java.util.Map<Long, List<PlantingSummary>> byGarden = new java.util.HashMap<>();
        plantings.findAll().stream().filter(p -> p.isGrowingOn(day))
                .forEach(p -> byGarden.computeIfAbsent(p.getPlot().getGarden().getId(), k -> new java.util.ArrayList<>())
                        .add(summary(p)));
        return gardens.findAll().stream()
                .filter(g -> g.getLatitude() != null && g.getLongitude() != null)
                .map(g -> new GardenViews.GrowingGarden(g.getSpaceId(), summary(g), byGarden.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    /**
     * The zone of Italy a garden lies in (NORD, CENTRO or SUD), for the knowledge base's sowing windows;
     * empty when the garden has no latitude. Coarse on purpose: north of about 44° N is NORD
     * (Emilia-Romagna and up), south of about 41.5° N is SUD (Campania, Apulia and down, the islands),
     * CENTRO in between. A garden abroad has no zone of the knowledge base.
     */
    public static Optional<String> zone(GardenSummary garden) {
        if (garden.latitude() == null) {
            return Optional.empty();
        }
        // A garden with no country was saved before the country was recorded (#30), so it is in Italy.
        boolean inItaly = garden.countryCode() == null || garden.countryCode().equals("IT");
        return Optional.of(inItaly ? zoneOf(garden.latitude().doubleValue()) : OUTSIDE_ITALY);
    }

    /** The zone of a garden abroad: the knowledge base's calendars cover Italy only. */
    public static final String OUTSIDE_ITALY = "OUTSIDE_ITALY";

    static String zoneOf(double latitude) {
        return latitude >= 44.0 ? "NORD" : latitude >= 41.5 ? "CENTRO" : "SUD";
    }

    /**
     * The plots of one garden that answer to a name (#55), ignoring case; a unique partial match ("B" for
     * "Bed B") also works. Only that garden is looked at: the same name in another garden is not a match.
     * Several plots matching is reported, never guessed.
     */
    @Transactional(readOnly = true)
    public GardenViews.PlotLookup plotNamed(long spaceId, String login, long gardenId, String name) {
        requireMember(spaceId, login);
        if (name == null || name.isBlank()) {
            return GardenViews.PlotLookup.none();
        }
        List<PlotSummary> all = plots.findByGardenIdOrderByName(garden(spaceId, gardenId).getId()).stream()
                .map(GardenService::summary).toList();
        List<PlotSummary> matches = named(all, PlotSummary::name, name);
        return matches.size() == 1 ? GardenViews.PlotLookup.one(matches.getFirst())
                : matches.isEmpty() ? GardenViews.PlotLookup.none() : GardenViews.PlotLookup.ambiguous(matches);
    }

    /**
     * The garden of a space that a question names (#57), with the plots' rule: the same name ignoring case
     * wins; otherwise a whole word of one garden's name. Several are never guessed; a town is not a name.
     */
    public GardenViews.GardenLookup gardenNamed(long spaceId, String login, String name) {
        List<GardenSummary> all = gardens(spaceId, login);
        if (name == null || name.isBlank()) {
            return new GardenViews.GardenLookup(null, List.of());
        }
        List<GardenSummary> matches = named(all, GardenSummary::name, name);
        return matches.size() == 1 ? new GardenViews.GardenLookup(matches.getFirst(), List.of())
                : new GardenViews.GardenLookup(null, matches.stream().map(GardenSummary::name).toList());
    }

    /** What answers to a name: those with that very name ignoring case, otherwise those with it as a whole word. */
    private static <T> List<T> named(List<T> all, java.util.function.Function<T, String> nameOf, String name) {
        String wanted = name.trim().toLowerCase(java.util.Locale.ROOT);
        List<T> exact = all.stream().filter(t -> nameOf.apply(t).toLowerCase(java.util.Locale.ROOT).equals(wanted))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return all.stream().filter(t -> java.util.Arrays.asList(
                nameOf.apply(t).toLowerCase(java.util.Locale.ROOT).split("\\s+")).contains(wanted)).toList();
    }

    /** Changes need an OWNER or MEMBER: a GUEST only looks (#33). */
    private void requireContributor(long spaceId, String login) {
        spaces.requireContributor(spaceId, login);
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
                g.getCountryCode(), g.getElevationM());
    }

    private static PlotSummary summary(Plot p) {
        return new PlotSummary(p.getId(), p.getGarden().getId(), p.getName(), p.getKind(), p.getSoil(), p.getAreaM2(),
                p.getOrientation(), p.getDirectSun(), p.getWind(), p.getFloor());
    }

    private PlantingSummary summary(Planting p) {
        String name = crops.byId(p.getCropId()).map(Crop::italianName).orElse(p.getCropId());
        return new PlantingSummary(p.getId(), p.getPlot().getId(), p.getPlot().getName(), p.getCropId(), name,
                p.getMethod(), p.getPlantedOn(), p.getEndedOn(), p.getNotes());
    }
}
