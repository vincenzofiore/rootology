# Rootology

**Rootology** is a small, hands-on experiment comparing two ways of giving an
AI system domain knowledge: a formal ontology with a symbolic reasoning
engine (Apache Jena), and a natural-language "skill file" consumed by an LLM
coding assistant (Claude Code / GitHub Copilot).

The name is a play on "root" (as in plant roots, and root causes / root
concepts) and "ontology" — the domain used for the experiment is a small
companion-planting garden model.

## Motivation

Modern AI coding assistants can be given domain context through natural
language documents (skill files, instructions files, CLAUDE.md). Formal
ontologies, on the other hand, represent a domain as an explicit graph of
concepts and relationships that a reasoning engine can query and validate
deterministically.

This project asks a simple, practical question:

> When the same domain knowledge is expressed both ways, how differently do
> a symbolic reasoner and an LLM behave on the same questions — especially
> on edge cases where a naive approach is likely to go wrong (missing facts,
> multi-hop relationships, out-of-scope entities)?

The garden domain was chosen deliberately: it's simple enough to model in an
afternoon, has genuinely interesting relationships (companion planting, pest
deterrence, seasonality), and carries no connection to any professional or
proprietary context.

## Project structure

The application is a single Spring Boot project organized with
[Spring Modulith](https://spring.io/projects/spring-modulith), so that module
boundaries are explicit and verified automatically by tests, without the
overhead of a multi-module Maven build.

```
dev.rootology
├── RootologyApplication.java
├── ontology/       # Domain model: Plant, Pest, Soil, Season, and their
│                   # declared relationships (companion-of, repels, etc.)
├── inference/       # Apache Jena integration: builds an in-memory RDF graph
│                   # from the ontology module and exposes SPARQL-backed
│                   # queries (companions, relevant pests, chained relations,
│                   # explicit fact-presence checks)
└── experiments/     # Comparison harness: records Jena's answers against
                    # answers manually collected from an LLM assistant,
                    # both with and without the skill file loaded
```

Module boundaries are declared with `@ApplicationModule` and enforced by a
`ModularityTests` class that runs `ApplicationModules.verify()` — the same
kind of architectural discipline this project's author normally applies with
ArchUnit, here provided natively by Modulith.

## The two ways of expressing the same ontology

1. **Formal / symbolic**: `ontology` module → RDF graph built by
   `JenaModelService` → queried via SPARQL (`CompanionQuery`, `PestQuery`,
   `ChainedRelationQuery`, `FactPresenceQuery`). Deterministic, always
   consistent with the declared facts, and capable of exact multi-hop
   inference.
2. **Natural language / LLM-consumed**: [`SKILL.md`](.claude/skills/rootology-garden-companion/SKILL.md),
   a structured prose description of the exact same concepts, relationships,
   and facts, written for an LLM coding assistant to read as context.
   Probabilistic: the model tends to follow the declared relationships, but
   with no formal guarantee, and only when the skill is actually loaded.

Both describe **the same closed dataset** (three plants, two pests, four
seasons, three soil types) so that answers can be compared question by
question.

## Running the Jena side

```bash
mvn test -Dtest=CompanionQueryTest,PestQueryTest,ChainedRelationQueryTest,FactPresenceQueryTest
```

Each test asserts the expected result for a specific relationship (direct
companionship, a two-step pest filter, a three-hop chained relation, and an
explicit missing-fact check).

## Running the LLM side

1. Place the skill folder under `.claude/skills/rootology-garden-companion/`
   in the project root (or under `~/.claude/skills/` to make it available
   globally).
2. Start a Claude Code session in the project directory.
3. Invoke the skill explicitly with `/rootology-garden-companion` before
   asking a question, to remove any ambiguity about whether the skill was
   actually loaded.
4. Ask the same question you'd send to the Jena queries, in plain language
   (e.g. *"What pests are relevant for Basil in summer?"*).
5. Record the answer manually in `ExperimentRecordingTest` (see below).

## Recording comparisons

The `experiments` module doesn't automate the LLM side — that part is
inherently manual, since it requires an actual conversation with the
assistant. `ExperimentRunner` computes the Jena answer automatically;
`ExperimentResult` stores it alongside the LLM answer(s) you paste in after
each session, supporting both a single "with skill" run and a three-way
comparison that includes a "without skill" baseline (the model's default,
general-knowledge behavior).

```java
ExperimentResult result = runner.recordLlmAnswerWithBaseline(
    "hard-2",
    "In which season is Mosquito active?",
    jenaAnswer,          // computed automatically
    llmWithSkillAnswer,  // pasted in after a session with the skill loaded
    llmWithoutSkillAnswer, // pasted in after a session without the skill
    "notes on what was observed"
);
```

## Experiment cases

| ID | Question | What it tests |
|---|---|---|
| `easy-1` | Companions of Tomato | Direct, single-hop relation — sanity check |
| `easy-2` | Relevant pests for Basil in summer | Two-step filter (active pests minus repelled pests) |
| `hard-1` | Companions of plants that repel the same pest as Tomato | Three-hop chained relation; Jena includes a self-referential result as a genuine artifact of the closed dataset — an interesting case for observing whether the LLM reproduces or "corrects" this |
| `hard-2` | Active season of Mosquito (a pest with no declared season) | Missing-fact handling: does the system say "not specified", or does it guess? |
| `hard-3` | Companions of Lavender (not in the dataset) | Out-of-scope entity: does the model respect the closed-dataset constraint declared in the skill, or fall back on general gardening knowledge? |

### Observed so far

With the skill loaded, `hard-2` correctly resulted in the model reporting
the fact as not specified. Without the skill loaded (same model, same
question, a separate session in a different directory), the model instead
generalized from general statistical knowledge about the seasonal activity
of mosquitoes — a concrete, reproducible example of context grounding
changing model behavior on an identical question. Further cases are being
recorded as the experiment continues.

## Dependencies

- Java 17+
- Spring Boot (starter, no web layer required for the core experiment)
- Spring Modulith (`spring-modulith-starter-core`, `spring-modulith-starter-test`)
- Apache Jena (`apache-jena-libs`) — RDF graph construction, SPARQL querying,
  and rule-based inference

## Status

This is a personal, exploratory project, not a production system. Its goal
is to gather first-hand, concrete observations — not to build a reusable
tool — to inform a broader reflection on providing domain context to AI
systems (written up separately as a LinkedIn post).

## License

License

Licensed under the Apache License 2.0 — chosen for consistency with the project's main dependencies (Spring, Apache Jena), both released under the same license.