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
 * optimum (`crop-needs.csv`), the antagonist pairs declared in either direction and the companions as declared
 * (`crop-pairs.csv`) and the service plants that control a pest or disease (`pest-controls.csv`). Read once
 * from the same files {@link ClimateWindows} reads, so a rule about crops does not go through Neo4j for facts
 * that are curated.
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
    private final Map<String, Set<String>> companions;
    private final Map<String, Beneficial> beneficials;

    public CropTraits(CropCatalog crops) {
        CuratedKnowledge knowledge = CuratedKnowledgeReader.read(crops);
        Map<String, NeedRow> byCrop = new HashMap<>();
        knowledge.needs().forEach(n -> byCrop.put(n.cropId(), n));
        this.needs = Map.copyOf(byCrop);
        this.antagonists = antagonists(knowledge.pairs());
        this.companions = companions(knowledge.pairs());
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

    /**
     * The crops the curated pairs declare companions of this one, in the direction the file declares: a pair is
     * never reversed, as the knowledge base never infers it (#185).
     */
    public Set<String> companions(String cropId) {
        return companions.getOrDefault(cropId, Set.of());
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

    /** The declared companions of each crop, by crop: a pair is kept as the file declares it. */
    private static Map<String, Set<String>> companions(List<PairRow> pairs) {
        Map<String, Set<String>> result = new HashMap<>();
        pairs.stream().filter(p -> "COMPANION".equals(p.relation())).forEach(p ->
                result.computeIfAbsent(p.cropId(), k -> new HashSet<>()).add(p.partnerId()));
        Map<String, Set<String>> copy = new HashMap<>();
        result.forEach((cropId, partners) -> copy.put(cropId, Set.copyOf(partners)));
        return Map.copyOf(copy);
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