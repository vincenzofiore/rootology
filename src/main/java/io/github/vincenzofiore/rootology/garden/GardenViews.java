package io.github.vincenzofiore.rootology.garden;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the garden module hands out: plain records, never entities. */
public final class GardenViews {

    public record GardenSummary(long id, String name, String town, BigDecimal latitude, BigDecimal longitude) {
    }

    public record PlotSummary(long id, long gardenId, String name, PlotKind kind, Soil soil, BigDecimal areaM2) {
    }

    /** cropName is the crop's Italian name from the knowledge base, or the id if unknown. */
    public record PlantingSummary(long id, long plotId, String plotName, String cropId, String cropName,
                                  PlantingMethod method, LocalDate plantedOn, LocalDate endedOn, String notes) {
    }

    private GardenViews() {
    }
}
