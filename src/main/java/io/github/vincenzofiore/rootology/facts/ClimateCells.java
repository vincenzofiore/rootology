package io.github.vincenzofiore.rootology.facts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The climate profile of every land cell of Europe (#62), from `knowledge/climate-cells-v1.csv.gz`:
 * AgERA5 1996-2025 frost dates and growing season, with the cell's mean land elevation and land
 * fraction from the Copernicus DEM GLO-90. Stored as compact primitive arrays: the file has about
 * 128,000 cells and is read once at startup.
 *
 * <p>Dates are days of the year (1-366), 0 when the cell has no such frost. Elevation is metres,
 * -9999 when the DEM has no value. Land fraction is kept as a percentage (0-100).
 */
@org.springframework.stereotype.Component
public class ClimateCells {

    /** One cell as the rules read it; dates are days of the year, null when there is no such frost. */
    public record Cell(double latitude, double longitude, int elevationM, double landFraction,
                       Integer lastFrostP50, Integer lastFrostP80, Integer firstFrostP50, Integer firstFrostP20,
                       int frostYears) {

        /** The cell as it is shown, for example "45.1/7.7". */
        public String label() {
            return String.format(Locale.ROOT, "%.1f/%.1f", latitude, longitude);
        }
    }

    private final int[] latTenths;
    private final int[] lonTenths;
    private final short[] elevation;
    private final byte[] landPercent;
    private final short[] lastFrostP50;
    private final short[] lastFrostP80;
    private final short[] firstFrostP50;
    private final short[] firstFrostP20;
    private final byte[] frostYears;
    private final Map<Integer, Integer> index;
    /** The extent of the grid in tenths of a degree: outside it nothing is known, not even land or sea. */
    private int minLat = Integer.MAX_VALUE;
    private int maxLat = Integer.MIN_VALUE;
    private int minLon = Integer.MAX_VALUE;
    private int maxLon = Integer.MIN_VALUE;

    /** The file, read once at startup; the app does not start when it is malformed (#62). */
    public ClimateCells() {
        ClimateCells read = ClimateCellsReader.read();
        this.latTenths = read.latTenths;
        this.lonTenths = read.lonTenths;
        this.elevation = read.elevation;
        this.landPercent = read.landPercent;
        this.lastFrostP50 = read.lastFrostP50;
        this.lastFrostP80 = read.lastFrostP80;
        this.firstFrostP50 = read.firstFrostP50;
        this.firstFrostP20 = read.firstFrostP20;
        this.frostYears = read.frostYears;
        this.index = read.index;
        extent();
    }

    ClimateCells(int[] latTenths, int[] lonTenths, short[] elevation, byte[] landPercent, short[] lastFrostP50,
                 short[] lastFrostP80, short[] firstFrostP50, short[] firstFrostP20, byte[] frostYears) {
        this.latTenths = latTenths;
        this.lonTenths = lonTenths;
        this.elevation = elevation;
        this.landPercent = landPercent;
        this.lastFrostP50 = lastFrostP50;
        this.lastFrostP80 = lastFrostP80;
        this.firstFrostP50 = firstFrostP50;
        this.firstFrostP20 = firstFrostP20;
        this.frostYears = frostYears;
        this.index = new HashMap<>(latTenths.length * 2);
        for (int i = 0; i < latTenths.length; i++) {
            index.put(key(latTenths[i], lonTenths[i]), i);
        }
        extent();
    }

    private void extent() {
        for (int i = 0; i < latTenths.length; i++) {
            minLat = Math.min(minLat, latTenths[i]);
            maxLat = Math.max(maxLat, latTenths[i]);
            minLon = Math.min(minLon, lonTenths[i]);
            maxLon = Math.max(maxLon, lonTenths[i]);
        }
    }

    /**
     * Whether a position looks like open sea (#66): inside the grid, yet with no land cell within 0.3 degrees
     * (about 30 km). Wider than the profile's 0.15, because cells keep only at least 25 % land and an island
     * like Capri has none of its own. Only a hint: a small island far from any coast may still look like sea,
     * and outside the grid (beyond Europe and the Mediterranean) nothing is said.
     */
    public boolean looksLikeSea(double latitude, double longitude) {
        int lat = (int) Math.round(latitude * 10);
        int lon = (int) Math.round(longitude * 10);
        if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
            return false;
        }
        for (int i = lat - 3; i <= lat + 3; i++) {
            for (int j = lon - 3; j <= lon + 3; j++) {
                if (index.containsKey(key(i, j)) && Math.hypot(i / 10.0 - latitude, j / 10.0 - longitude) <= 0.3) {
                    return false;
                }
            }
        }
        return true;
    }

    public int size() {
        return latTenths.length;
    }

    /** The cell at a position in the file, for validation and tests. */
    public Cell cell(int i) {
        return new Cell(latTenths[i] / 10.0, lonTenths[i] / 10.0, elevation[i], landPercent[i] / 100.0,
                day(lastFrostP50[i]), day(lastFrostP80[i]), day(firstFrostP50[i]), day(firstFrostP20[i]),
                frostYears[i]);
    }

    /**
     * The nearest land cell within 0.15 degrees of a position, looking at the neighbouring cells and not
     * only the rounded one (#62). Empty when there is none: the position has no climate profile.
     */
    public Optional<Cell> nearest(double latitude, double longitude) {
        int lat = (int) Math.round(latitude * 10);
        int lon = (int) Math.round(longitude * 10);
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = lat - 2; i <= lat + 2; i++) {
            for (int j = lon - 2; j <= lon + 2; j++) {
                Integer candidate = index.get(key(i, j));
                if (candidate == null) {
                    continue;
                }
                double distance = Math.hypot(i / 10.0 - latitude, j / 10.0 - longitude);
                if (distance <= 0.15 && distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best < 0 ? Optional.empty() : Optional.of(cell(best));
    }

    private static Integer day(short value) {
        return value == 0 ? null : (int) value;
    }

    private static int key(int latTenths, int lonTenths) {
        return latTenths * 10_000 + lonTenths;
    }
}
