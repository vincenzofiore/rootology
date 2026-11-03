package io.github.vincenzofiore.rootology.facts;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the curated knowledge base in Neo4j (label `Kb`), never the quality suite's dataset. */
@Component
public class KnowledgeGraph {

    /** A declared pair as seen from one crop. */
    public record CropPair(String cropId, String partnerId, String relation, String effect, String evidence,
                           List<String> sources) {
    }

    /** A pest or disease that affects a crop in a given month. */
    public record Affecting(String organismId, String italianName, String kind, int fromMonth, int toMonth,
                            List<String> sources, boolean verified) {
    }

    /** A sowing, planting or harvest window of a crop in a zone, curated or derived by a rule. */
    public record Window(String cropId, String zone, String activity, int fromMonth, int toMonth, String note,
                         String provenance, String derivedBy, List<String> sources, boolean verified) {
    }

    /** A crop or an organism the knowledge base knows, and the language its name was given in. */
    public record Entity(String kind, String id, String italianName, String englishName, boolean italian) {

        public String name() {
            return italian ? italianName : englishName;
        }
    }

    /** Months of each season, as used by the questions (northern hemisphere). */
    static final Map<String, Set<Integer>> SEASONS = Map.of(
            "SPRING", Set.of(3, 4, 5),
            "SUMMER", Set.of(6, 7, 8),
            "AUTUMN", Set.of(9, 10, 11),
            "WINTER", Set.of(12, 1, 2));

    private static final Set<String> LABELS = Set.of("Crop", "Family", "Organism", "Zone", "Source");

    private final Driver driver;

    public KnowledgeGraph(Driver driver) {
        this.driver = driver;
    }

    public long count(String label) {
        if (!LABELS.contains(label)) {
            throw new IllegalArgumentException("Unknown knowledge base label " + label);
        }
        return single("MATCH (n:Kb:" + label + ") RETURN count(n) AS n", Map.of()).get("n").asLong();
    }

    /** The pairs declared from this crop (the reverse direction is never inferred). */
    public List<CropPair> pairsOf(String cropId) {
        return list("MATCH (a:Kb:Crop {id: $id})-[r:COMPANION_OF|ANTAGONIST_OF]->(b:Kb:Crop) "
                        + "RETURN a.id AS a, b.id AS b, type(r) AS t, r.effect AS effect, r.evidence AS evidence, "
                        + "r.sources AS sources ORDER BY b.id", Map.of("id", cropId),
                r -> new CropPair(r.get("a").asString(), r.get("b").asString(),
                        r.get("t").asString().replace("_OF", ""), r.get("effect").asString(),
                        r.get("evidence").asString(), r.get("sources").asList(v -> v.asString())));
    }

    /** Pests and diseases active on this crop in this month (1-12); periods may wrap over the new year. */
    public List<Affecting> affecting(String cropId, int month) {
        return list("MATCH (o:Kb:Organism)-[a:AFFECTS]->(:Kb:Crop {id: $id}) "
                        + "WHERE (a.fromMonth <= a.toMonth AND $m >= a.fromMonth AND $m <= a.toMonth) "
                        + "   OR (a.fromMonth > a.toMonth AND ($m >= a.fromMonth OR $m <= a.toMonth)) "
                        + "RETURN o.id AS id, o.italianName AS name, o.kind AS kind, a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources AS sources, a.verified AS verified ORDER BY o.id",
                Map.of("id", cropId, "m", month),
                r -> new Affecting(r.get("id").asString(), r.get("name").asString(), r.get("kind").asString(),
                        r.get("f").asInt(), r.get("t").asInt(), r.get("sources").asList(v -> v.asString()),
                        r.get("verified").asBoolean()));
    }

    /** The windows of a crop in a zone, curated ones first. */
    public List<Window> windows(String cropId, String zone) {
        return list("MATCH (c:Kb:Crop {id: $id})-[w:WINDOW]->(z:Kb:Zone {id: $zone}) "
                        + "RETURN w ORDER BY w.provenance, w.activity, w.fromMonth", Map.of("id", cropId, "zone", zone),
                r -> {
                    var w = r.get("w").asRelationship();
                    return new Window(cropId, zone, w.get("activity").asString(), w.get("fromMonth").asInt(),
                            w.get("toMonth").asInt(), w.get("note").asString(""), w.get("provenance").asString(),
                            w.get("derivedBy").isNull() ? null : w.get("derivedBy").asString(),
                            w.get("sources").asList(v -> v.asString()), w.get("verified").asBoolean());
                });
    }

    /**
     * Finds a crop or a pest or disease by its id, Italian, English or scientific name, ignoring case.
     */
    public java.util.Optional<Entity> resolve(String name) {
        if (name == null || name.isBlank()) {
            return java.util.Optional.empty();
        }
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        return list("MATCH (e:Kb) WHERE (e:Crop OR e:Organism) AND (toLower(e.id) = $n OR toLower(e.italianName) = $n "
                        + "OR toLower(e.englishName) = $n OR toLower(e.scientificName) = $n) "
                        + "RETURN CASE WHEN e:Crop THEN 'CROP' ELSE 'ORGANISM' END AS kind, e.id AS id, "
                        + "e.italianName AS it, e.englishName AS en LIMIT 1", Map.of("n", n),
                r -> new Entity(r.get("kind").asString(), r.get("id").asString(), r.get("it").asString(),
                        r.get("en").asString(), r.get("it").asString().equalsIgnoreCase(name.trim())))
                .stream().findFirst();
    }

    /**
     * The facts about a crop or an organism, named in the language the name was given in, each with
     * its sources: for a crop, its declared companions and antagonists (never the reverse), the pests
     * and diseases that affect it and the seasons they are active on it; for an organism, the crops
     * it affects and the seasons it is active. Empty when the name is unknown.
     */
    public List<SourcedFact> factsAbout(String name) {
        return factsAbout(name, null);
    }

    /**
     * As {@link #factsAbout(String)}, with the other names in the given language ("it" for Italian,
     * anything else English); when null, the language the subject was named in.
     */
    public List<SourcedFact> factsAbout(String name, String language) {
        // The subject keeps the exact name of the question, so the decision layer can match it.
        return resolve(name).map(e -> language == null ? e : new Entity(e.kind(), e.id(), e.italianName(),
                        e.englishName(), language.equalsIgnoreCase("it")))
                .map(e -> e.kind().equals("CROP") ? cropFacts(e, name.trim()) : organismFacts(e, name.trim()))
                .orElse(List.of());
    }

    private List<SourcedFact> cropFacts(Entity crop, String self) {
        List<SourcedFact> facts = new java.util.ArrayList<>();
        list("MATCH (:Kb:Crop {id: $id})-[r:COMPANION_OF|ANTAGONIST_OF]->(b:Kb:Crop) "
                        + "RETURN type(r) AS t, b.italianName AS it, b.englishName AS en, r.sources AS sources ORDER BY b.id",
                Map.of("id", crop.id()), r -> new SourcedFact(new Fact(self,
                        r.get("t").asString().equals("COMPANION_OF") ? "companionOf" : "antagonistOf",
                        crop.italian() ? r.get("it").asString() : r.get("en").asString()),
                        r.get("sources").asList(v -> v.asString()))).forEach(facts::add);
        list("MATCH (o:Kb:Organism)-[a:AFFECTS]->(:Kb:Crop {id: $id}) "
                        + "RETURN o.italianName AS it, o.englishName AS en, a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources + o.sources AS sources ORDER BY o.id",
                Map.of("id", crop.id()), r -> r).forEach(r -> {
            String organism = crop.italian() ? r.get("it").asString() : r.get("en").asString();
            List<String> sources = r.get("sources").asList(v -> v.asString());
            facts.add(new SourcedFact(new Fact(organism, "affects", self), sources));
            seasons(r.get("f").asInt(), r.get("t").asInt())
                    .forEach(season -> facts.add(new SourcedFact(new Fact(organism, "activeInSeason", season), sources)));
        });
        return dedup(facts);
    }

    private List<SourcedFact> organismFacts(Entity organism, String self) {
        List<SourcedFact> facts = new java.util.ArrayList<>();
        list("MATCH (o:Kb:Organism {id: $id})-[a:AFFECTS]->(c:Kb:Crop) "
                        + "RETURN c.italianName AS it, c.englishName AS en, a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources + o.sources AS sources ORDER BY c.id",
                Map.of("id", organism.id()), r -> r).forEach(r -> {
            List<String> sources = r.get("sources").asList(v -> v.asString());
            facts.add(new SourcedFact(new Fact(self, "affects",
                    organism.italian() ? r.get("it").asString() : r.get("en").asString()), sources));
            seasons(r.get("f").asInt(), r.get("t").asInt())
                    .forEach(season -> facts.add(new SourcedFact(new Fact(self, "activeInSeason", season), sources)));
        });
        return dedup(facts);
    }

    static final List<String> MONTHS = List.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
            "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    /** The predicate a window activity becomes in the facts. */
    static String predicate(String activity) {
        return switch (activity) {
            case "SOW", "SOW_SEEDBED" -> "sowIn";
            case "PLANT", "TRANSPLANT" -> "plantIn";
            default -> "harvestIn";
        };
    }

    /**
     * The sowing, planting and harvest months of a crop in a zone, as facts (crop, sowIn|plantIn|harvestIn,
     * MONTH). Windows derived by a rule keep its name in derivedBy. Empty when the crop is unknown.
     */
    public List<SourcedFact> windowFacts(String name, String zone) {
        return resolve(name).map(crop -> dedup(windows(crop.id(), zone).stream()
                .flatMap(w -> months(w.fromMonth(), w.toMonth()).stream().map(m -> new SourcedFact(
                        new Fact(name.trim(), predicate(w.activity()), MONTHS.get(m - 1)), w.sources(), w.derivedBy())))
                .toList())).orElse(List.of());
    }

    /**
     * The crops that can be sown or planted in a month (JANUARY to DECEMBER) in a zone, as facts
     * (crop, sowIn|plantIn, MONTH), named in the given language ("it" for Italian, otherwise English).
     */
    public List<SourcedFact> sowingFacts(String month, String zone, String language) {
        int m = MONTHS.indexOf(month) + 1;
        if (m == 0) {
            return List.of();
        }
        boolean italian = "it".equalsIgnoreCase(language);
        return dedup(list("MATCH (c:Kb:Crop)-[w:WINDOW]->(:Kb:Zone {id: $zone}) "
                        + "WHERE w.activity IN ['SOW', 'SOW_SEEDBED', 'PLANT', 'TRANSPLANT'] "
                        + "AND ((w.fromMonth <= w.toMonth AND $m >= w.fromMonth AND $m <= w.toMonth) "
                        + "  OR (w.fromMonth > w.toMonth AND ($m >= w.fromMonth OR $m <= w.toMonth))) "
                        + "RETURN c.italianName AS it, c.englishName AS en, w.activity AS activity, w.sources AS sources, "
                        + "w.derivedBy AS derivedBy ORDER BY c.id",
                Map.of("zone", zone, "m", m), r -> new SourcedFact(
                        new Fact(italian ? r.get("it").asString() : r.get("en").asString(),
                                predicate(r.get("activity").asString()), month),
                        r.get("sources").asList(v -> v.asString()),
                        r.get("derivedBy").isNull() ? null : r.get("derivedBy").asString())));
    }

    /** The months of a window (1-12, wrapping over the year), in order. */
    static List<Integer> months(int from, int to) {
        List<Integer> months = new java.util.ArrayList<>();
        for (int m = from; ; m = m % 12 + 1) {
            months.add(m);
            if (m == to) {
                return months;
            }
        }
    }

    /** The seasons a month window (1-12, wrapping over the year) touches, in calendar order. */
    static List<String> seasons(int from, int to) {
        Set<Integer> months = new java.util.HashSet<>();
        for (int m = from; ; m = m % 12 + 1) {
            months.add(m);
            if (m == to) {
                break;
            }
        }
        return java.util.stream.Stream.of("WINTER", "SPRING", "SUMMER", "AUTUMN")
                .filter(s -> SEASONS.get(s).stream().anyMatch(months::contains)).toList();
    }

    /**
     * One fact per triple, with the union of its sources, in first-seen order. A fact that is curated
     * anywhere stays curated; it is inferred only if every occurrence was inferred.
     */
    private static List<SourcedFact> dedup(List<SourcedFact> facts) {
        Map<Fact, java.util.LinkedHashSet<String>> sources = new java.util.LinkedHashMap<>();
        Map<Fact, String> derivedBy = new java.util.HashMap<>();
        java.util.Set<Fact> curated = new java.util.HashSet<>();
        for (SourcedFact f : facts) {
            sources.computeIfAbsent(f.fact(), k -> new java.util.LinkedHashSet<>()).addAll(f.sourceIds());
            if (f.derivedBy() == null) {
                curated.add(f.fact());
            } else {
                derivedBy.putIfAbsent(f.fact(), f.derivedBy());
            }
        }
        return sources.entrySet().stream().map(e -> new SourcedFact(e.getKey(), List.copyOf(e.getValue()),
                curated.contains(e.getKey()) ? null : derivedBy.get(e.getKey()))).toList();
    }

    private org.neo4j.driver.Record single(String query, Map<String, Object> params) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, params).single());
        }
    }

    private <T> List<T> list(String query, Map<String, Object> params,
                             java.util.function.Function<org.neo4j.driver.Record, T> mapper) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, params).list(mapper::apply));
        }
    }
}
