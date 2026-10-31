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
  - `inference :: facts` (named interface, sub-package `inference.facts`): `FactSource.allFacts()` returns raw declared triples as `Fact` records. It is the only part of `inference` the pipeline may use (no SPARQL).
  - `decision`: `Intent` + `QueryKind`, `GraphFacts`, `Decision` (answer, confidence, escalate, reason), interface `DecisionLayer`. `JevDecisionLayer` (TypeSafe System One API, Noul questions per candidate plus an `answerable` check) is used when `ROOTOLOGY_TYPESAFE_API_KEY` is set, otherwise the deterministic `StubDecisionLayer` (see `DecisionLayerConfiguration`).
  - `pipeline`: Embabel agent `GardenPipelineAgent` (Question -> Intent via Claude -> GraphFacts -> Decision -> PipelineAnswer, phrased by Claude), entry point `GardenPipeline.ask(...)`.
  - `ui` (Vaadin 25.3 Flow, `./mvnw spring-boot:run`, http://localhost:8080): `ComparisonView` shows one experiment case with Jena, the hand-recorded Claude arms (`RecordedLlmAnswers`, a display copy of `ExperimentRecordingTest`) and a live pipeline run; `EscalationView` lists paused agents and resumes one with a person's answer.
  - Human in the loop: `GardenPipeline.start` runs in `ReviewMode.HUMAN`; an escalated decision throws Embabel's `AwaitableResponseException` (`TypeRequest<HumanAnswer>`), the process waits, `respond` binds the answer and resumes it. `ask(...)` stays in `ReviewMode.AUTOMATIC`, so recordings never block.
  - `experiments` also has `PipelineResult`, the sibling record for the pipeline arm, recorded by `PipelineRecordingTest` (real Claude, runs only when `ROOTOLOGY_ANTHROPIC_API_KEY` is set).
- Embabel 1.5.4 (`embabel-agent-starter-anthropic`), default LLM `claude-sonnet-5-5`. API keys use project-specific env vars (`ROOTOLOGY_ANTHROPIC_API_KEY`, `ROOTOLOGY_TYPESAFE_API_KEY`) so Claude Code itself never picks up `ANTHROPIC_API_KEY`. `GardenPipelineAgentTest` mocks the LLM via `EmbabelMockitoIntegrationTest`.
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
Pipeline arm, first real run (2026-10-31): all five intents extracted correctly and all values match Jena (hard-2, hard-3 escalated). In hard-1 the phrasing step contradicted the correct `[Tomato]` decision ("can't identify the companions"). The owner chose to keep the phrasing prompt untuned and record this as a finding.
Pipeline arm with Jev (2026-10-31, `jev-latest`, threshold 0.5, nothing tuned after seeing results):
- First two runs used unsorted facts. Jev's answers shifted between runs (easy-1 once included Tomato as its own companion; Mosquito `answerable` was 0.44 in one run and above 0.5 in another). In that easy-1 run the phrasing step silently dropped Tomato from the text, hiding the error: the mirror image of the stub-era hard-1 finding. Values, not text, are the record.
- `FactSource` now sorts facts. With identical input (`JevStabilityRecordingTest`, 5 runs per case, no Claude) Jev is stable within about ±0.05, but fact order alone had moved some answers across 0.5.
- Stable picture: easy-1 [Basil, Marigold] correct, but Tomato at p 0.18 to 0.26 puts confidence around the threshold (2 of 5 runs escalated). easy-2 [Nematode] correct, confidence about 0.8. hard-1: Basil and Tomato both near 0.45, answer empty, always escalated (does not resolve the three-hop chain, but knows it). hard-2: `answerable` 0.23 to 0.31, always escalated. hard-3: `answerable` 0.25 to 0.32, always escalated.
- Full pipeline (Claude + Jev, sorted facts), two runs: same picture, except hard-1 once came out [Tomato] (matching Jena) with confidence 0.17, still escalated. Every hard case escalated in every run; no wrong answer went out un-escalated.
Step 7, noisy inputs (2026-10-31, `NoiseRecordingTest`, fixed intents, no Claude). Readings now carry a source (CURATED / UNCONFIRMED); the noise is 15% dropout, 2 false facts about the subject, 20% wrong source tags, seeds 1 to 5. Jena stays on the clean data. Outcomes over 25 noisy trials per layer:
- Stub (trusts every reading): 7 right, 0 escalated, 18 wrong. It never doubts: hard-2 always answers invented Mosquito seasons, hard-3 answers companions of Lavender, easy-2 always loses Nematode to the false "Basil repels Nematode".
- Jev, two runs: 10 right, 12 escalated, 3 wrong; then 12 right, 12 escalated, 1 wrong. Every wrong one sat right at the threshold (confidence 0.50 to 0.52, two of them on hard-2). hard-3 was escalated in every trial, hard-2 in 8 of 10.
- The stub is right on hard-1 in every trial, by luck: the injected Tomato facts happen to keep [Tomato] as the answer. Jev escalates hard-1 every time.
- Cost of the source-aware Jev prompt on clean data: easy-2 dropped from about 0.8 to about 0.4 confidence and now escalates. Nothing was tuned after seeing these results.
- Threshold sweep (2026-10-31, `ThresholdSweepTest` over the judgments saved in `src/test/resources/experiments/jev-noise-judgments.json`, a third noise run; the file is an experiment record, rewritten only by `NoiseRecordingTest`). Noisy, 25 trials, right / escalated / wrong: 0.3 → 3/10/12, 0.4 → 9/11/5, 0.5 → 10/13/2, 0.6 → 11/14/0, 0.7 → 11/14/0, 0.8 → 10/15/0, 0.9 → 10/15/0. Clean, 5 trials: 3/2/0 up to 0.7, then easy-1 escalates too. 0.6 removes every silent error here for one extra escalation, but it is chosen on the same 25 trials it is judged on; `MIN_CONFIDENCE` was left at 0.5 pending the owner's call.
- Baseline comparison for #1 (2026-10-31, `BaselineComparisonTest`, offline, same 25 noisy trials as the sweep; Jev at threshold 0.5). Right / escalated / wrong: naive stub 7/0/18, skeptical stub (drops UNCONFIRMED) 11/0/14, Jev 10/13/2. The source tag alone cuts silent errors from 18 to 14. The rest of Jev's advantage comes from knowing when to escalate: on hard-2 the skeptical stub still invents seasons in 3 of 5 trials, while Jev escalates in all 5. The skeptical stub beats Jev on hard-3 (5/5 against 4/5) and loses on hard-1 (it drops true facts mislabeled as UNCONFIRMED: 2/5 against Jev's 5 escalations). Neither stub ever escalates on its own, by design.
- Held-out calibration for #3 (2026-10-31, `NoiseRun.HOLDOUT`: seeds 6 to 15, `nearSubject` on, 50 noisy trials, judgments in `jev-noise-judgments-holdout.json`). Jev, right / escalated / wrong per threshold: 0.3 → 25/20/5, 0.4 → 22/25/3, 0.5 → 23/26/1, 0.6 → 22/27/1, 0.7 → 20/29/1, 0.8 → 19/30/1, 0.9 → 20/30/0. The 0.6 candidate picked on `FIRST` does not generalize: on held-out data it only turns one right answer into an escalation. Decision: keep `MIN_CONFIDENCE` at 0.5. The one remaining error (hard-2, seed 6) is irreducible noise: the false fact "Mosquito activeInSeason SPRING" was tagged CURATED, so no layer could tell it from a true fact.
- Baselines on `HOLDOUT`: naive stub 23/0/27, skeptical stub 31/0/19, Jev 23/26/1. With the more diluted noise the stubs do better than on `FIRST`, but they still fail silently on hard-2 and hard-3 (naive stub 0/10 on both). Jev escalates easy-2 and hard-1 in all 10 trials: the cost of its caution, already visible on clean data for easy-2.
- Field order (fixed 2026-10-31): until then `JevDecisionLayer` built the request with `Map.of`, whose iteration order changes on every JVM run, so `FIRST` and `HOLDOUT` (and every earlier Jev run) sent the state fields to Jev in an order that could differ between runs. Within one run the order was stable, so the ±0.05 jitter measured by `JevStabilityRecordingTest` is Jev's own. Requests now use insertion-ordered maps (`intent`, `rule`, `sources`, `facts`; each fact as subject, predicate, object, source). Jev reads fields in order, so later runs are not strictly comparable with the recorded files on this point.
- Limit of the noise design: for Basil and Tomato only two false facts are possible, so easy-2 and hard-1 get the same two injected facts at every seed; only dropout and source tags vary there. Fixed by #2 with `NoiseProfile.nearSubject` (false facts also about the plants and pests sharing a fact with the subject). It is off in `NoiseCases.noise`, because `jev-noise-judgments.json` and the offline comparisons depend on the original noise; fresh recordings (#3) should turn it on. With it on, some injected facts are harmless to the asked answer (for example `Aphid activeInSeason AUTUMN` for Tomato's companions), so the noise is more varied but also more diluted.

## New goal: a third contestant

Add a pipeline that answers the same questions and gets compared with Jena (arbiter) and Claude alone (with and without the skill):

- Embabel Agent Framework orchestrates the flow. Its blackboard is the internal handoff, using typed domain objects (planned: `Question`, `GraphFacts`, `Decision` with decision, confidence and an `escalate` flag). Use a distinct output type per contestant so results are never confused.
- Semantic layer: the knowledge graph. Start by reusing the existing in-memory Jena graph as a plain fact source. Embabel Worlds is optional and only if it fits the toy; verify against current documentation first.
- Claude: understands the question and phrases the answer.
- Jev (TypeSafe AI, early access granted to the owner): decision layer returning a decision with confidence, wrapped as an Embabel action behind the `Decision` interface. If the API is not ready, use a stub with the same interface.
- Human in the loop only for low confidence or out-of-scope cases (hard-2 and hard-3 should escalate).

Design caveat to keep in mind: Jena is both the arbiter and, if reused as the fact source, a dependency of the pipeline. To keep the comparison meaningful, the pipeline should receive raw facts (triples or declared lists) and do the reasoning itself, while only the arbiter runs the SPARQL inference.

## Planned steps (all done as of 2026-10-31)

1. Baseline: run the existing tests on Java 25 and confirm everything is green, including `ModularityTest`.
2. Compatibility check before adding any dependency: confirm which Spring Boot and Java versions the current Embabel release supports. This project is on Spring Boot 4.1.0 and Java 25, which may not match. Report the result to the owner before proceeding.
3. New Modulith module(s), for example `pipeline` and `decision`, with the typed objects and an Embabel agent skeleton reading facts through a narrow API of the `inference` module. Update `ModularityTest` expectations if needed.
4. Extend `ExperimentResult` (or add a sibling record) with a pipeline arm without breaking existing records. Keep the manual recording style for the "Claude alone" arms.
5. Integrate Claude for question understanding and phrasing.
6. Integrate Jev behind `Decision` (stub first if needed).
7. Add uncertainty to the inputs (for example noisy or missing readings), since the closed dataset is deterministic and gives a decision layer nothing to estimate. Then run all cases and compare.

## Next plan (agreed 2026-10-31)

Each item is a GitHub issue (label `plan`), listed in working order (list numbers are not issue numbers) on `vincenzofiore/rootology`. Work happens on `develop`; reference the issue in commit messages (for example `refs #1`, or `closes #1` for the last commit of an item).

Constraint: Jev is the decision layer by design. Do not build a Jev-vs-LLM comparison of decision layers (for example Embabel's prompted-LLM decision service as an alternative to Jev).

1. (#1, done) Skeptical stub as a stronger baseline: a stub that drops UNCONFIRMED readings, compared offline on the same noisy seeds. Shows how much of Jev's advantage is judgment rather than the source tag alone. No paid calls.
2. (#2, done) Fix the noise design limit: allow false facts about entities near the subject too, so easy-2 and hard-1 do not get the same two injected facts at every seed.
3. (#3, done: kept at 0.5) Calibrate `MIN_CONFIDENCE` on fresh seeds (see Open questions). Do it after #1 and #2, so the new trials serve all comparisons. Record them with `nearSubject` on, in a new judgments file, so the existing one stays a valid record.
4. (#4, decision deferred) Choose whether to replace the hand-written Jev HTTP client with a library. Candidates: Embabel's `embabel-agent-starter-typesafe` (1.5.4, `DecisionService` via `context.ai().decisions()`), whose decision input is a `String` sent as `"state": "<text>"` (checked on the jar and on `main`; asked upstream in embabel/embabel-agent#2211, opened 2026-10-31: if declined, structured state is not planned there), while we send a structured JSON state, as TypeSafe recommends; and the community `spring-ai-starter-typesafe` (0.4.0), which accepts a structured state but is not yet tried on this stack. Until then we keep our own client. Whatever the choice, keep the `DecisionLayer` interface, the raw-facts input, a structured state with fixed field order and the saved-judgments format, and compare Jev's probabilities with a recorded run before switching.
5. (#5, done; Embabel's `vaadin-components` not used: only 0.2.0-SNAPSHOT on Embabel's Artifactory, built on Embabel 1.5.0 with Spring Security) UI with Vaadin Flow and Embabel's `vaadin-components`: a comparison view (one question, the arms side by side with confidence and escalation) and an escalation queue where a human answers. This turns the `escalate` flag into a real human-in-the-loop step (Embabel can pause an agent and wait for input).
6. (#11) Login with OAuth2 (GitHub), since the app is meant for the cloud: Spring Security `oauth2Login` with Vaadin, no page visible without login, an allow-list of GitHub accounts, the reviewer who answers an escalation recorded in the answer (so #6 keeps it), client id and secret from `ROOTOLOGY_...` environment variables. Before #6.
7. (#6) Postgres for experiment run history (trials, outcomes, confidences, thresholds), when the UI (#5) needs it. Populated from the pipeline, never a replacement for `OntologyDefinition`. Testcontainers is already in the pom.
8. (#7) Neo4j as the pipeline's own fact store, populated from `OntologyDefinition`, so the arbiter (Jena) and the pipeline's fact source stop being the same component.
9. (#8) Re-record the "Claude alone" arms with noise (a degraded skill file), by hand in real Claude Code sessions, to close the comparison under noise.
10. (#9) Later, only if it fits: Embabel DICE (incubating library, confidence-weighted propositions, Neo4j and Prolog) or Embabel Worlds (a separate docker-compose product, 0.1.0-rc.1, where the LLM explores the graph via "code mode", which conflicts with the pipeline design where Claude never sees the facts).
11. (#10) Optional cleanup: trim the unused Initializr dependencies (JPA, H2, restdocs, Testcontainers) unless #6 or #7 need them.
12. (#12) Deploy to the cloud (provider not chosen yet): container image with Vaadin in production mode, hosting and databases (#6, #7), secrets in the platform's secret store, public URL registered as the OAuth callback (#11).

## Open questions

- Resolved: Jev is the TypeSafe System One HTTP API (`POST https://api.typesafe.ai/v1/systemone`, model `jev-latest`); Noul questions return a probability of yes. `JevDecisionLayer` derives confidence as min |2p - 1|.
- Resolved: Embabel 1.5.4 works on Spring Boot 4.1.0 / Java 25 (built against Boot 4.1.1, Java 21 bytecode; Boot overrides its Kotlin to 2.3.21).
- Tests tagged `live-api` call Claude and Jev for real and are excluded from a plain `./mvnw test`; run them with `-Plive`.
- Resolved (#3): `JevDecisionLayer.MIN_CONFIDENCE` stays at 0.5. The 0.6 picked on `FIRST` gave no gain on the held-out `HOLDOUT` run.
- Embabel Worlds: checked 2026-10-31. Not a library but a separate product (`embabel-worlds/appliance`, docker compose); see plan item 9.
