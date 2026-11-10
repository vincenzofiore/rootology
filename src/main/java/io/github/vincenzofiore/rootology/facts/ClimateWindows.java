package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.WindowRow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The sowing, planting and harvest windows of a site (#62): the curated central-Italy windows of Vasto,
 * moved by {@link FrostShiftRule} to the site's own frost dates, plus the curated NORD or SUD rows when
 * the question names that zone. Nothing is read from Neo4j: the calendars are a rule on the curated files.
 *
 * <p>A garden's profile applies to every garden with coordinates, CENTRO included: the curated CENTRO
 * rows are Vasto's, the base of the derivation, not "the garden's zone".
 */
@org.springframework.stereotype.Component
public class ClimateWindows {

    /** A window as a site has it, and whether a rule derived it or the curated file declares it. */
    public record ClimateWindow(WindowRow window, boolean derived) {
    }

    private final List<WindowRow> curated;
    private final Map<String, String> groups;
    private final CropCatalog crops;
    private final KnowledgeGraph knowledge;

    public ClimateWindows(CropCatalog crops, KnowledgeGraph knowledge) {
        this.crops = crops;
        this.knowledge = knowledge;
        CuratedKnowledge read = CuratedKnowledgeReader.read(crops);
        this.curated = read.windows();
        this.groups = crops.all().stream().collect(Collectors.toMap(Crop::id, Crop::frostGroup));
    }

    /** The windows of a crop at a site, curated ones for a named NORD or SUD zone included. */
    public List<ClimateWindow> windows(String cropId, SiteProfile site) {
        return all(site).stream().filter(w -> w.window().cropId().equals(cropId)).toList();
    }

    /**
     * The sowing, planting and harvest months of a crop at a site, as facts (crop, sowIn|plantIn|harvestIn,
     * MONTH) named as the question named it. Derived facts keep the rule in derivedBy and the sources of the
     * site; curated ones keep their own source. Empty when the crop is unknown.
     */
    public List<SourcedFact> windowFacts(String name, SiteProfile site, Long spaceId) {
        if (site == null) {
            return List.of();
        }
        return knowledge.resolve(name, spaceId).map(crop -> KnowledgeGraph.dedup(windows(crop.id(), site).stream()
                .flatMap(w -> KnowledgeGraph.months(w.window().fromMonth(), w.window().toMonth()).stream()
                        .map(m -> new SourcedFact(new Fact(name.trim(), KnowledgeGraph.predicate(w.window().activity()),
                                KnowledgeGraph.MONTHS.get(m - 1)), sources(w, site),
                                w.derived() ? FrostShiftRule.ID : null)))
                .toList())).orElse(List.of());
    }

    /**
     * The crops that can be sown or planted in a month at a site, as facts (crop, sowIn|plantIn, MONTH),
     * named in the given language ("it" for Italian, otherwise English).
     */
    public List<SourcedFact> sowingFacts(String month, SiteProfile site, String language) {
        int m = KnowledgeGraph.MONTHS.indexOf(month) + 1;
        if (m == 0 || site == null) {
            return List.of();
        }
        boolean italian = "it".equalsIgnoreCase(language);
        List<ClimateWindow> all = all(site);
        List<SourcedFact> facts = new ArrayList<>();
        for (Crop crop : crops.all()) {
            for (ClimateWindow w : all) {
                if (!w.window().cropId().equals(crop.id())
                        || !Set.of("SOW", "SOW_SEEDBED", "PLANT", "TRANSPLANT").contains(w.window().activity())
                        || !KnowledgeGraph.months(w.window().fromMonth(), w.window().toMonth()).contains(m)) {
                    continue;
                }
                facts.add(new SourcedFact(new Fact(italian ? crop.italianName() : crop.englishName(),
                        KnowledgeGraph.predicate(w.window().activity()), month), sources(w, site),
                        w.derived() ? FrostShiftRule.ID : null));
            }
        }
        return KnowledgeGraph.dedup(facts);
    }

    /** Every window of the site: the derived ones, then the curated rows of a zone named in the question. */
    private List<ClimateWindow> all(SiteProfile site) {
        if (site == null) {
            return List.of();
        }
        if ("CENTRO".equals(site.zone())) {
            // Vasto itself: the curated rows are its own windows, not an inference.
            return curated.stream().filter(w -> w.zone().equals("CENTRO")).map(w -> new ClimateWindow(w, false))
                    .toList();
        }
        List<WindowRow> declared = site.zone() == null ? List.of()
                : curated.stream().filter(w -> w.zone().equals(site.zone())).toList();
        Set<String> declaredKeys = declared.stream().map(w -> w.cropId() + "|" + w.activity())
                .collect(Collectors.toSet());
        List<ClimateWindow> windows = new ArrayList<>();
        // A curated row of the zone named in the question wins over the derived window of the same crop and activity.
        FrostShiftRule.infer(curated, groups, site).stream()
                .filter(i -> !declaredKeys.contains(i.window().cropId() + "|" + i.window().activity()))
                .forEach(i -> windows.add(new ClimateWindow(i.window(), true)));
        declared.forEach(w -> windows.add(new ClimateWindow(w, false)));
        return windows;
    }

    /** The sources of a window: the site's own for a derived one, the curated file's for a declared one. */
    private static List<String> sources(ClimateWindow w, SiteProfile site) {
        if (!w.derived()) {
            return List.of(w.window().source());
        }
        Set<String> sources = new LinkedHashSet<>();
        sources.add(w.window().source());
        sources.addAll(site.sources());
        return List.copyOf(sources);
    }
}
