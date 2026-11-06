# Changelog

What each version of Rootology brings, for the people who use it and run it. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). The GitHub Release of each version carries its section here.

## [Unreleased]

Nothing changes for gardeners, members and guests, except that everyone signs in again once: sessions live
in memory, and the ones from before the release are not recognised.

### Added

- Admins get an email, in Italian and once per event, when Claude, Jev, MET Norway or GeoNames keep failing,
  and when the server's disk is 90 % full. It reaches admins with an address in Notifications (#27).
- Metrics for admins at `/actuator/metrics`: questions by outcome (answered, escalated, failed), the decision
  layer's confidence, and the duration and failures of calls to external services (#27).
- The health check reports `DEGRADED`, still with HTTP 200, while an external service is not answering;
  liveness and readiness (Postgres and Neo4j) checks for the deployment (#27).
- Sign in with Google, for people invited by email or to an open invitation link (#28).
- Link a GitHub and a Google account to the same person from My account, and unlink one when two are linked (#28)

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

[Unreleased]: https://github.com/vincenzofiore/rootology/compare/v0.1.0...develop
[0.1.0]: https://github.com/vincenzofiore/rootology/releases/tag/v0.1.0
