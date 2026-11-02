# Rootology

**Rootology** is a small, hands-on experiment comparing two ways of giving an
AI system domain knowledge: a formal ontology with a symbolic reasoning
engine (Apache Jena), and a natural-language "skill file" consumed by an LLM
coding assistant (Claude Code / GitHub Copilot). A third contestant, a layered
agentic pipeline (Embabel + Claude + TypeSafe Jev), answers the same questions
from raw facts.

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
io.github.vincenzofiore.rootology
├── RootologyApplication.java
├── ontology/       # Domain model: Plant, Pest, Soil, Season, and their
│                   # declared relationships (companion-of, repels, etc.)
├── inference/       # Apache Jena integration: builds an in-memory RDF graph
│   │               # from the ontology module and exposes SPARQL-backed
│   │               # queries (companions, relevant pests, chained relations,
│   │               # explicit fact-presence checks)
│   └── facts/       # Narrow named interface: raw declared triples only, no SPARQL
├── decision/        # Decision layer: Intent, GraphFacts (readings tagged with a
│                   # source), Decision; Jev behind DecisionLayer, or a stub
├── pipeline/        # Embabel agent: question understanding and phrasing by
│                   # Claude, facts from the graph, decision from the decision layer,
│                   # human in the loop for escalations
├── ui/              # Vaadin views: comparison of the arms and escalation queue
└── experiments/     # Comparison harness: records Jena's answers against
                    # answers manually collected from an LLM assistant,
                    # both with and without the skill file loaded, and
                    # against the pipeline
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

3. **Agentic pipeline**: an [Embabel](https://github.com/embabel/embabel-agent)
   agent passes typed objects on its blackboard:
   `Question → Intent → GraphFacts → Decision → PipelineAnswer`. Claude turns the
   question text into an `Intent` and phrases the final answer, but never sees
   the facts and never chooses the answer values. The decision layer gets only
   raw triples (never the SPARQL inference, which stays with the Jena arbiter)
   and returns an answer with a confidence and an `escalate` flag. With a
   TypeSafe key it is [Jev](https://docs.typesafe.ai), asked one yes/no
   judgment per candidate value; without one, a deterministic stub. Low
   confidence and out-of-scope questions are escalated to a human.

The first two describe **the same closed dataset** (three plants, two pests, four
seasons, three soil types) so that answers can be compared question by
question.

## Running the Jena side

```bash
./mvnw test -Dtest=CompanionQueryTest,PestQueryTest,ChainedRelationQueryTest,FactPresenceQueryTest
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

## Running the pipeline side

The pipeline needs two API keys, read from project-specific environment
variables (so that Claude Code itself never picks up `ANTHROPIC_API_KEY`):

```bash
export ROOTOLOGY_ANTHROPIC_API_KEY="..."   # Claude, via Embabel
export ROOTOLOGY_TYPESAFE_API_KEY="..."    # Jev; without it the stub decision layer is used
```

A plain `./mvnw test` makes no paid API call: the pipeline tests mock the LLM
and use the stub. Tests that call Claude and Jev for real are tagged
`live-api` and run only with the `live` profile:

```bash
./mvnw test -Plive -Dtest=PipelineRecordingTest       # the five cases, end to end
./mvnw test -Plive -Dtest=JevStabilityRecordingTest   # same input to Jev, repeated
./mvnw test -Plive -Dtest=NoiseRecordingTest          # degraded inputs, stub vs Jev
```

`NoiseRecordingTest` also saves Jev's raw judgments under
`src/test/resources/experiments/`, one file per recorded run (`FIRST`, and the
held-out `HOLDOUT` used to check the threshold), and never overwrites an existing
one. `ThresholdSweepTest` and `BaselineComparisonTest` re-use those judgments
offline, as part of the normal build, to compare confidence thresholds and
simpler baselines.

Since the closed dataset is deterministic, `NoiseRecordingTest` degrades the
pipeline's input (missing facts, false facts, unreliable source tags, seeded and
reproducible) while Jena keeps arbitrating on the clean data.

## Running the UI

```bash
./mvnw spring-boot:run      # then open http://localhost:8080
```

The UI requires a GitHub login (OAuth2). Create a GitHub OAuth App with
callback URL `http://localhost:8080/login/oauth2/code/github`, then set:

```bash
export ROOTOLOGY_GITHUB_CLIENT_ID="..."
export ROOTOLOGY_GITHUB_CLIENT_SECRET="..."
export ROOTOLOGY_ALLOWED_GITHUB_USERS="your-github-login"   # comma-separated; empty lets nobody in
```

Whoever answers an escalation is recorded by their GitHub login.

- **Compare**: pick an experiment case and see the arms side by side: Jena,
  Claude with and without the skill (as recorded by hand), and a live pipeline
  run, on clean or noisy facts. The pipeline calls Claude and Jev for real.
- **Escalations**: an escalated question pauses its Embabel agent (human in the
  loop). Pick it, give the answer, and the agent resumes and finishes.

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

The pipeline matches Jena on all five cases with clean facts and the stub, and
escalates `hard-2` and `hard-3`. With Jev and degraded inputs, the stub (which
trusts every reading) answered wrongly without escalating in 18 of 25 trials,
Jev in 1 to 3, all of them right at the confidence threshold. Two findings on
the phrasing step: Claude once contradicted a correct decision in its text, and
once silently hid a wrong one, so the recorded values come from the decision
layer, never from the text. Detailed notes are in `CLAUDE.md`.

## Dependencies

- Java 25
- Spring Boot 4.1 (starter, no web layer required for the core experiment)
- Spring Modulith 2.1 (`spring-modulith-starter-core`, `spring-modulith-starter-test`)
- Apache Jena 6.2 (`apache-jena-libs`) — RDF graph construction, SPARQL querying,
  and rule-based inference
- Embabel Agent Framework 1.5.4 (`embabel-agent-starter-anthropic`,
  `embabel-agent-test`) — agent orchestration over Claude
- TypeSafe System One API (Jev), called over HTTP with Spring's `RestClient`
- Vaadin Flow 25.3 — the experiment UI

## Status

This is a personal, exploratory project, not a production system. Its goal
is to gather first-hand, concrete observations — not to build a reusable
tool — to inform a broader reflection on providing domain context to AI
systems.

## License

License

Licensed under the Apache License 2.0 — chosen for consistency with the 
project's main dependencies (Spring, Apache Jena), both released under the same license.