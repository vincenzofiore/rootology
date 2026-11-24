package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.ControlRow;
import app.rootology.facts.CuratedKnowledge.HostRow;
import app.rootology.facts.CuratedKnowledge.NeedRow;
import app.rootology.facts.CuratedKnowledge.OrganismRow;
import app.rootology.facts.CuratedKnowledge.PairRow;
import app.rootology.facts.CuratedKnowledge.SourceRow;
import app.rootology.facts.CuratedKnowledge.WindowRow;

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
    static final Set<String> ACTIVITIES = Set.of("SOW", "SOW_SEEDBED", "TRANSPLANT", "PLANT", "HARVEST", "PRUNE");
    static final Set<String> RELATIONS = Set.of("COMPANION", "ANTAGONIST");
    static final Set<String> EVIDENCE = Set.of("study", "extension", "traditional");
    static final Set<String> KINDS = Set.of("INSECT", "MITE", "MOLLUSC", "DISEASE");
    static final Set<String> CONTROL_KINDS = Set.of("PRACTICE", "NATURAL_ENEMY", "SERVICE_PLANT", "SUBSTANCE",
            "MICROORGANISM", "TRAP");
    /** Substances and microorganisms must be allowed in organic production (#140). */
    static final String ORGANIC_LIST = "eu-2021-1165";

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
            oneOf(c.frostGroup(), FrostShiftRule.GROUPS, "crop " + c.id() + ": frost_group", problems);
            oneOf(c.italianArticle(), Crop.ITALIAN_ARTICLES, "crop " + c.id() + ": italian_article", problems);
            oneOf(c.lifecycle(), Crop.LIFECYCLES, "crop " + c.id() + ": lifecycle", problems);
            oneOf(c.use(), Crop.USES, "crop " + c.id() + ": use", problems);
            if (c.frostHardiness() != null || c.hardinessSource() != null) {
                if (!c.perennial()) {
                    problems.add("crop " + c.id() + ": frost_hardiness is for perennials only");
                } else if (c.frostHardiness() == null || c.frostHardiness() < -40 || c.frostHardiness() > 15) {
                    problems.add("crop " + c.id() + ": frost_hardiness must be between -40 and 15 °C");
                }
                source(sources, c.hardinessSource(), "crop " + c.id() + ": hardiness_source", problems);
            }
        }
        for (NeedRow n : k.needs()) {
            String where = "needs of " + n.cropId();
            crop(crops, n.cropId(), where, problems);
            source(sources, n.source(), where, problems);
            ordered(where + ": temperature", problems, n.tempKillEarly(), n.tempAbsMin(), n.tempOptMin(),
                    n.tempOptMax(), n.tempAbsMax());
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
            // A service plant or green manure is not harvested for food (#140): it needs only its sowing window.
            if (activities.stream().noneMatch(a -> Set.of("SOW", "SOW_SEEDBED", "PLANT").contains(a))) {
                problems.add("crop " + c.id() + " needs a sowing or planting window");
            } else if (!c.service() && !activities.contains("HARVEST")) {
                problems.add("crop " + c.id() + " needs a harvest window");
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
        otherNames(k, problems);
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
        controls(k.controls(), sources, crops, organisms, problems);
        return problems;
    }

    private static void controls(List<ControlRow> controls, Map<String, SourceRow> sources, Map<String, Crop> crops,
                                 Map<String, OrganismRow> organisms, List<String> problems) {
        Set<String> seen = new HashSet<>();
        for (ControlRow c : controls) {
            String where = "control " + c.organismId() + " \"" + c.method() + "\"";
            if (!organisms.containsKey(c.organismId())) {
                problems.add(where + ": unknown organism " + c.organismId());
            }
            if (c.method() == null || c.method().isBlank()) {
                problems.add(where + ": no method");
            }
            if (!seen.add(c.organismId() + "|" + c.method())) {
                problems.add(where + ": duplicate");
            }
            oneOf(c.kind(), CONTROL_KINDS, where + ": kind", problems);
            oneOf(c.evidence(), EVIDENCE, where + ": evidence", problems);
            c.sources().forEach(s -> source(sources, s, where, problems));
            if (("SUBSTANCE".equals(c.kind()) || "MICROORGANISM".equals(c.kind()))
                    && !c.sources().contains(ORGANIC_LIST)) {
                problems.add(where + ": a substance or microorganism must cite " + ORGANIC_LIST);
            }
            if ("SERVICE_PLANT".equals(c.kind())) {
                if (c.agent() == null || c.agent().isBlank()) {
                    problems.add(where + ": a service plant names its crop in agent");
                } else {
                    java.util.Arrays.stream(c.agent().split(";")).map(String::trim)
                            .forEach(id -> crop(crops, id, where, problems));
                }
            }
        }
    }

    /**
     * Checks the climate cells file (#62): it must not be empty, and every cell must be a plausible land
     * cell with dates and an elevation the rules can use. Returns every problem found; empty means valid.
     */
    public static List<String> validateCells(ClimateCells cells) {
        List<String> problems = new ArrayList<>();
        if (cells.size() == 0) {
            return List.of("the climate cells file is empty");
        }
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            ClimateCells.Cell cell = cells.cell(i);
            String where = "cell " + cell.label();
            if (cell.latitude() < -90 || cell.latitude() > 90 || cell.longitude() < -180 || cell.longitude() > 180) {
                problems.add(where + ": coordinates are not on Earth");
            }
            if (cell.elevationM() < -9999) {
                problems.add(where + ": elevation " + cell.elevationM() + " is not a DEM value");
            }
            if (cell.landFraction() < 0 || cell.landFraction() > 1) {
                problems.add(where + ": land fraction " + cell.landFraction() + " is not between 0 and 1");
            }
            day(where + ": last frost p50", cell.lastFrostP50(), problems);
            day(where + ": last frost p80", cell.lastFrostP80(), problems);
            day(where + ": first frost p50", cell.firstFrostP50(), problems);
            day(where + ": first frost p20", cell.firstFrostP20(), problems);
            if (cell.frostYears() < 0 || cell.frostYears() > 30) {
                problems.add(where + ": frost years " + cell.frostYears() + " is not between 0 and 30");
            }
            long key = Math.round(cell.latitude() * 10) * 10_000L + Math.round(cell.longitude() * 10);
            if (!seen.add(key)) {
                problems.add(where + ": duplicate cell");
            }
        }
        return problems;
    }

    private static void day(String where, Integer value, List<String> problems) {
        if (value != null && (value < 1 || value > 366)) {
            problems.add(where + " day " + value + " is not between 1 and 366");
        }
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

    /**
     * Common names of a pest (#140), in lower case as questions are matched: each says one pest only, so none
     * may be the name of a crop, of another pest or another pest's common name.
     */
    private static void otherNames(CuratedKnowledge k, List<String> problems) {
        Map<String, String> taken = new java.util.HashMap<>();
        k.crops().forEach(c -> java.util.stream.Stream.of(c.id(), c.italianName(), c.englishName())
                .forEach(n -> taken.put(n.toLowerCase(java.util.Locale.ROOT), "crop " + c.id())));
        k.organisms().forEach(o -> java.util.stream.Stream.of(o.id(), o.italianName(), o.englishName(), o.scientificName())
                .forEach(n -> taken.put(n.toLowerCase(java.util.Locale.ROOT), "organism " + o.id())));
        for (OrganismRow o : k.organisms()) {
            for (String name : o.otherNames()) {
                String owner = taken.putIfAbsent(name, "organism " + o.id());
                if (name.isBlank() || !name.equals(name.toLowerCase(java.util.Locale.ROOT)) || name.contains(",")) {
                    problems.add("organism " + o.id() + ": other name '" + name + "' is not a lower-case name");
                } else if (owner != null) {
                    problems.add("organism " + o.id() + ": other name '" + name + "' is already the name of " + owner);
                }
            }
        }
    }
}
