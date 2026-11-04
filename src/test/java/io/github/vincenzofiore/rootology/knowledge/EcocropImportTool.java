package io.github.vincenzofiore.rootology.knowledge;

import io.github.vincenzofiore.rootology.facts.Crop;
import io.github.vincenzofiore.rootology.facts.CropCatalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import tool, run by hand (#16): reads each crop's data sheet from the official FAO ECOCROP tool
 * (CC BY 4.0) and writes `knowledge/crop-needs.csv`, to be reviewed before committing. One request
 * per second. Run with: ./mvnw test -Plive -Dtest=EcocropImportTool
 */
@Tag("live-api")
class EcocropImportTool {

    private static final String SHEET = "https://ecocrop.apps.fao.org/ecocrop/srv/en/dataSheet?id=";
    private static final Path OUTPUT = Path.of("src/main/resources/knowledge/crop-needs.csv");
    private static final String HEADER = "crop_id,ecocrop_id,temp_opt_min,temp_opt_max,temp_abs_min,temp_abs_max,"
            + "rain_opt_min,rain_opt_max,ph_opt_min,ph_opt_max,ph_abs_min,ph_abs_max,"
            + "cycle_min_days,cycle_max_days,soil_texture_opt,soil_drainage_opt,light_opt,temp_kill_early,source";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    @Test
    void importNeeds() throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Environmental needs of the crops, from FAO ECOCROP (CC BY 4.0), retrieved by EcocropImportTool.");
        lines.add("# Temperatures in degrees C, rainfall in mm per year; empty when ECOCROP gives no value.");
        lines.add("# temp_kill_early: ECOCROP's killing temperature during early growth (frost tolerance of young plants).");
        lines.add(HEADER);
        for (Crop crop : new CropCatalog().all().stream().sorted(Comparator.comparing(Crop::id)).toList()) {
            if (crop.ecocropId() == null) {
                continue;
            }
            Map<String, List<String>> sheet = rows(fetch(crop.ecocropId()));
            List<String> temp = numbers(sheet, "Temperat. requir.");
            List<String> rain = numbers(sheet, "Rainfall (annual)");
            List<String> ph = numbers(sheet, "Soil PH");
            List<String> cycle = numbers(sheet, "Crop cycle");
            if (cycle.stream().allMatch(c -> c.equals("0"))) {
                cycle = List.of(); // ECOCROP shows 0 when it has no value
            }
            lines.add(String.join(",", crop.id(), crop.ecocropId(),
                    at(temp, 0), at(temp, 1), at(temp, 2), at(temp, 3),
                    at(rain, 0), at(rain, 1),
                    at(ph, 0), at(ph, 1), at(ph, 2), at(ph, 3),
                    at(cycle, 0), at(cycle, 1),
                    text(sheet, "Soil texture"), text(sheet, "Soil drainage"), text(sheet, "Light intensity"),
                    at(numbers(sheet, "Killing temp. early growth"), 0), "fao-ecocrop"));
        }
        Files.write(OUTPUT, lines);
        System.out.println("Wrote " + (lines.size() - 4) + " crops to " + OUTPUT);
    }

    private String fetch(String id) throws IOException, InterruptedException {
        Thread.sleep(1000);
        HttpRequest request = HttpRequest.newBuilder(URI.create(SHEET + id))
                .header("User-Agent", "Rootology/0.1 (https://github.com/vincenzofiore/rootology)")
                .timeout(Duration.ofSeconds(60)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** The data sheet's table rows as label -> cells, from the page text. */
    static Map<String, List<String>> rows(String html) {
        String text = html.replaceAll("(?s)<script.*?</script>", "")
                .replaceAll("(?i)<(br|/tr|/p|/h\\d|/div)[^>]*>", "\n")
                .replaceAll("(?i)</t[dh]>", "|")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        Map<String, List<String>> rows = new HashMap<>();
        for (String line : text.split("\n")) {
            List<String> cells = Arrays.stream(line.split("\\|")).map(c -> c.replaceAll("\\s+", " ").trim()).toList();
            for (int i = 0; i < cells.size(); i++) {
                if (!cells.get(i).isEmpty() && !rows.containsKey(cells.get(i))) {
                    rows.put(cells.get(i), cells.subList(i + 1, cells.size()));
                }
            }
        }
        return rows;
    }

    private static final Pattern NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    /** The leading numeric cells after a label (optimal min, max, absolute min, max). */
    private static List<String> numbers(Map<String, List<String>> sheet, String label) {
        List<String> out = new ArrayList<>();
        for (String cell : sheet.getOrDefault(label, List.of())) {
            Matcher m = NUMBER.matcher(cell);
            if (m.matches()) {
                out.add(cell);
            } else if (cell.equals("-") || cell.isEmpty()) {
                out.add("");
            } else {
                break;
            }
        }
        return out;
    }

    /** The first text cell after a label (the optimal column), commas replaced so the CSV stays simple. */
    private static String text(Map<String, List<String>> sheet, String label) {
        return sheet.getOrDefault(label, List.of()).stream().filter(c -> !c.isEmpty()).findFirst()
                .map(c -> c.replace(",", ";").trim()).orElse("");
    }

    private static String at(List<String> values, int i) {
        return i < values.size() ? values.get(i) : "";
    }
}
