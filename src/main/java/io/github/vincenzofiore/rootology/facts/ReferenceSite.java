package io.github.vincenzofiore.rootology.facts;

import java.util.Arrays;
import java.util.Optional;

/**
 * The reference site of each Italian zone (#62), used when a question names a zone: the cell's own
 * values, with no altitude correction. NORD is Milan, CENTRO is Vasto (the site of the curated windows)
 * and SUD is Palermo.
 */
public enum ReferenceSite {

    NORD("NORD", 45.46, 9.19),
    CENTRO("CENTRO", 42.11, 14.71),
    SUD("SUD", 38.12, 13.36);

    private final String zone;
    private final double latitude;
    private final double longitude;

    ReferenceSite(String zone, double latitude, double longitude) {
        this.zone = zone;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String zone() {
        return zone;
    }

    /** The site's profile, or null when no land cell lies within 0.15 degrees of it. */
    public SiteProfile profile(ClimateCells cells) {
        return cells.nearest(latitude, longitude).map(cell -> FrostSeasonRule.reference(cell, zone)).orElse(null);
    }

    public static Optional<ReferenceSite> of(String zone) {
        return Arrays.stream(values()).filter(site -> site.zone.equals(zone)).findFirst();
    }
}
