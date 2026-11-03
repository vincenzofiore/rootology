# Rootology

A personal assistant for vegetable gardens and small-scale agriculture, as a prototype for a few real users. Later it should grow into precision agriculture with field sensors.

The core idea comes from the original experiment (see `docs/experiment-log.md`):

- A **knowledge graph** holds verifiable facts, each with its source.
- An **LLM** (Claude) understands the question and phrases the answer, but never invents the facts and never chooses the answer values.
- A **calibrated decision layer** (Jev) decides from the facts with a real confidence, and knows when it does not know.
- A **human in the loop** answers the uncertain cases; who answered is recorded.
- A **quality suite** measures silent errors against an arbiter with known answers.

Sensors fit this design directly: missing and noisy readings are what the decision layer was built to handle (see the noise work in the experiment log).

## Working conventions

- Talk to the owner (Vincenzo) in Italian. Write all code, comments, commit messages, README and docs in English.
- Prototype for a few real users: keep it small, readable and easy to inspect, with no speculative abstractions. Real users mean real basics: their data is kept separate, secrets stay outside the repository, inputs from outside are validated, the app survives a failing external service.
- Never commit or push on your own; ask the owner each time. Commit messages are short, lowercase and imperative, and reference the GitHub issue (`refs #N`, `closes #N` on the last commit of an item). Work happens on `develop`; `main` is updated only by the owner.
- The quality suite is ground truth: do not change `OntologyDefinition` (now the suite's fixture), the five cases, the recorded judgments under `src/test/resources/experiments/` or `ExperimentRecordingTest`, unless explicitly asked. The real knowledge base grows elsewhere (see the plan).
- Jev is the decision layer by design. Do not build a Jev-vs-LLM comparison of decision layers.
- This domain is deliberately unrelated to the owner's day job. Do not import concepts or code from any professional project.
- Before adding a dependency or an external data source, check its current version, compatibility and license, and report to the owner.

## Current state (already built)

- Stack: Spring Boot 4.1.0, Java 25, Maven (`./mvnw`), Spring Modulith 2.1.0 (boundaries verified by `ModularityTest`, which must keep passing), Apache Jena 6.2.0, Embabel 1.5.4 (`embabel-agent-starter-anthropic`, default LLM `claude-sonnet-5-5`), Vaadin Flow 25.3, Spring Security OAuth2 client, Postgres 17 with Flyway, Neo4j 2026.10 (Java driver, Cypher). Some Initializr leftovers remain (actuator, cache, restdocs; see #10).
- Modules (base package `io.github.vincenzofiore.rootology`):
  - `ontology`: `OntologyDefinition`, the original closed dataset (3 plants, 3 pests). Now the quality suite's fixture.
  - `inference`: Jena graph and SPARQL queries. Serves only the arbiter of the quality suite.
  - `facts`: the pipeline's fact store in Neo4j. `OntologyFacts.from(OntologyDefinition)` gives the triples, `FactGraphLoader` reloads Neo4j at startup, `FactSource.allFacts()` reads them back.
  - `decision`: `Intent` + `QueryKind`, `GraphFacts` (readings tagged CURATED / UNCONFIRMED), `Decision`, interface `DecisionLayer`. `JevDecisionLayer` (TypeSafe System One API, structured state with fixed field order, `MIN_CONFIDENCE` 0.5) when `ROOTOLOGY_TYPESAFE_API_KEY` is set, otherwise `StubDecisionLayer`; `SkepticalStubDecisionLayer` as a baseline.
  - `pipeline`: Embabel agent `GardenPipelineAgent` (Question -> Intent via Claude -> GraphFacts -> Decision -> Resolution -> PipelineAnswer, phrased by Claude). `GardenPipeline.ask(...)` never blocks (`ReviewMode.AUTOMATIC`); `start/pending/respond` pause on escalation until a person answers (`ReviewMode.HUMAN`). `NoisyFacts` / `NoiseProfile` degrade inputs for the suite.
  - `experiments`: the quality suite's arbiter (`ExperimentRunner`), the five cases (`ExperimentCase`), recorded answers and comparison records.
  - `history`: finished UI runs stored in Postgres (`pipeline_run`, Flyway migrations, Hibernate `ddl-auto: validate`), with the arbiter's answer when the question is a suite case.
  - `ui` (Vaadin, http://localhost:8080): Compare, Escalations, History; GitHub OAuth2 login with an allow-list (`ROOTOLOGY_ALLOWED_GITHUB_USERS`), which works as invite-only access for the prototype.
- Secrets and settings come from project-specific environment variables: `ROOTOLOGY_ANTHROPIC_API_KEY`, `ROOTOLOGY_TYPESAFE_API_KEY`, `ROOTOLOGY_GITHUB_CLIENT_ID`, `ROOTOLOGY_GITHUB_CLIENT_SECRET`, `ROOTOLOGY_ALLOWED_GITHUB_USERS` (never `ANTHROPIC_API_KEY`, which Claude Code itself would pick up).
- `./mvnw spring-boot:run` starts Postgres and Neo4j from `compose.yaml` (needs Docker). Spring tests import `TestcontainersConfiguration`. A plain `./mvnw test` makes no paid call; tests tagged `live-api` call Claude and Jev and run with `-Plive`.
- CI (#13): `.github/workflows/build.yml` runs `./mvnw test` on every push to `develop` and `main` and on pull requests, on a pinned `ubuntu-26.04` runner with Java 25 (Temurin); no secrets, Testcontainers uses the runner's Docker. The README badge shows the `develop` status.
- `.claude/skills/rootology-garden-companion/SKILL.md` is the prose version of the original dataset, used only by the experiment's "Claude with skill" arm.

## Roadmap

Each item is a GitHub issue on `vincenzofiore/rootology` (labels `plan` and `phase-1` / `phase-2`), listed in working order. The issue is the detailed plan; this list only orders it.

Phase 0, done 2026-11-03: reframe from toy experiment to prototype (this file, `docs/experiment-log.md`, the issue list; #8 closed as no longer needed).

Phase 1, a personal assistant for a few invited users:

1. #13 (done) CI with GitHub Actions (offline build on every push).
2. #15 Shared spaces: `app_user`, `space`, `space_member` (`OWNER` / `MEMBER`); a personal space created on first login; runs and escalations owned by a space, with who did what recorded; a current-space switcher; owners add members by GitHub login. The space filter lives in services and repositories, from a single `CurrentUser`.
3. #23 Invitations to a shared space: single-use expiring links (tokens stored hashed), revocable; accepting one also lets that GitHub account in, so the allow-list moves to the database. No email in the first version.
4. #14 My garden: plots, crops, coarse location and soil, owned by a space.
5. #16 Knowledge base v1: real crops, with a source and license for every fact, and an ontology layer designed with the data: a light schema in Neo4j (classes, hierarchies, constraints), AGROVOC identifiers where they fit, explicit and tested inference rules, inferred facts tagged `INFERRED`. Choose the sources with the owner first.
6. #17 New question types (what to sow now, what not to plant together, pest risk this week, when to harvest).
7. #18 Local weather from an open service (check terms first).
8. #20 Quality suite: golden questions in CI, failing on silent wrong answers.
9. #19 Usage limits per user for Claude and Jev.
10. #12 Deploy: invite-only (allow-list and invitations, #23), HTTPS behind the platform, Postgres backups.
11. #10 Trim unused Initializr dependencies. #4 Jev client library (deferred, see Open questions).

Phase 2, precision agriculture (umbrella #22):

1. #21 The owner's weather station on their terrace as the first real sensor and test bed: ingest its readings, show them next to the forecast, feed one decision (for example frost or watering), treat gaps and outliers as uncertain readings. Model sensors and readings on SOSA/SSN (W3C).
2. Then split #22: field sensors (soil moisture and temperature) as time series with per-sensor reliability, and operational decisions (irrigation, disease risk) that escalate when readings are missing or uncertain. #9 (Embabel DICE, confidence-weighted propositions) may fit here.

## Open questions

- Library for Jev calls (#4, deferred): our own client sends a structured state; Embabel's starter sends text only (asked upstream in embabel/embabel-agent#2211); the Spring AI Community starter accepts structured state but is untried here.
- Which open data sources for the knowledge base, and under which licenses.
- Cloud provider for the deployment (#12).
- The owner's weather station (#21): a Froggit console with EasyWeatherPro firmware (Ecowitt-compatible), registered on ecowitt.net and Weather Underground. Data via the Ecowitt cloud API v3 (proposed first), the WU PWS API, or the console's local "Customized" upload (Ecowitt protocol, plain HTTP). Keep its identifiers and keys out of the repository and public issues.
