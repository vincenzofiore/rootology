# Changelog

What each version of Rootology brings, for the people who use it and run it. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). The GitHub Release of each version carries its section here.

## [Unreleased]

### Added

- A question that is clearly not about gardens is no longer sent to a reviewer: Jev first judges whether the
  question is about the garden, and only a clear no gets a fixed, polite answer ("Rootology answers only about
  gardens and growing") with no answer attempt and no waiting question. A button under the answer asks the same
  question anyway; a question Jev is unsure about, or when Jev is unavailable, takes the normal path, and a
  refused question is kept in the history and counted like the others (#53).

- One question, one context: the answer, the history and the waiting question use only the garden the
  question was asked in (zone, month, forecast, what grows, plots), the question's own words win over the
  garden with a note when they disagree, and a plot that does not exist or matches several, a garden question
  asked outside a garden and a garden deleted meanwhile wait for a person instead of guessing. Every answer,
  history row and waiting question says what context it used (space, garden, plot, zone, month), and keeps
  it, so whoever answers knows what the question was about (#55).
- The space and the garden are the working context, and the URL is the only source of it: the gardens
  overview, the garden's home (today, weather, what grows), and Ask, Plots, Escalations, History and
  Knowledge under the garden, plus the space level for the questions asked outside a garden; members and
  invitations on their own pages, the curators' queue on its own; questions, escalations and history
  record their garden, and a notification of another space or garden asks before opening it. Old links
  lead to the space's only garden, or to the overview (#56).
- Each plot can record its orientation, direct sun, wind and floor (balconies and terraces), declared by the
  gardener; the "what to sow now" and "what to sow in this plot" answers warn when a crop needs more light
  than the plot gets, and name the crops whose light needs are unknown (#54).
- Whoever answers a waiting question sees its notes: the same warnings and remarks an answered question shows
  (a missing forecast, a plot that was not found, a question asked outside a garden), kept with the waiting
  question and also checked by the quality suite when a question escalates (#60).
- When someone answers a waiting question, the person who asked gets a notification in the app and an email
  with who answered and a link to the answer; the other members only see their notification resolved (#51).

### Changed

- The Privacy page names exactly what stays in the browser: the `JSESSIONID` session cookie and its
  attributes, when it ends, the local storage key of the light or dark choice, and the fact that GitHub and
  Google set their own cookies while signing in. Still no consent banner (#64).
- Sources and Privacy moved from the side menu to a small line at the bottom of the menu and of the
  public pages (#63).

### Fixed

- Waiting questions and their notifications can no longer be mixed up by a repeated internal id: each
  question gets a random identifier that never repeats (#61).
- A pest question no longer mentions the Italian sowing and harvest calendars for a garden abroad, and its
  context no longer shows a zone that played no part in the answer (#58).
- The welcome page no longer shows a back link to itself for visitors (#50).

## [0.2.0] - 2026-11-06

Everyone signs in again once after this release: sessions live in memory, and the ones from before it are not
recognised.

### Added

- A public welcome page explains Rootology and offers sign-in with GitHub or Google; visitors no longer land
  straight on GitHub (#47).
- Admins get an email, in Italian and once per event, when Claude, Jev, MET Norway or GeoNames keep failing,
  and when the server's disk is 90 % full. It reaches admins with an address in Notifications (#27).
- Metrics for admins at `/actuator/metrics`: questions by outcome (answered, escalated, failed), the decision
  layer's confidence, and the duration and failures of calls to external services (#27).
- The health check reports `DEGRADED`, still with HTTP 200, while an external service is not answering;
  liveness and readiness (Postgres and Neo4j) checks for the deployment (#27).
- Sign in with Google, for people invited by email or to an open invitation link (#28).
- Link a GitHub and a Google account to the same person from My account, and unlink one when two are linked (#28).

### Changed

- In production the logs are JSON, one object per line, with an id per request to follow it (#27).
- The nightly backup runs at 1:30 UTC, before the retention job (#12). Update the server's crontab.

### Security

- A GitHub account is now recognised by its stable id: renaming it keeps its spaces, and whoever takes a
  freed GitHub name cannot enter someone else's account (#28).
- Failed calls to external services no longer log the exception's message, which could carry a garden's
  coordinates or the facts sent to Jev (#27).

## [0.1.0] - 2026-11-05

The first release, for a few invited users.

### Added

- Questions about the vegetable garden in Italian or English: companions and plants to keep apart, when to
  sow and harvest, what to sow now, pest risk, what grows in the garden. Answers come from a curated
  knowledge base with a source for every fact (39 crops, 66 pairs, 24 pests and diseases), and say how sure
  they are (#16, #17).
- When the app is not sure, the question waits for a person of the space, who answers it (#15, #36).
- Shared spaces with owners, members and guests, invitations by link or email (#15, #23, #25, #33).
- The garden: plots, soil, what is planted and when, with the place found by name or by the browser's
  position (#14, #30).
- The weather forecast of MET Norway for the garden, frost and late blight warnings in answers, and a
  morning alert when they apply to what grows (#18, #37).
- Proposing missing facts for a space, or for everyone with a curator's approval (#24, #32).
- Notifications in the app and by email, grouped and outside quiet hours (#26).
- A daily question limit per person (#19).
- Account deletion, data export and a public privacy page (#40).
- Italian and English interface, usable on a phone, light and dark (#31).

[Unreleased]: https://github.com/vincenzofiore/rootology/compare/v0.2.0...develop
[0.2.0]: https://github.com/vincenzofiore/rootology/releases/tag/v0.2.0
[0.1.0]: https://github.com/vincenzofiore/rootology/releases/tag/v0.1.0
