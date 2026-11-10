package io.github.vincenzofiore.rootology.facts;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The crops of the knowledge base, from the curated file `knowledge/crops-v1.csv`
 * (approved list, docs/crops-v1.md), with their Wikidata and FAO ECOCROP records.
 */
@Component
public class CropCatalog {

    static final String RESOURCE = "knowledge/crops-v1.csv";

    private final Map<String, Crop> crops;

    public CropCatalog() {
        this.crops = load();
    }

    /** All crops, by Italian name. */
    public List<Crop> all() {
        return crops.values().stream().sorted(Comparator.comparing(Crop::italianName)).toList();
    }

    public Optional<Crop> byId(String id) {
        return Optional.ofNullable(crops.get(id));
    }

    private static Map<String, Crop> load() {
        Map<String, Crop> result = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
            for (String line : lines.subList(1, lines.size())) {
                String[] f = line.split(",", -1);
                if (f.length != 9) {
                    throw new IllegalStateException("Malformed line in " + RESOURCE + ": " + line);
                }
                Crop crop = new Crop(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), f[4].trim(),
                        f[5].trim(), f[6].isBlank() ? null : f[6].trim(), f[8].trim());
                if (result.put(crop.id(), crop) != null) {
                    throw new IllegalStateException("Duplicate crop id in " + RESOURCE + ": " + crop.id());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }
}
