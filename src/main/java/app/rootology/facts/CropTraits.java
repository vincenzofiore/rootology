package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.ControlRow;
import app.rootology.facts.CuratedKnowledge.NeedRow;
import app.rootology.facts.CuratedKnowledge.PairRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the curated files say about one crop besides its calendar (#141): FAO ECOCROP's cycle and light
 * optimum (`crop-needs.csv`), the antagonist pairs declared in either direction (`crop-pairs.csv`) and the
 * service plants that control a pest or disease (`pest-controls.csv`). Read once from the same files
 * {@link ClimateWindows} reads, so a rule about crops does not go through Neo4j for facts that are curated.
 */
@Component
public class CropTraits {

    /** A crop named as the agent of at least one SERVICE_PLANT row: its sources, and whether every row is traditional. */
    public record Beneficial(List<String> sources, boolean traditionalOnly) {

        public Beneficial {
            sources = List.copyOf(sources);
        }
    }

    private final Map<String, NeedRow> needs;
    private final Set<String> antagonists;
    private final Map<String, Beneficial> beneficials;

    public CropTraits(CropCatalog crops) {
        CuratedKnowledge knowledge = CuratedKnowledgeReader.read(crops);
        Map<String, NeedRow> byCrop = new HashMap<>();
        knowledge.needs().forEach(n -> byCrop.put(n.cropId(), n));
        this.needs = Map.copyOf(byCrop);
        this.antagonists = antagonists(knowledge.pairs());
        this.beneficials = beneficials(knowledge.controls());
    }

    /** ECOCROP's shortest cycle in days, empty when the crop has no ECOCROP record or no value. */
    public Optional<Double> cycleMinDays(String cropId) {
        return Optional.ofNullable(needs.get(cropId)).map(NeedRow::cycleMinDays);
    }

    /** ECOCROP's light optimum as written ("very bright", "clear skies", ...), empty when not known. */
    public Optional<String> lightOptimum(String cropId) {
        return Optional.ofNullable(needs.get(cropId)).map(NeedRow::light).filter(light -> !light.isBlank());
    }

    /** True when the curated pairs declare these two crops antagonists, in either direction. */
    public boolean antagonists(String cropId, String otherCropId) {
        return antagonists.contains(key(cropId, otherCropId));
    }

    /** The service-plant rows that name this crop as their agent; empty when there are none. */
    public Optional<Beneficial> beneficial(String cropId) {
        return Optional.ofNullable(beneficials.get(cropId));
    }

    private static Set<String> antagonists(List<PairRow> pairs) {
        Set<String> result = new HashSet<>();
        pairs.stream().filter(p -> "ANTAGONIST".equals(p.relation())).forEach(p -> {
            result.add(key(p.cropId(), p.partnerId()));
            result.add(key(p.partnerId(), p.cropId()));
        });
        return Set.copyOf(result);
    }

    /** The sources of every SERVICE_PLANT row that names the crop, and whether all of them are traditional. */
    private static Map<String, Beneficial> beneficials(List<ControlRow> controls) {
        Map<String, Set<String>> sources = new HashMap<>();
        Set<String> traditional = new HashSet<>();
        Set<String> withEvidence = new HashSet<>();
        for (ControlRow control : controls) {
            if (!"SERVICE_PLANT".equals(control.kind()) || control.agent() == null) {
                continue;
            }
            for (String agent : control.agent().split(";")) {
                String cropId = agent.trim();
                if (cropId.isEmpty()) {
                    continue;
                }
                sources.computeIfAbsent(cropId, k -> new LinkedHashSet<>()).addAll(control.sources());
                if ("traditional".equals(control.evidence())) {
                    traditional.add(cropId);
                } else {
                    withEvidence.add(cropId);
                }
            }
        }
        Map<String, Beneficial> result = new HashMap<>();
        sources.forEach((cropId, ids) -> result.put(cropId,
                new Beneficial(new ArrayList<>(ids), traditional.contains(cropId) && !withEvidence.contains(cropId))));
        return Map.copyOf(result);
    }

    private static String key(String first, String second) {
        return (first + "|" + second).toLowerCase(Locale.ROOT);
    }
}