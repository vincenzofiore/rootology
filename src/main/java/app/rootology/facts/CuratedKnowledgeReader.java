package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.HostRow;
import app.rootology.facts.CuratedKnowledge.NeedRow;
import app.rootology.facts.CuratedKnowledge.OrganismRow;
import app.rootology.facts.CuratedKnowledge.PairRow;
import app.rootology.facts.CuratedKnowledge.SourceRow;
import app.rootology.facts.CuratedKnowledge.WindowRow;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads the curated CSV files: comma-separated, `#` comment lines, a header row, no quoting. */
final class CuratedKnowledgeReader {

    static final String DIRECTORY = "knowledge/";

    static CuratedKnowledge read(CropCatalog catalog) {
        return new CuratedKnowledge(
                rows("sources.csv").stream().map(r -> new SourceRow(r.get("id"), r.get("title"), r.get("url"),
                        r.get("license"), "yes".equals(r.get("commercial")), r.get("retrieved_on"))).toList(),
                catalog.all(),
                rows("crop-needs.csv").stream().map(r -> new NeedRow(r.get("crop_id"), r.get("ecocrop_id"),
                        number(r, "temp_opt_min"), number(r, "temp_opt_max"), number(r, "temp_abs_min"),
                        number(r, "temp_abs_max"), number(r, "rain_opt_min"), number(r, "rain_opt_max"),
                        number(r, "ph_opt_min"), number(r, "ph_opt_max"), number(r, "ph_abs_min"),
                        number(r, "ph_abs_max"), number(r, "cycle_min_days"), number(r, "cycle_max_days"),
                        r.get("soil_texture_opt"), r.get("soil_drainage_opt"), r.get("light_opt"),
                        number(r, "temp_kill_early"), r.get("source"))).toList(),
                rows("sowing-windows.csv").stream().map(r -> new WindowRow(r.get("crop_id"), r.get("zone"),
                        r.get("activity"), month(r, "from_month"), month(r, "to_month"), r.get("note"),
                        r.get("source"), "yes".equals(r.get("verified")))).toList(),
                rows("crop-pairs.csv").stream().map(r -> new PairRow(r.get("crop_id"), r.get("partner_id"),
                        r.get("relation"), r.get("effect"), r.get("evidence"),
                        Arrays.asList(r.get("sources").split(";")))).toList(),
                rows("pests.csv").stream().map(r -> new OrganismRow(r.get("id"), r.get("italian"),
                        r.get("english"), r.get("scientific"), r.get("kind"), r.get("source"))).toList(),
                rows("pest-hosts.csv").stream().map(r -> new HostRow(r.get("pest_id"), r.get("crop_id"),
                        month(r, "from_month"), month(r, "to_month"), r.get("note"), r.get("source"),
                        "yes".equals(r.get("verified")))).toList());
    }

    /** Rows as column -> trimmed value. A row with the wrong number of cells is an error. */
    static List<Map<String, String>> rows(String file) {
        try {
            List<String> lines = new ClassPathResource(DIRECTORY + file).getContentAsString(StandardCharsets.UTF_8)
                    .lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
            String[] header = lines.getFirst().split(",", -1);
            return lines.subList(1, lines.size()).stream().map(line -> {
                String[] cells = line.split(",", -1);
                if (cells.length != header.length) {
                    throw new IllegalStateException(file + ": expected " + header.length + " cells: " + line);
                }
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.length; i++) {
                    row.put(header[i].trim(), cells[i].trim());
                }
                return row;
            }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Double number(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null || value.isEmpty() ? null : Double.valueOf(value);
    }

    /** A month that is not a number becomes 0, which the validator reports. */
    private static int month(Map<String, String> row, String column) {
        try {
            return Integer.parseInt(row.get(column));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private CuratedKnowledgeReader() {
    }
}
