# Rootology

Personal, exploratory Java project. It started as a comparison between a formal ontology with a symbolic reasoner (Apache Jena) and a prose skill file consumed by an LLM, on a small closed companion-planting garden domain. It is now being extended with a layered agentic pipeline (Embabel + Claude + Jev) as a third contestant on the same questions.

## Working conventions

- Talk to the owner (Vincenzo) in Italian. Write all code, comments, commit messages, README and docs in English.
- Toy project: keep it small, readable and easy to inspect. No production hardening, no speculative abstractions.
- Never commit or push on your own; leave git operations to the owner. Existing commit messages are short, lowercase and imperative.
- Do not change the closed dataset or the existing experiment records unless explicitly asked. They are the experiment's ground truth.
- This domain is deliberately unrelated to the owner's day job. Do not import concepts or code from any professional project.

## Current state (already built)

- Stack (from `pom.xml`): Spring Boot 4.1.0, Java 25, Maven (use `./mvnw`), Spring Modulith 2.1.0, Apache Jena 6.2.0 (`apache-jena-libs`), Lombok. README says Java 17+, so the README is stale on this point.
- The pom also carries Initializr leftovers not used by the core experiment (JPA, H2, webmvc, actuator, cache, restdocs, Testcontainers). Do not rely on them; trimming them is optional cleanup.
- Base package `io.github.vincenzofiore.rootology`, organized as Spring Modulith modules; boundaries are verified by `ModularityTest`, which must keep passing.
  - `ontology`: `OntologyDefinition` (the closed dataset, as a Spring component), records `Plant`, `Pest`, enums `Season`, `SoilType`.
  - `inference`: `JenaModelService` (builds an in-memory RDF graph from `OntologyDefinition`, `refresh()` rebuilds it), `Vocabulary` (URIs, namespace `http://rootology.dev/ontology#`), and SPARQL queries `CompanionQuery`, `PestQuery`, `ChainedRelationQuery`, `FactPresenceQuery`.
  - `experiments`: `ExperimentRunner` computes the Jena answers; `ExperimentResult` is a record holding Jena, LLM-with-skill and LLM-without-skill answers plus a `matches` flag. The LLM arms are pasted in by hand from real Claude Code sessions (see `ExperimentRecordingTest`, which is a recording mechanism, not an assertion test).
- The LLM-side knowledge is `.claude/skills/rootology-garden-companion/SKILL.md`, a prose description of exactly the same dataset.
- Run the Jena side: `./mvnw test -Dtest=CompanionQueryTest,PestQueryTest,ChainedRelationQueryTest,FactPresenceQueryTest`.

## Closed domain (single source of truth: `OntologyDefinition`)

Soils: SANDY, CLAY, LOAMY. Seasons: SPRING, SUMMER, AUTUMN, WINTER.

- Tomato: soils LOAMY, CLAY; seasons SPRING, SUMMER; companions Basil, Marigold; repels Aphid.
- Basil: soils LOAMY; seasons SPRING, SUMMER; companions Tomato; repels Mosquito, Aphid.
- Marigold: soils SANDY, LOAMY, CLAY; seasons SPRING, SUMMER, AUTUMN; companions Tomato; repels Nematode.
- Aphid: active SPRING, SUMMER. Nematode: active SUMMER, AUTUMN.
- Mosquito appears only as something Basil repels. It has no seasonal facts, so any seasonal answer about it must be "not declared", never a guess.

Rules: companions are only what is declared (never inferred from shared soil or season). Relevant pests for plant X in season S = pests active in S minus pests X repels. Anything outside the dataset is out of scope.

## Existing experiment cases (keep them, add the new arm to each)

- `easy-1`: companions of Tomato (single hop).
- `easy-2`: relevant pests for Basil in summer (two-step filter).
- `hard-1`: companions of plants that repel the same pest as Tomato (three-hop; Jena's result includes Tomato itself, a genuine artifact of the closed dataset).
- `hard-2`: active season of Mosquito (missing fact: must say "not specified").
- `hard-3`: companions of Lavender (out of scope entity).
Observed so far: with the skill loaded the model reported hard-2 as not specified; without it, it guessed seasons from general knowledge.

## New goal: a third contestant

Add a pipeline that answers the same questions and gets compared with Jena (arbiter) and Claude alone (with and without the skill):

- Embabel Agent Framework orchestrates the flow. Its blackboard is the internal handoff, using typed domain objects (planned: `Question`, `GraphFacts`, `Decision` with decision, confidence and an `escalate` flag). Use a distinct output type per contestant so results are never confused.
- Semantic layer: the knowledge graph. Start by reusing the existing in-memory Jena graph as a plain fact source. Embabel Worlds is optional and only if it fits the toy; verify against current documentation first.
- Claude: understands the question and phrases the answer.
- Jev (TypeSafe AI, early access granted to the owner): decision layer returning a decision with confidence, wrapped as an Embabel action behind the `Decision` interface. If the API is not ready, use a stub with the same interface.
- Human in the loop only for low confidence or out-of-scope cases (hard-2 and hard-3 should escalate).

Design caveat to keep in mind: Jena is both the arbiter and, if reused as the fact source, a dependency of the pipeline. To keep the comparison meaningful, the pipeline should receive raw facts (triples or declared lists) and do the reasoning itself, while only the arbiter runs the SPARQL inference.

## Planned steps

1. Baseline: run the existing tests on Java 25 and confirm everything is green, including `ModularityTest`.
2. Compatibility check before adding any dependency: confirm which Spring Boot and Java versions the current Embabel release supports. This project is on Spring Boot 4.1.0 and Java 25, which may not match. Report the result to the owner before proceeding.
3. New Modulith module(s), for example `pipeline` and `decision`, with the typed objects and an Embabel agent skeleton reading facts through a narrow API of the `inference` module. Update `ModularityTest` expectations if needed.
4. Extend `ExperimentResult` (or add a sibling record) with a pipeline arm without breaking existing records. Keep the manual recording style for the "Claude alone" arms.
5. Integrate Claude for question understanding and phrasing.
6. Integrate Jev behind `Decision` (stub first if needed).
7. Add uncertainty to the inputs (for example noisy or missing readings), since the closed dataset is deterministic and gives a decision layer nothing to estimate. Then run all cases and compare.

## Open questions

- Exact Jev API access details and response format (probability? label?). It drives the shape of `Decision`.
- Embabel version and compatibility with Spring Boot 4.1.0 / Java 25 (step 2).
- Whether Embabel Worlds is usable as-is here and which store it needs (Neo4j is one option; FalkorDB and Memgraph are alternatives).
