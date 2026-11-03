package io.github.vincenzofiore.rootology.facts;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** The curated knowledge files are consistent with each other and only use commercial-use sources. */
class KnowledgeFilesTest {

    private final CropCatalog catalog = new CropCatalog();

    /** Rows of a curated CSV as column -> value, comments and header skipped. */
    private static List<Map<String, String>> read(String file) throws IOException {
        List<String> lines = new ClassPathResource("knowledge/" + file).getContentAsString(StandardCharsets.UTF_8)
                .lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        String[] header = lines.getFirst().split(",", -1);
        return lines.subList(1, lines.size()).stream().map(line -> {
            String[] cells = line.split(",", -1);
            assertThat(cells).as(file + ": " + line).hasSize(header.length);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                row.put(header[i], cells[i].trim());
            }
            return row;
        }).toList();
    }

    private Set<String> sourceIds() throws IOException {
        return read("sources.csv").stream().map(r -> r.get("id")).collect(Collectors.toSet());
    }

    @Test
    void everySourceAllowsCommercialUse() throws IOException {
        assertThat(read("sources.csv")).allSatisfy(source -> {
            assertThat(source.get("commercial")).as(source.get("id")).isEqualTo("yes");
            assertThat(source.get("url")).startsWith("https://");
            assertThat(source.get("license")).isNotBlank();
        });
    }

    @Test
    void cropsCarryTheirWikidataAndEcocropRecords() {
        assertThat(catalog.all()).allSatisfy(crop -> {
            assertThat(crop.wikidataId()).as(crop.id()).matches("Q\\d+");
            if (crop.ecocropId() != null) {
                assertThat(crop.ecocropId()).as(crop.id()).matches("\\d+");
            }
        });
    }

    @Test
    void needsReferToKnownCropsAndHaveSensibleRanges() throws IOException {
        Set<String> sources = sourceIds();
        List<Map<String, String>> needs = read("crop-needs.csv");
        assertThat(needs).hasSize((int) catalog.all().stream().filter(c -> c.ecocropId() != null).count());
        assertThat(needs).allSatisfy(row -> {
            String crop = row.get("crop_id");
            assertThat(catalog.byId(crop)).as(crop).isPresent();
            assertThat(row.get("ecocrop_id")).isEqualTo(catalog.byId(crop).orElseThrow().ecocropId());
            assertThat(sources).contains(row.get("source"));
            assertOrdered(crop + " temperature", row.get("temp_abs_min"), row.get("temp_opt_min"),
                    row.get("temp_opt_max"), row.get("temp_abs_max"));
            assertOrdered(crop + " pH", "0", row.get("ph_abs_min"), row.get("ph_opt_min"), row.get("ph_opt_max"),
                    row.get("ph_abs_max"), "14");
        });
    }

    @Test
    void sowingWindowsAreValidAndCoverEveryCrop() throws IOException {
        Set<String> sources = sourceIds();
        Set<String> activities = Set.of("SOW", "SOW_SEEDBED", "TRANSPLANT", "PLANT", "HARVEST");
        List<Map<String, String>> windows = read("sowing-windows.csv");
        assertThat(windows).allSatisfy(row -> {
            assertThat(catalog.byId(row.get("crop_id"))).as(row.get("crop_id")).isPresent();
            assertThat(row.get("zone")).isIn("NORD", "CENTRO", "SUD");
            assertThat(row.get("activity")).isIn(activities);
            assertThat(Integer.parseInt(row.get("from_month"))).isBetween(1, 12);
            assertThat(Integer.parseInt(row.get("to_month"))).isBetween(1, 12);
            assertThat(sources).contains(row.get("source"));
            assertThat(row.get("verified")).isIn("yes", "no");
        });
        for (Crop crop : catalog.all()) {
            Set<String> mine = windows.stream().filter(w -> w.get("crop_id").equals(crop.id()))
                    .map(w -> w.get("activity")).collect(Collectors.toSet());
            assertThat(mine).as(crop.id()).contains("HARVEST")
                    .containsAnyOf("SOW", "SOW_SEEDBED", "PLANT");
        }
    }

    /** Non-empty values must not decrease from left to right. */
    private static void assertOrdered(String what, String... values) {
        double[] numbers = Arrays.stream(values).filter(v -> v != null && !v.isEmpty())
                .mapToDouble(Double::parseDouble).toArray();
        for (int i = 1; i < numbers.length; i++) {
            assertThat(numbers[i]).as(what).isGreaterThanOrEqualTo(numbers[i - 1]);
        }
    }
}
