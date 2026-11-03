package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.HostRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.NeedRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.OrganismRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.PairRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.SourceRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.WindowRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks the curated knowledge before it is loaded: every fact has a known source that allows
 * commercial use, every reference points to a known crop or organism, values are in range, and no
 * pair is both companion and antagonist. Returns every problem found; empty means valid.
 */
public final class KnowledgeValidator {

    static final Set<String> ZONES = Set.of("NORD", "CENTRO", "SUD");
    static final Set<String> ACTIVITIES = Set.of("SOW", "SOW_SEEDBED", "TRANSPLANT", "PLANT", "HARVEST");
    static final Set<String> RELATIONS = Set.of("COMPANION", "ANTAGONIST");
    static final Set<String> EVIDENCE = Set.of("study", "extension", "traditional");
    static final Set<String> KINDS = Set.of("INSECT", "MITE", "MOLLUSC", "DISEASE");

    public static List<String> validate(CuratedKnowledge k) {
        List<String> problems = new ArrayList<>();
        Map<String, SourceRow> sources = unique(k.sources(), SourceRow::id, "source", problems);
        Map<String, Crop> crops = unique(k.crops(), Crop::id, "crop", problems);
        Map<String, OrganismRow> organisms = unique(k.organisms(), OrganismRow::id, "organism", problems);

        sources.values().stream().filter(s -> !s.commercial())
                .forEach(s -> problems.add("source " + s.id() + " does not allow commercial use"));
        sources.values().stream().filter(s -> s.url() == null || !s.url().startsWith("https://"))
                .forEach(s -> problems.add("source " + s.id() + " needs an https URL"));
        sources.values().stream().filter(s -> s.license() == null || s.license().isBlank())
                .forEach(s -> problems.add("source " + s.id() + " has no license"));

        for (Crop c : k.crops()) {
            if (c.wikidataId() == null || !c.wikidataId().matches("Q\\d+")) {
                problems.add("crop " + c.id() + " has no valid Wikidata id");
            }
        }
        for (NeedRow n : k.needs()) {
            String where = "needs of " + n.cropId();
            crop(crops, n.cropId(), where, problems);
            source(sources, n.source(), where, problems);
            ordered(where + ": temperature", problems, n.tempAbsMin(), n.tempOptMin(), n.tempOptMax(), n.tempAbsMax());
            ordered(where + ": pH", problems, 0.0, n.phAbsMin(), n.phOptMin(), n.phOptMax(), n.phAbsMax(), 14.0);
            ordered(where + ": rainfall", problems, n.rainOptMin(), n.rainOptMax());
            ordered(where + ": crop cycle", problems, n.cycleMinDays(), n.cycleMaxDays());
        }
        for (WindowRow w : k.windows()) {
            String where = "window " + w.cropId() + " " + w.activity();
            crop(crops, w.cropId(), where, problems);
            source(sources, w.source(), where, problems);
            oneOf(w.zone(), ZONES, where + ": zone", problems);
            oneOf(w.activity(), ACTIVITIES, where + ": activity", problems);
            month(w.fromMonth(), where, problems);
            month(w.toMonth(), where, problems);
        }
        for (Crop c : k.crops()) {
            Set<String> activities = k.windows().stream().filter(w -> w.cropId().equals(c.id()))
                    .map(WindowRow::activity).collect(Collectors.toSet());
            if (!activities.contains("HARVEST")
                    || activities.stream().noneMatch(a -> Set.of("SOW", "SOW_SEEDBED", "PLANT").contains(a))) {
                problems.add("crop " + c.id() + " needs a sowing or planting window and a harvest window");
            }
        }
        Set<String> declared = new HashSet<>();
        for (PairRow p : k.pairs()) {
            String where = "pair " + p.cropId() + "-" + p.partnerId();
            crop(crops, p.cropId(), where, problems);
            crop(crops, p.partnerId(), where, problems);
            if (Objects.equals(p.cropId(), p.partnerId())) {
                problems.add(where + ": a crop cannot pair with itself");
            }
            oneOf(p.relation(), RELATIONS, where + ": relation", problems);
            oneOf(p.evidence(), EVIDENCE, where + ": evidence", problems);
            p.sources().forEach(s -> source(sources, s, where, problems));
            declared.add(unordered(p) + p.relation());
        }
        for (PairRow p : k.pairs()) {
            String other = "COMPANION".equals(p.relation()) ? "ANTAGONIST" : "COMPANION";
            if (declared.contains(unordered(p) + other)) {
                problems.add("pair " + p.cropId() + "-" + p.partnerId() + " is both companion and antagonist");
            }
        }
        for (OrganismRow o : k.organisms()) {
            if (o.id() == null || !o.id().matches("[0-9A-Z]{5,6}")) {
                problems.add("organism " + o.id() + " is not an EPPO code");
            }
            oneOf(o.kind(), KINDS, "organism " + o.id() + ": kind", problems);
            source(sources, o.source(), "organism " + o.id(), problems);
        }
        for (HostRow h : k.hosts()) {
            String where = "host " + h.organismId() + "-" + h.cropId();
            if (!organisms.containsKey(h.organismId())) {
                problems.add(where + ": unknown organism " + h.organismId());
            }
            crop(crops, h.cropId(), where, problems);
            source(sources, h.source(), where, problems);
            month(h.fromMonth(), where, problems);
            month(h.toMonth(), where, problems);
        }
        Set<String> withHosts = k.hosts().stream().map(HostRow::organismId).collect(Collectors.toSet());
        organisms.keySet().stream().filter(id -> !withHosts.contains(id))
                .forEach(id -> problems.add("organism " + id + " affects no crop"));
        return problems;
    }

    /** The same key whichever crop of the pair comes first. */
    private static String unordered(PairRow p) {
        return p.cropId().compareTo(p.partnerId()) <= 0
                ? p.cropId() + "|" + p.partnerId() + "|"
                : p.partnerId() + "|" + p.cropId() + "|";
    }

    private static <T> Map<String, T> unique(List<T> rows, Function<T, String> id, String what, List<String> problems) {
        Map<String, T> byId = new java.util.LinkedHashMap<>();
        for (T row : rows) {
            if (byId.put(id.apply(row), row) != null) {
                problems.add("duplicate " + what + " id " + id.apply(row));
            }
        }
        return byId;
    }

    private static void crop(Map<String, Crop> crops, String id, String where, List<String> problems) {
        if (!crops.containsKey(id)) {
            problems.add(where + ": unknown crop " + id);
        }
    }

    private static void source(Map<String, SourceRow> sources, String id, String where, List<String> problems) {
        if (!sources.containsKey(id)) {
            problems.add(where + ": unknown source " + id);
        }
    }

    private static void oneOf(String value, Set<String> allowed, String where, List<String> problems) {
        if (!allowed.contains(value)) {
            problems.add(where + " must be one of " + allowed + ", not " + value);
        }
    }

    private static void month(int month, String where, List<String> problems) {
        if (month < 1 || month > 12) {
            problems.add(where + ": month " + month + " is not between 1 and 12");
        }
    }

    /** Non-null values must not decrease from left to right. */
    private static void ordered(String what, List<String> problems, Double... values) {
        Double previous = null;
        for (Double v : values) {
            if (v == null) {
                continue;
            }
            if (previous != null && v < previous) {
                problems.add(what + " is not in order");
                return;
            }
            previous = v;
        }
    }

    private KnowledgeValidator() {
    }
}
