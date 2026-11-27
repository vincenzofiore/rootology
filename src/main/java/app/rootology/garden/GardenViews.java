package app.rootology.garden;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

/** What the garden module hands out: plain records, never entities. */
public final class GardenViews {

    /**
     * countryCode: ISO 3166-1 alpha-2, or null when not known (gardens saved before #30, all in Italy).
     * regionCode: GeoNames first-level administrative code (for Italy the region, "01"…"20"), or null when
     * not known (#136); it belongs to the place, so it is cleared when the garden moves or loses coordinates.
     * elevationM: metres above sea level from GeoNames GTOPO30 (#62), or null when not known.
     */
    public record GardenSummary(long id, String name, String town, BigDecimal latitude, BigDecimal longitude,
                                String countryCode, String regionCode, Integer elevationM) {
    }

    /**
     * orientation, directSun, wind and floor are declared by the gardener and all optional (#54); depthCm is the
     * soil depth of a container area, in whole centimetres, declared the same way (#186).
     */
    public record PlotSummary(long id, long gardenId, String name, PlotKind kind, Soil soil, BigDecimal areaM2,
                              Orientation orientation, DirectSun directSun, Wind wind, Integer floor,
                              Integer sunHoursSummer, Integer sunHoursWinter, String sunDeclaredBy,
                              java.time.Instant sunDeclaredAt, Integer depthCm) {

        /** Without the sun hours of #89, as before them. */
        public PlotSummary(long id, long gardenId, String name, PlotKind kind, Soil soil, BigDecimal areaM2,
                           Orientation orientation, DirectSun directSun, Wind wind, Integer floor) {
            this(id, gardenId, name, kind, soil, areaM2, orientation, directSun, wind, floor, null, null, null, null,
                    null);
        }

        /** Without the container depth of #186, as before it. */
        public PlotSummary(long id, long gardenId, String name, PlotKind kind, Soil soil, BigDecimal areaM2,
                           Orientation orientation, DirectSun directSun, Wind wind, Integer floor,
                           Integer sunHoursSummer, Integer sunHoursWinter, String sunDeclaredBy,
                           java.time.Instant sunDeclaredAt) {
            this(id, gardenId, name, kind, soil, areaM2, orientation, directSun, wind, floor, sunHoursSummer,
                    sunHoursWinter, sunDeclaredBy, sunDeclaredAt, null);
        }

        /**
         * The area's direct sun in the month of a question (#89): the level from that season's hours when they
         * are declared (summer April-September, winter October-March), otherwise the declared level, possibly
         * none. Nothing is guessed from the other season.
         */
        public DirectSun directSunIn(java.time.Month month) {
            Integer hours = LightRule.isSummer(month) ? sunHoursSummer : sunHoursWinter;
            return hours != null ? LightRule.fromHours(hours) : directSun;
        }
    }

    /**
     * The gardens of a space that answer to a name written in a question (#57), with the same rule as the
     * plots: garden is the one meant, or null when none does; matches lists them when more than one does,
     * with their ids so the person can choose one (#107).
     */
    public record GardenLookup(GardenSummary garden, List<GardenSummary> matches) {

        public boolean found() {
            return garden != null;
        }

        public boolean ambiguous() {
            return !matches.isEmpty();
        }

        /** The matching gardens' names, for a note that says which ones they are. */
        public List<String> matchNames() {
            return matches.stream().map(GardenSummary::name).toList();
        }
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

    public record PlantingSummary(long id, long plotId, String plotName, PlotKind plotKind, String cropId,
                                  String cropName, PlantingMethod method, LocalDate plantedOn, LocalDate endedOn, String notes) {
    }

    private GardenViews() {
    }
}
