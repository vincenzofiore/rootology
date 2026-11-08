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

    /** orientation, directSun, wind and floor are declared by the gardener and all optional (#54). */
    public record PlotSummary(long id, long gardenId, String name, PlotKind kind, Soil soil, BigDecimal areaM2,
                              Orientation orientation, DirectSun directSun, Wind wind, Integer floor) {
    }

    /**
     * The plots of one garden that answer to a name (#55): plot is the one meant, or null when none does;
     * matches lists the plots when more than one does, so the caller can ask instead of picking one.
     */
    public record PlotLookup(PlotSummary plot, List<String> matches) {

        static PlotLookup one(PlotSummary plot) {
            return new PlotLookup(plot, List.of());
        }

        static PlotLookup none() {
            return new PlotLookup(null, List.of());
        }

        static PlotLookup ambiguous(List<PlotSummary> plots) {
            return new PlotLookup(null, plots.stream().map(PlotSummary::name).toList());
        }

        public boolean found() {
            return plot != null;
        }

        public boolean ambiguous() {
            return plot == null && !matches.isEmpty();
        }
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
