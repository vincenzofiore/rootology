# Contributing to Rootology

Thank you for your interest. Rootology is a prototype used by a few invited people and kept by one
maintainer, so contributions follow a simple path: **issues are open to everyone, pull requests come
after an accepted issue**.

Everyone who takes part follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Issues

Open an issue for:

- a **bug**: what you did, what you expected, what happened;
- a **wrong or missing fact** in the knowledge base (a calendar month, a pest, a companion plant, a control
  method): say which fact, what is wrong, and the source that says otherwise;
- an **idea**: the problem it solves for a gardener, before the solution.

Security problems never go in a public issue: follow [SECURITY.md](SECURITY.md).

## Pull requests

Open a pull request only for an issue where the maintainer has said, in a comment, that a pull request is
welcome. Pull requests without one are closed with a pointer to this file: it is not about your work, it
keeps the review small enough for one person.

- Branch from `develop` and target `develop`. `main` is updated only by the maintainer.
- Keep it to the issue: no unrelated refactoring.
- `./mvnw verify` passes (it needs Docker and makes no paid call): tests, PMD, CPD and coverage. See
  [docs/testing.md](docs/testing.md).
- A change people notice gets a line in `CHANGELOG.md` under `[Unreleased]`, with the issue number.
- Check a screen you change on a phone, in dark mode and with a keyboard.

## Rights: Apache 2.0 and the DCO

Rootology is licensed under the [Apache License 2.0](LICENSE). Your contribution is licensed under the
same terms (section 5 of the license).

Every commit in a pull request carries a sign-off, which certifies the
[Developer Certificate of Origin 1.1](https://developercertificate.org/): that you wrote the change or have
the right to submit it under the project's license.

```
git commit -s -m "knowledge: correct the sowing months of chard (refs #123)"
```

adds `Signed-off-by: Your Name <your@email>` with the name and address of your git configuration. A check
on every pull request refuses commits without it. To add it afterwards: `git rebase --signoff develop`.

## Facts need a source

Rootology answers only from facts that have a source; this is the core of the project.

- Every new fact cites a source in `src/main/resources/knowledge/sources.csv`, with its title, address,
  license and the date you read it. A fact without one is marked editorial and needs the maintainer's review.
- Use only sources whose license allows reuse, and say the license in the pull request. Do not copy data from
  a source that does not allow it, even with a citation.
- Name methods and substances, never commercial products.
- The expected answers of the quality suite (`src/main/resources/quality/golden-questions.yaml`) are written
  by hand and reviewed by the maintainer: propose changes to them in the issue first.
- Do not change the experiment's fixture (`OntologyDefinition`, the five cases, the recorded judgments under
  `src/test/resources/experiments/`).

## AI tools

You may use AI tools. Say so in the pull request (which tool, for what), and remember that you answer for
every line: that it is correct, that its tests pass, and that every fact and source in it is real and
correctly licensed. A source you have not opened yourself is not a source.

## Conventions

- Code, comments, commit messages and documentation in English. Texts shown in the app are in
  `src/main/resources/i18n/messages*.properties`, Italian and English, with the typographic apostrophe ’.
- Commit messages are short, lowercase and imperative, with the issue: `area: what it does (refs #N)`.
- URLs, route segments and query parameters are English words.
- Styles live only in `src/main/resources/META-INF/resources/styles/rootology.css`.
- The structure of the code and its modules is described in [docs/architecture.md](docs/architecture.md).

## No personal data

Do not put personal data, keys, tokens or identifiers of devices or accounts (a weather station's id, an API
key, someone's address or coordinates) in issues, pull requests, commits or files. Secrets are passed only as
`ROOTOLOGY_*` environment variables and never committed.
