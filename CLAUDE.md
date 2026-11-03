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
  - `facts`: the pipeline's fact store in Neo4j. `OntologyFacts.from(OntologyDefinition)` gives the triples, `FactGraphLoader` reloads Neo4j at startup, `FactSource.allFacts()` reads them back. `CropCatalog` loads the approved crops from the curated file `src/main/resources/knowledge/crops-v1.csv` (39 crops: id, Italian, English and scientific names, family checked against Wikidata, Wikidata and FAO ECOCROP ids). Curated knowledge files (#16) live in `src/main/resources/knowledge/`: `sources.csv` (every source with license; only commercial-use sources), `crop-needs.csv` (FAO ECOCROP, imported by the `live-api` tool `EcocropImportTool`), `sowing-windows.csv` (CENTRO zone, editorial, `verified=no`). `KnowledgeFilesTest` checks they are consistent.
  - `decision`: `Intent` + `QueryKind`, `GraphFacts` (readings tagged CURATED / UNCONFIRMED), `Decision`, interface `DecisionLayer`. `JevDecisionLayer` (TypeSafe System One API, structured state with fixed field order, `MIN_CONFIDENCE` 0.5) when `ROOTOLOGY_TYPESAFE_API_KEY` is set, otherwise `StubDecisionLayer`; `SkepticalStubDecisionLayer` as a baseline.
  - `pipeline`: Embabel agent `GardenPipelineAgent` (Question -> Intent via Claude -> GraphFacts -> Decision -> Resolution -> PipelineAnswer, phrased by Claude). `GardenPipeline.ask(...)` never blocks (`ReviewMode.AUTOMATIC`); `start(question, noise, RunContext)` / `pending(spaceId)` / `respond(processId, spaceId, answer)` pause on escalation until a member of the run's space answers (`ReviewMode.HUMAN`). `NoisyFacts` / `NoiseProfile` degrade inputs for the suite.
  - `experiments`: the quality suite's arbiter (`ExperimentRunner`), the five cases (`ExperimentCase`), recorded answers and comparison records.
  - `spaces` (#15): `app_user`, `space`, `space_member` (`OWNER` / `MEMBER`), Flyway `V2__create_spaces.sql` and `V3__track_first_login.sql`. GitHub logins are stored and compared in lower case (`SpaceService.normalizeLogin`). `SpaceService.signedIn` (called on every login) creates the person's personal space on their first real login (`first_login_at`), also when someone had already added them to a space; `spacesOf`, `isMember`, `membersOf`, `addMember`, `removeMember` (owners remove, members leave; nobody leaves their personal space, `space.personal_of`; a space keeps at least one owner), `changeRole`. Personal data elsewhere refers to a space by id.
  - Invitations (#23, `InvitationService`, Flyway `V4`): an owner creates a single-use link (`/invite/<token>`, 7 days, optionally restricted to one GitHub login); only the token's SHA-256 is stored; owners list and revoke pending ones. The invitation page is public: the token waits in the HTTP session (`PendingInvitation`) through the GitHub login, and `AllowListOAuth2UserService` accepts it. Who may sign in: the bootstrap `ROOTOLOGY_ALLOWED_GITHUB_USERS`, anyone who already belongs to a space, or the holder of a valid invitation.
  - `garden` (#14, Flyway `V5`): gardens of a space (name, town, coordinates rounded to 0.01 degrees), plots (`GROUND`, `RAISED_BED`, `CONTAINER`; soil incl. `POTTING_MIX`; optional area), plantings (crop from `CropCatalog`, sown or transplanted, date, ended when harvested). `GardenService` checks membership on every call; `growingOn(space, login, day)` is the context for garden questions (#17). UI: Garden view.
  - `history`: finished UI runs stored in Postgres (`pipeline_run`, Flyway migrations, Hibernate `ddl-auto: validate`), with the arbiter's answer when the question is a suite case. Each run belongs to a space (`space_id`) and records who started it; `RunHistory.recent(spaceId)`. Runs stored before #15 have no space and are not shown.
  - `ui` (Vaadin, http://localhost:8080): Compare, Escalations, History, Space (members; owners add people by GitHub login); a space switcher in the navbar. `CurrentUser` (Vaadin session scope) is the only source of the signed-in login and current space. GitHub OAuth2 login with an allow-list (`ROOTOLOGY_ALLOWED_GITHUB_USERS`), which works as invite-only access for the prototype.
- Secrets and settings come from project-specific environment variables: `ROOTOLOGY_ANTHROPIC_API_KEY`, `ROOTOLOGY_TYPESAFE_API_KEY`, `ROOTOLOGY_GITHUB_CLIENT_ID`, `ROOTOLOGY_GITHUB_CLIENT_SECRET`, `ROOTOLOGY_ALLOWED_GITHUB_USERS` (never `ANTHROPIC_API_KEY`, which Claude Code itself would pick up).
- `./mvnw spring-boot:run` starts Postgres and Neo4j from `compose.yaml` (needs Docker). Spring tests import `TestcontainersConfiguration`. A plain `./mvnw test` makes no paid call; tests tagged `live-api` call Claude and Jev and run with `-Plive`.
- CI (#13): `.github/workflows/build.yml` runs `./mvnw test` on every push to `develop` and `main` and on pull requests, on a pinned `ubuntu-26.04` runner with Java 25 (Temurin); no secrets, Testcontainers uses the runner's Docker. The README badge shows the `develop` status.
- `.claude/skills/rootology-garden-companion/SKILL.md` is the prose version of the original dataset, used only by the experiment's "Claude with skill" arm.

## Roadmap

Each item is a GitHub issue on `vincenzofiore/rootology` (labels `plan` and `phase-1` / `phase-2`), listed in working order. The issue is the detailed plan; this list only orders it.

Phase 0, done 2026-11-03: reframe from toy experiment to prototype (this file, `docs/experiment-log.md`, the issue list; #8 closed as no longer needed).

Phase 1, a personal assistant for a few invited users:

1. #13 (done) CI with GitHub Actions (offline build on every push).
2. #15 (done) Shared spaces: `app_user`, `space`, `space_member` (`OWNER` / `MEMBER`); a personal space created on first login; runs and escalations owned by a space, with who did what recorded; a current-space switcher; owners add members by GitHub login. The space filter lives in services and repositories, from a single `CurrentUser`.
3. #23 (done) Invitations to a shared space, and removing members: single-use expiring links (tokens stored hashed), revocable; accepting one also lets that GitHub account in, so the allow-list moves to the database. No email in the first version.
4. #14 My garden: plots, crops, coarse location and soil, owned by a space.
5. #16 Knowledge base v1: real crops, with a source and license for every fact, and an ontology layer designed with the data: a light schema in Neo4j (classes, hierarchies, constraints), AGROVOC identifiers where they fit, explicit and tested inference rules, inferred facts tagged `INFERRED`. Choose the sources with the owner first.
6. #24 Knowledge enrichment: members propose missing facts (names, crops, varieties, pairs, windows) with a source; a curator approves global facts; space-level facts stay in their space; Claude may suggest, never write. Sources and the crop list: `docs/knowledge-sources.md`, `docs/crops-v1.md` (commercial use is a goal, so no non-commercial sources).
7. #17 New question types (what to sow now, what not to plant together, pest risk this week, when to harvest).
8. #18 Local weather from an open service (check terms first).
9. #20 Quality suite: golden questions in CI, failing on silent wrong answers.
10. #19 Usage limits per user for Claude and Jev.
11. #25 Send email: SMTP (Gmail app password or a transactional service, terms checked first), Mailpit in development, invitations by email bound to a verified address.
12. #26 Notifications: in-app and email, starting with escalations; per-person preferences; Telegram, web push or ntfy later for sensors.
13. #27 Observability: structured logs, domain metrics (escalation rate, confidence, Claude/Jev latency and errors, cost per user), traces through the agent, health checks, alerts; no personal data in logs or traces.
14. #12 Deploy (likely a small cloud server; the DS225+ NAS would need 6 GB RAM): invite-only (allow-list and invitations, #23), HTTPS behind the platform, Postgres backups.
15. #28 Sign in with Google and provider-independent identities (verified email), once the deployment has a domain name.
16. #10 Trim unused Initializr dependencies. #4 Jev client library (deferred, see Open questions).

Phase 2, precision agriculture (umbrella #22):

1. #21 The owner's weather station on their terrace as the first real sensor and test bed: ingest its readings, show them next to the forecast, feed one decision (for example frost or watering), treat gaps and outliers as uncertain readings. Model sensors and readings on SOSA/SSN (W3C).
2. Then split #22: field sensors (soil moisture and temperature) as time series with per-sensor reliability, and operational decisions (irrigation, disease risk) that escalate when readings are missing or uncertain. #9 (Embabel DICE, confidence-weighted propositions) may fit here.

## Open questions

- Library for Jev calls (#4, deferred): our own client sends a structured state; Embabel's starter sends text only (asked upstream in embabel/embabel-agent#2211); the Spring AI Community starter accepts structured state but is untried here.
- Which open data sources for the knowledge base, and under which licenses.
- Hosting for the deployment (#12): likely a small cloud server; the owner's NAS (DS225+, 2 GB) is not enough without a RAM upgrade.
- The owner's weather station (#21): a Froggit console with EasyWeatherPro firmware (Ecowitt-compatible), registered on ecowitt.net and Weather Underground. Data via the Ecowitt cloud API v3 (proposed first), the WU PWS API, or the console's local "Customized" upload (Ecowitt protocol, plain HTTP). Keep its identifiers and keys out of the repository and public issues.
