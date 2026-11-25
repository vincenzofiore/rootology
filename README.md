# <img src="docs/brand/rootology-icon.svg" alt="" width="40" align="top"> Rootology

[![build](https://github.com/vincenzofiore/rootology/actions/workflows/build.yml/badge.svg?branch=develop)](https://github.com/vincenzofiore/rootology/actions/workflows/build.yml)

**Ask. Know. Grow.** A personal assistant for vegetable gardens and small-scale growing, at
[rootology.app](https://rootology.app). It is a prototype for a few invited users, designed for Italy for now,
with an Italian and an English interface.

## What it does

- **Answers questions about your garden** in plain language: what to sow or plant this month, companions,
  pests and diseases and how to control them, when to prune or harvest. Each answer is about your own garden:
  its local climate, the exposure and sun hours of each area, what is growing there now.
- **Garden home**: what grows in each area and when it is usually harvested, the free areas with up to three
  proposed crops each, official weather warnings first.
- **Morning alerts**: frost, extreme heat, heavy rain, strong wind and a reminder to water, from the MET Norway
  forecast, plus MeteoAlarm's official warnings, in the app, by email or on Telegram.
- **Shared gardens**: a space has owners, members and guests, joined by invitation.
- **Knows when it does not know**: a crop or a fact the knowledge base lacks is said so; an uncertain answer
  goes to a person of the space, and the answer records who gave it.

## How it answers

The idea comes from the original experiment (see below): the model never invents the facts.

1. A **knowledge graph** (Neo4j) holds curated facts, each with its source: FAO ECOCROP needs, regional
   integrated production rules, editorial calendars. Facts that people propose wait for a curator's review.
2. **Claude** understands the question and phrases the answer, but never chooses the answer's values.
3. A **calibrated decision layer** ([TypeSafe Jev](https://docs.typesafe.ai)) decides from the facts with a
   real confidence, and escalates when it is not confident enough.
4. A **person** answers the escalated cases.
5. A **quality suite** measures silent errors against an arbiter with known answers, and golden questions
   with answers written by hand run in CI.

Missing and noisy readings are what the decision layer was built for, which is why field sensors are the
next step (phase 2, [#22](https://github.com/vincenzofiore/rootology/issues/22)).

## Running locally

Docker must be running: Postgres, Neo4j and Mailpit start from `compose.yaml`, and the tests use
Testcontainers for the same services.

```bash
./run-local.sh      # then open http://localhost:8080
```

`run-local.sh` sends every email to Mailpit (http://localhost:8025), never to a real person, and stops an
older local run of this repository first. Extra arguments go to `./mvnw spring-boot:run`.

Settings are `ROOTOLOGY_*` environment variables, never files in the repository:

| Variable | What for |
|---|---|
| `ROOTOLOGY_ANTHROPIC_API_KEY` | Claude, via Embabel |
| `ROOTOLOGY_TYPESAFE_API_KEY` | Jev; without it a deterministic stub decides |
| `ROOTOLOGY_GITHUB_CLIENT_ID`, `ROOTOLOGY_GITHUB_CLIENT_SECRET` | sign-in with GitHub: an OAuth App with callback `http://localhost:8080/login/oauth2/code/github` |
| `ROOTOLOGY_GOOGLE_CLIENT_ID`, `ROOTOLOGY_GOOGLE_CLIENT_SECRET` | sign-in with Google, optional |
| `ROOTOLOGY_ALLOWED_GITHUB_USERS` | GitHub logins let in without an invitation, comma-separated |

After the first sign-in, grant yourself ADMIN as in "First admin of an environment" in
[docs/operations.md](docs/operations.md).

```bash
./mvnw test         # offline and free: no call to Claude, Jev or MET Norway
./mvnw verify       # also PMD, CPD and JaCoCo, as in CI
```

Tests that call Claude and Jev for real are tagged `live-api` and run only with `-Plive`.

### The local database

```bash
./local-db.sh dump [name]      # save it to ~/rootology-dumps (default name: date and time)
./local-db.sh list
./local-db.sh restore <name>   # replace it with a dump; Flyway applies newer migrations on the next run
./local-db.sh reset            # delete the volume and start from an empty database
```

`restore` and `reset` ask first (`--yes` skips the question) and refuse while the app of this checkout is
running. Each checkout has its own Postgres volume (named after the folder), so a feature branch's migration
run in a worktree never reaches the main checkout's database.

## Documentation

- [docs/architecture.md](docs/architecture.md): every module and the issue that built it.
- [docs/testing.md](docs/testing.md): which kind of test, shared containers and contexts, quality gates.
- [docs/operations.md](docs/operations.md): deployment on Hetzner, backups, the first admin.
- [docs/knowledge-sources.md](docs/knowledge-sources.md): where the facts come from, and their licenses.
- [docs/privacy.md](docs/privacy.md), [docs/security.md](docs/security.md), [docs/adr/](docs/adr/): privacy,
  security and architecture decisions.
- [docs/roadmap.md](docs/roadmap.md) and the [issues](https://github.com/vincenzofiore/rootology/issues): what
  is done and what comes next.

## The original experiment

Rootology started as a small experiment: give an AI the same garden knowledge as a formal ontology (Apache
Jena), as a natural-language skill file for an LLM assistant, and as an agentic pipeline (Embabel, Claude and
Jev), and compare their answers on five cases with known answers, including missing facts, multi-hop relations
and out-of-scope entities. The pipeline's design (facts from a graph, a calibrated decision, a person when
unsure) grew into the app. The experiment is now the
quality suite's fixture; its log, its findings and how to re-run it are in
[docs/experiment-log.md](docs/experiment-log.md).

The name is a play on "root" (plant roots, root causes) and "ontology".

## Stack

Java 25, Spring Boot 4.1, Spring Modulith 2.1 (module boundaries verified by tests), Vaadin Flow 25.3, Embabel
1.5 over Claude, TypeSafe Jev, Apache Jena 6.2, Neo4j 2026.10, Postgres 17 with Flyway, Spring Security OAuth2
(GitHub, Google). Data from FAO ECOCROP, MET Norway, MeteoAlarm and GeoNames.

## Contributing

Issues are welcome: bugs, wrong or missing facts (with their source), ideas. Pull requests come after the
maintainer has accepted the issue. See [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md); security problems go through [SECURITY.md](SECURITY.md).

## License

Apache License 2.0, the same as the project's main dependencies (Spring, Apache Jena).
