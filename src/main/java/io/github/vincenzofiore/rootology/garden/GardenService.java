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

    GardenService(SpaceService spaces, CropCatalog crops, GardenRepository gardens, PlotRepository plots,
                  PlantingRepository plantings) {
        this.spaces = spaces;
        this.crops = crops;
        this.gardens = gardens;
        this.plots = plots;
        this.plantings = plantings;
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
        requireMember(spaceId, login);
        String cleanName = required(name, "A garden needs a name");
        BigDecimal lat = coarse(latitude, 90, "Latitude");
        BigDecimal lon = coarse(longitude, 180, "Longitude");
        Garden garden = gardenId == null
                ? new Garden(spaceId, cleanName, blankToNull(town), lat, lon, Instant.now())
                : garden(spaceId, gardenId);
        garden.update(cleanName, blankToNull(town), lat, lon);
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
        return new GardenSummary(g.getId(), g.getName(), g.getTown(), g.getLatitude(), g.getLongitude());
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
