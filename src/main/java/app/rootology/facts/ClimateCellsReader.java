package app.rootology.facts;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads `knowledge/climate-cells-v1.csv.gz` (#62): comma-separated, `#` comment lines, a header row,
 * no quoting. The file is gzipped because it holds about 128,000 cells; it is read once at startup.
 */
final class ClimateCellsReader {

    static final String RESOURCE = "knowledge/climate-cells-v1.csv.gz";

    static ClimateCells read() {
        List<String> lines = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ClassPathResource(RESOURCE).getInputStream()), StandardCharsets.UTF_8))) {
            reader.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).forEach(lines::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " is empty");
        }
        List<String> rows = lines.subList(1, lines.size());
        int[] lat = new int[rows.size()];
        int[] lon = new int[rows.size()];
        short[] elevation = new short[rows.size()];
        byte[] land = new byte[rows.size()];
        short[] lastP50 = new short[rows.size()];
        short[] lastP80 = new short[rows.size()];
        short[] firstP50 = new short[rows.size()];
        short[] firstP20 = new short[rows.size()];
        byte[] years = new byte[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] cells = rows.get(i).split(",", -1);
            if (cells.length != 11) {
                throw new IllegalStateException(RESOURCE + ": expected 11 cells at row " + (i + 2) + ": "
                        + rows.get(i));
            }
            try {
                lat[i] = (int) Math.round(Double.parseDouble(cells[0]) * 10);
                lon[i] = (int) Math.round(Double.parseDouble(cells[1]) * 10);
                elevation[i] = (short) Integer.parseInt(cells[2]);
                land[i] = (byte) Math.round(Double.parseDouble(cells[3]) * 100);
                lastP50[i] = (short) day(cells[6]);
                lastP80[i] = (short) day(cells[7]);
                firstP50[i] = (short) day(cells[8]);
                firstP20[i] = (short) day(cells[9]);
                years[i] = (byte) Integer.parseInt(cells[10]);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(RESOURCE + ": bad number at row " + (i + 2) + ": " + rows.get(i), e);
            }
        }
        return new ClimateCells(lat, lon, elevation, land, lastP50, lastP80, firstP50, firstP20, years);
    }

    /** "MM-DD" as a day of the year (2025, not a leap year); 0 when the cell has no such frost. */
    private static int day(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String[] parts = value.split("-");
        if (parts.length != 2) {
            throw new IllegalStateException("Not a month and day: " + value);
        }
        return LocalDate.of(2025, Integer.parseInt(parts[0]), Integer.parseInt(parts[1])).getDayOfYear();
    }

    private ClimateCellsReader() {
    }
}
