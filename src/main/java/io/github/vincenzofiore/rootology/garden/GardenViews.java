package io.github.vincenzofiore.rootology.garden;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

/** What the garden module hands out: plain records, never entities. */
public final class GardenViews {

    /** countryCode: ISO 3166-1 alpha-2, or null when not known (gardens saved before #30, all in Italy). */
    public record GardenSummary(long id, String name, String town, BigDecimal latitude, BigDecimal longitude,
                                String countryCode) {
    }

    public record PlotSummary(long id, long gardenId, String name, PlotKind kind, Soil soil, BigDecimal areaM2) {
    }

    /** cropName is the crop's Italian name from the knowledge base, or the id if unknown. */
    /** A garden with coordinates and what grows in it on a day, for system jobs such as weather alerts (#37). */
    public record GrowingGarden(long spaceId, GardenSummary garden, List<PlantingSummary> growing) {
    }

    public record PlantingSummary(long id, long plotId, String plotName, String cropId, String cropName,
                                  PlantingMethod method, LocalDate plantedOn, LocalDate endedOn, String notes) {
    }

    private GardenViews() {
    }
}
