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

- Talk to the owner (Vincenzo) in Italian. Write all code, comments, commit messages, README and docs in English. URLs, route segments and query parameters are always English, never Italian words.
- Prototype for a few real users: keep it small, readable and easy to inspect, with no speculative abstractions. Real users mean real basics: their data is kept separate, secrets stay outside the repository, inputs from outside are validated, the app survives a failing external service.
- Never commit or push on your own; ask the owner each time. Commit messages are short, lowercase and imperative, and reference the GitHub issue (`refs #N`, `closes #N` on the last commit of an item). Work happens on `develop`; `main` is updated only by the owner. A change that people or the server notice gets a line in `CHANGELOG.md` under `[Unreleased]` (Keep a Changelog, in English, with the issue number); internal refactors and tests do not.
- The quality suite is ground truth: do not change `OntologyDefinition` (now the suite's fixture), the five cases, the recorded judgments under `src/test/resources/experiments/` or `ExperimentRecordingTest`, unless explicitly asked. The real knowledge base grows elsewhere (see the plan).
- Jev is the decision layer by design. Do not build a Jev-vs-LLM comparison of decision layers.
- This domain is deliberately unrelated to the owner's day job. Do not import concepts or code from any professional project.
- Design matters as much as function: the app must be easy to use, easy to understand at a glance and look like a modern application. Prefer a clear icon with a value or a count over a sentence (the word stays as tooltip and aria label, so nothing is lost for screen readers), show only what helps the decision at hand, keep the same icon and pattern for the same action everywhere (`ui.RowActions`), confirm before anything destructive, and check every screen on a phone, in dark mode and with a keyboard. Styles live only in `styles/rootology.css`. When a screen feels crowded or wordy, propose a simpler one.
- Before adding a dependency or an external data source, check its current version, compatibility and license, and report to the owner.
- Agent orchestration (from 2026-11-12): Claude Code orchestrates and verifies, goose develops. Bounded, test-verifiable issues go to goose, run headless (`goose run --no-session -i <prompt>`) in its own worktree `../rootology-worktrees/goose-<n>` on a branch `goose/<n>` from `develop`, at most two at a time on disjoint files; `.goosehints` has the rules for those runs. Claude Code then reviews the diff, reads the test totals of goose's final run (and runs `./mvnw test` itself only when it changed the code, for example to merge two branches), checks the UI in the browser when the issue touches it, and proposes the commit to the owner (squashed onto `develop`). Design-heavy work, data model and rule changes, and anything that needs the owner stay with Claude Code, together with the owner.
- Architecture decisions are recorded in `docs/adr/` (numbered, with context, decision, consequences and when to revisit). ADR 0001: the front end stays on Vaadin Flow; a screen that needs a browser library gets a small client-side element, as the map does.

## Where things are

The detail of every module, with the issue that built it, is in `docs/architecture.md`: read the part the work
touches before changing it, and update it (not this file) when a change alters what it says. `ModularityTest` checks the module boundaries and writes the module diagram and each
module's dependencies to `target/spring-modulith-docs/`.

- Stack: Spring Boot 4.1.3, Java 25, Maven (`./mvnw`), Spring Modulith 2.1.2, Apache Jena 6.2.0, Embabel 1.5.4 (Claude, default `claude-sonnet-5-5`), Vaadin Flow 25.3, Spring Security OAuth2 (GitHub, Google), Postgres 17 with Flyway (Hibernate `ddl-auto: validate`), Neo4j 2026.10.
- Modules (base package `app.rootology`):
  - `ontology`, `inference`, `experiments`: the quality suite's fixture (`OntologyDefinition`), its Jena arbiter and the five cases. Ground truth, see the conventions.
  - `facts`: Neo4j fact store with two graphs that never mix (`Suite` and the curated knowledge base `Kb`), loaded at startup from `src/main/resources/knowledge/` and validated (`KnowledgeValidator`: an invalid base stops startup); climate cells and the calendar rules (`FrostSeasonRule`, `FrostShiftRule`, `ClimateWindows`); `KnowledgeGraph.resolve`/`factsAbout`.
  - `decision`: `Intent`, `QueryKind`, `GraphFacts` (readings tagged CURATED / UNCONFIRMED / INFERRED / PROPOSED / FORECAST / DECLARED), `Note`; Jev (`JevDecisionLayer`, `MIN_CONFIDENCE` 0.5) or the stub without a key; the domain filter (`JevDomainFilter`).
  - `pipeline`: the Embabel agent `GardenPipelineAgent` (question, intent by Claude, `ContextResolver`, facts, Jev, a person when uncertain, phrasing by Claude); `GardenPipeline` (`start`, `askKnowledgeBase`, `continueWith`, `respond`), garden, weather and light facts, pending escalations, metrics.
  - `spaces`, `access`, `roles`: people, spaces and members (OWNER / MEMBER / GUEST, blocking, display names), invitations, invitation requests, app roles ADMIN / CURATOR / QA.
  - `garden`: gardens, areas (`plot`), plantings, exposure and sun hours, `LightRule`, `RotationRule`.
  - `enrichment`: proposed facts, space-level or global after a curator's review.
  - `weather`, `alerts`, `geocoding`: MET Norway forecast with its cache and rules, the morning alerts, GeoNames.
  - `notifications`, `mail`: in-app, email digest and Telegram; SMTP (Mailpit locally).
  - `usage`, `resilience`, `operations`: usage and daily limits, circuit breakers and timeouts on every external call, operational alerts and metrics.
  - `privacy`, `history`: export, account deletion, retention (`docs/privacy.md`); stored runs.
  - `ui`: Vaadin views; the URL is the only source of the space and garden (`/spaces/{id}/gardens/{id}/...`); texts in `i18n/messages*.properties`; styles only in `styles/rootology.css`.
- Quality suite: golden questions in `src/main/resources/quality/golden-questions.yaml` (answers written by hand, reviewed by the owner), run offline by `GoldenQuestionsTest` in CI; live runs (`-Plive`) only on the owner's request.
- Running locally: `./run-local.sh` (Docker needed; email always to Mailpit, an older local run stopped first). Tests import `TestcontainersConfiguration` and share one set of containers: follow `docs/testing.md`. A plain `./mvnw test` makes no paid call; `live-api` tests run with `-Plive`.
- Secrets and settings: `ROOTOLOGY_*` environment variables only (never `ANTHROPIC_API_KEY`), never in the repository.
- Deployment and operations: `docs/operations.md` (Hetzner, Caddy, `deploy/`, backups, the first admin); images from the `publish` job of `.github/workflows/build.yml`; releases tagged by the owner.
- Security and CI checks: `docs/security.md`, `SECURITY.md`, `.github/workflows/`.
- Other docs: `docs/experiment-log.md` (the original experiment), `docs/climate-profile.md` and `tools/climate/`, `docs/knowledge-sources.md`, `docs/crops-v1.md`, `docs/crop-pairs-v1.md`, `docs/pests-v1.md`, `docs/adr/`, `docs/brand/`.

## Pitfalls

- UI texts are MessageFormat patterns: use the typographic apostrophe ’, never '; `TranslationsTest` checks both language files.
- "Now" comes from the `Clock` bean (replaced with `@TestBean` in tests), and the month of a question is in Italian time.
- A new Flyway migration takes the next number on `develop`; a feature branch's migration is renumbered when it is merged.
- `./mvnw -Pproduction package` leaves precompressed `.gz`/`.br` copies in `target/classes` that development then serves: `./mvnw clean` first.
- Notes, reasons and names reach Jev only in the forms described in `docs/architecture.md`; the suite's requests must stay as recorded.

## Roadmap

Each item is a GitHub issue on `vincenzofiore/rootology`; the issue is the detailed plan. What is done, and in
which order it was built, is in `docs/roadmap.md`.

Phase 1, a personal assistant for a few invited users, still open:

- Before the first users outside the owner's circle: #70 off-server backups, a production restore test and the domain email; #71 TypeSafe zero data retention; #125 after the next release, the root's redirect in production and Search Console; #165 flag accounts that only create useless traffic, blocked by an admin.
- Quality: #69 Jev's confidence around the 0.5 threshold; #27 observability, part 2 (traces, a place for metrics).
- Knowledge (census, not started): #139 crop list v2 (vegetables, herbs, fruit, flowers, perennials); #140 biological and integrated pest control per pest; #141 proposal of crops for the free areas.
- Later: #52 review policy per space and second opinions; #34 more collaborator roles; #99 support the project; #90 layout of areas in the garden (blocked: its value is being evaluated; step 1 on `feature/90-garden-plan`).
- Tooling: #127 dev environment; #128 this file; #129 ponytail trial.

Phase 2, precision agriculture (umbrella #22): #21 the owner's weather station as the first real sensor (SOSA/SSN), then field sensors and operational decisions that escalate on missing or uncertain readings; #9 Embabel DICE may fit here.
## Open questions

- Library for Jev calls (#4, deferred): our own client sends a structured state; Embabel's starter sends text only (asked upstream in embabel/embabel-agent#2211); the Spring AI Community starter accepts structured state but is untried here.
- Which open data sources for the knowledge base, and under which licenses.
- Off-server storage for the backups (#70): a Hetzner Storage Box BX11 in Finland with daily snapshots, chosen; deferred until the first outside users.
- The owner's weather station (#21): a Froggit console with EasyWeatherPro firmware (Ecowitt-compatible), registered on ecowitt.net and Weather Underground. Data via the Ecowitt cloud API v3 (proposed first), the WU PWS API, or the console's local "Customized" upload (Ecowitt protocol, plain HTTP). Keep its identifiers and keys out of the repository and public issues.
