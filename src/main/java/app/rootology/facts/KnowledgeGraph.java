package app.rootology.facts;

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
    public record Affecting(String organismId, String italianName, String englishName, String kind, int fromMonth,
                            int toMonth, List<String> sources, boolean verified, boolean proposed) {
    }

    /** A pest or a disease, by its EPPO code. */
    public record Organism(String id, String italianName, String englishName, String kind) {
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

    /** The pests and diseases of the knowledge base, by Italian name. */
    public List<Organism> organisms() {
        return list("MATCH (o:Kb:Organism) RETURN o.id AS id, o.italianName AS it, o.englishName AS en, o.kind AS kind "
                        + "ORDER BY o.italianName", Map.of(),
                r -> new Organism(r.get("id").asString(), r.get("it").asString(), r.get("en").asString(),
                        r.get("kind").asString()));
    }

    /** The temperature that kills young plants of each crop (ECOCROP), for the crops that have one. */
    public Map<String, Double> killingTemperatures() {
        Map<String, Double> result = new java.util.TreeMap<>();
        list("MATCH (c:Kb:Crop) WHERE c.tempKillEarly IS NOT NULL RETURN c.id AS id, c.tempKillEarly AS t", Map.of(),
                r -> r).forEach(r -> result.put(r.get("id").asString(), r.get("t").asDouble()));
        return result;
    }

    /** The highest temperature each crop stands (ECOCROP's absolute maximum), for the crops that have one. */
    public Map<String, Double> upperTemperatures() {
        Map<String, Double> result = new java.util.TreeMap<>();
        list("MATCH (c:Kb:Crop) WHERE c.tempAbsMax IS NOT NULL RETURN c.id AS id, c.tempAbsMax AS t", Map.of(),
                r -> r).forEach(r -> result.put(r.get("id").asString(), r.get("t").asDouble()));
        return result;
    }

    /**
     * ECOCROP's light optimum of each crop, as `crop-needs.csv` writes it (for example "very bright",
     * "clear skies", "cloudy skies"), for the light rule (#54).
     */
    public Map<String, String> lightNeeds() {
        Map<String, String> result = new java.util.TreeMap<>();
        list("MATCH (c:Kb:Crop) WHERE c.light IS NOT NULL RETURN c.id AS id, c.light AS light", Map.of(),
                r -> r).forEach(r -> result.put(r.get("id").asString(), r.get("light").asString()));
        return result;
    }

    /** The pairs declared from this crop (the reverse direction is never inferred). */
    public List<CropPair> pairsOf(String cropId) {
        return list("MATCH (a:Kb:Crop {id: $id})-[r:COMPANION_OF|ANTAGONIST_OF]->(b:Kb:Crop) WHERE r.spaceId IS NULL "
                        + "RETURN a.id AS a, b.id AS b, type(r) AS t, r.effect AS effect, r.evidence AS evidence, "
                        + "r.sources AS sources ORDER BY b.id", Map.of("id", cropId),
                r -> new CropPair(r.get("a").asString(), r.get("b").asString(),
                        r.get("t").asString().replace("_OF", ""), r.get("effect").asString(),
                        r.get("evidence").asString(), r.get("sources").asList(v -> v.asString())));
    }

    /** Pests and diseases active on this crop in this month (1-12); periods may wrap over the new year. */
    public List<Affecting> affecting(String cropId, int month) {
        return affecting(cropId, month, null);
    }

    /** As {@link #affecting(String, int)}, plus the facts this space added (null: none). */
    public List<Affecting> affecting(String cropId, int month, Long spaceId) {
        return hosts(cropId, spaceId).stream().filter(a -> PestShiftRule.covers(a.fromMonth(), a.toMonth(), month))
                .toList();
    }

    /**
     * The pests and diseases declared on this crop, every month (#162): the rows a rule such as
     * pest-shift-v1 moves before the month is chosen. Periods may wrap over the new year; the facts this
     * space added are included (null: only global ones).
     */
    public List<Affecting> hosts(String cropId, Long spaceId) {
        return list("MATCH (o:Kb:Organism)-[a:AFFECTS]->(:Kb:Crop {id: $id}) "
                        + "WHERE (a.spaceId IS NULL OR a.spaceId = $space) "
                        + "RETURN o.id AS id, o.italianName AS name, o.englishName AS en, o.kind AS kind, "
                        + "a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources + o.sources AS sources, a.verified AS verified, "
                        + "a.spaceId IS NOT NULL AS proposed ORDER BY o.id",
                Map.of("id", cropId, "space", space(spaceId)),
                r -> new Affecting(r.get("id").asString(), r.get("name").asString(), r.get("en").asString(),
                        r.get("kind").asString(),
                        r.get("f").asInt(), r.get("t").asInt(), r.get("sources").asList(v -> v.asString()),
                        r.get("verified").asBoolean(), r.get("proposed").asBoolean()));
    }

    /**
     * Finds a crop or a pest or disease by its id, Italian, English or scientific name, ignoring case.
     */
    public java.util.Optional<Entity> resolve(String name) {
        return resolve(name, null);
    }

    /**
     * As {@link #resolve(String)}, also by the names this space added (null: only global names). A name not
     * found as written is tried in its singular forms ({@link Singulars}): questions say "i pomodori".
     */
    public java.util.Optional<Entity> resolve(String name, Long spaceId) {
        if (name == null || name.isBlank()) {
            return java.util.Optional.empty();
        }
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        java.util.Optional<Entity> exact = resolveAny(List.of(n), spaceId);
        if (exact.isPresent()) {
            return exact;
        }
        java.util.Optional<Entity> singular = resolveAny(List.copyOf(Singulars.of(n)), spaceId);
        String bare = PLANT_FORM.matcher(n).replaceAll("").trim();
        return singular.isPresent() || bare.equals(n) || bare.isEmpty() ? singular : resolve(bare, spaceId);
    }

    /** The plant's form around a crop's name: "fig tree", "lemon plants", "albero di fico", "pianta del limone". */
    static final java.util.regex.Pattern PLANT_FORM = java.util.regex.Pattern.compile(
            "^(?:alber[oi]|piant[ae]) d(?:ell['’]|ello|ella|elle|egli|el|ei|i)\\s*|\\s+(?:trees?|plants?|bush(?:es)?)$");

    /** The first of the names (in their order) that is a crop or an organism; italian when it matched the Italian name. */
    private java.util.Optional<Entity> resolveAny(List<String> names, Long spaceId) {
        if (names.isEmpty()) {
            return java.util.Optional.empty();
        }
        return list("UNWIND range(0, size($names) - 1) AS i WITH i, $names[i] AS n "
                        + "MATCH (e:Kb) WHERE (e:Crop OR e:Organism) AND (toLower(e.id) = n OR toLower(e.italianName) = n "
                        + "OR toLower(e.englishName) = n OR toLower(e.scientificName) = n "
                        + "OR EXISTS { MATCH (a:Kb:Alias)-[:NAME_OF]->(e) WHERE a.name = n AND (a.spaceId IS NULL OR a.spaceId = $space) }) "
                        + "RETURN CASE WHEN e:Crop THEN 'CROP' ELSE 'ORGANISM' END AS kind, e.id AS id, "
                        + "e.italianName AS it, e.englishName AS en, toLower(e.italianName) = n AS italian "
                        + "ORDER BY i LIMIT 1", Map.of("names", names, "space", space(spaceId)),
                r -> new Entity(r.get("kind").asString(), r.get("id").asString(), r.get("it").asString(),
                        r.get("en").asString(), r.get("italian").asBoolean()))
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
        return factsAbout(name, language, null);
    }

    /** As {@link #factsAbout(String, String)}, plus the names and facts this space added (null: none). */
    public List<SourcedFact> factsAbout(String name, String language, Long spaceId) {
        // The subject keeps the exact name of the question, so the decision layer can match it.
        return resolve(name, spaceId).map(e -> language == null ? e : new Entity(e.kind(), e.id(), e.italianName(),
                        e.englishName(), language.equalsIgnoreCase("it")))
                .map(e -> e.kind().equals("CROP") ? cropFacts(e, name.trim(), spaceId) : organismFacts(e, name.trim(), spaceId))
                .orElse(List.of());
    }

    private List<SourcedFact> cropFacts(Entity crop, String self, Long spaceId) {
        List<SourcedFact> facts = new java.util.ArrayList<>();
        list("MATCH (:Kb:Crop {id: $id})-[r:COMPANION_OF|ANTAGONIST_OF]->(b:Kb:Crop) WHERE (r.spaceId IS NULL OR r.spaceId = $space) "
                        + "RETURN type(r) AS t, b.italianName AS it, b.englishName AS en, r.sources AS sources, "
                        + "r.spaceId IS NOT NULL AS proposed ORDER BY b.id",
                Map.of("id", crop.id(), "space", space(spaceId)), r -> new SourcedFact(new Fact(self,
                        r.get("t").asString().equals("COMPANION_OF") ? "companionOf" : "antagonistOf",
                        crop.italian() ? r.get("it").asString() : r.get("en").asString()),
                        r.get("sources").asList(v -> v.asString()), null, r.get("proposed").asBoolean()))
                .forEach(facts::add);
        list("MATCH (o:Kb:Organism)-[a:AFFECTS]->(:Kb:Crop {id: $id}) WHERE (a.spaceId IS NULL OR a.spaceId = $space) "
                        + "RETURN o.italianName AS it, o.englishName AS en, a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources + o.sources AS sources, a.spaceId IS NOT NULL AS proposed ORDER BY o.id",
                Map.of("id", crop.id(), "space", space(spaceId)), r -> r).forEach(r -> {
            String organism = crop.italian() ? r.get("it").asString() : r.get("en").asString();
            List<String> sources = r.get("sources").asList(v -> v.asString());
            boolean proposed = r.get("proposed").asBoolean();
            facts.add(new SourcedFact(new Fact(organism, "affects", self), sources, null, proposed));
            seasons(r.get("f").asInt(), r.get("t").asInt()).forEach(season -> facts.add(
                    new SourcedFact(new Fact(organism, "activeInSeason", season), sources, null, proposed)));
        });
        return dedup(facts);
    }

    /**
     * What can be done against a pest or disease (#140): (pest, controlledBy, method) for each method, with
     * (method, controlKind, kind) and (method, evidence, level) beside it, all with the method's sources. The
     * methods are written in English. Empty when the name is not a known pest or disease.
     */
    public List<SourcedFact> controlFacts(String name, Long spaceId) {
        return resolve(name, spaceId).filter(e -> e.kind().equals("ORGANISM")).map(e -> {
            List<SourcedFact> facts = new java.util.ArrayList<>();
            String self = name.trim();
            list("MATCH (:Kb:Organism {id: $id})-[:CONTROLLED_BY]->(c:Kb:Control) "
                            + "RETURN c.method AS method, c.kind AS kind, c.evidence AS evidence, c.sources AS sources "
                            + "ORDER BY c.kind, c.method",
                    Map.of("id", e.id()), r -> r).forEach(r -> {
                String method = r.get("method").asString();
                List<String> sources = r.get("sources").asList(v -> v.asString());
                facts.add(new SourcedFact(new Fact(self, "controlledBy", method), sources, null, false));
                facts.add(new SourcedFact(new Fact(method, "controlKind", r.get("kind").asString()), sources, null, false));
                facts.add(new SourcedFact(new Fact(method, "evidence", r.get("evidence").asString()), sources, null, false));
            });
            return facts;
        }).orElse(List.of());
    }

    private List<SourcedFact> organismFacts(Entity organism, String self, Long spaceId) {
        List<SourcedFact> facts = new java.util.ArrayList<>();
        list("MATCH (o:Kb:Organism {id: $id})-[a:AFFECTS]->(c:Kb:Crop) WHERE (a.spaceId IS NULL OR a.spaceId = $space) "
                        + "RETURN c.italianName AS it, c.englishName AS en, a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources + o.sources AS sources, a.spaceId IS NOT NULL AS proposed ORDER BY c.id",
                Map.of("id", organism.id(), "space", space(spaceId)), r -> r).forEach(r -> {
            List<String> sources = r.get("sources").asList(v -> v.asString());
            boolean proposed = r.get("proposed").asBoolean();
            facts.add(new SourcedFact(new Fact(self, "affects",
                    organism.italian() ? r.get("it").asString() : r.get("en").asString()), sources, null, proposed));
            seasons(r.get("f").asInt(), r.get("t").asInt()).forEach(season -> facts.add(
                    new SourcedFact(new Fact(self, "activeInSeason", season), sources, null, proposed)));
        });
        return dedup(facts);
    }

    static final List<String> MONTHS = List.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
            "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    /** The predicate a window activity becomes in the facts: one per activity (#112), harvest and pruning too. */
    static String predicate(String activity) {
        return switch (activity) {
            case "HARVEST" -> "harvestIn";
            case "PRUNE" -> "pruneIn";
            default -> SowingActivity.valueOf(activity).predicate();
        };
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
     * anywhere stays curated; otherwise inferred if a rule derived it, and proposed only if every
     * occurrence was proposed.
     */
    static List<SourcedFact> dedup(List<SourcedFact> facts) {
        Map<Fact, java.util.LinkedHashSet<String>> sources = new java.util.LinkedHashMap<>();
        Map<Fact, String> derivedBy = new java.util.HashMap<>();
        java.util.Set<Fact> curated = new java.util.HashSet<>();
        for (SourcedFact f : facts) {
            sources.computeIfAbsent(f.fact(), k -> new java.util.LinkedHashSet<>()).addAll(f.sourceIds());
            if (f.derivedBy() != null) {
                derivedBy.putIfAbsent(f.fact(), f.derivedBy());
            } else if (!f.proposed()) {
                curated.add(f.fact());
            }
        }
        return sources.entrySet().stream().map(e -> {
            Fact fact = e.getKey();
            String rule = curated.contains(fact) ? null : derivedBy.get(fact);
            return new SourcedFact(fact, List.copyOf(e.getValue()), rule, !curated.contains(fact) && rule == null);
        }).toList();
    }

    /** The space whose own facts a read includes; -1 matches no space. */
    private static long space(Long spaceId) {
        return spaceId == null ? -1L : spaceId;
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
