# Changelog

What each version of Rootology brings, for the people who use it and run it. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). The GitHub Release of each version carries its section here.

## [Unreleased]

### Changed

- The history speaks to gardeners: a title, the date, the question, the answer's values, who asked, the
  outcome and the context, an empty state with a way to ask, and a click on a row opens the full answer card.
  The quality suite's own columns (case, noise, arbiter, match, confidence) are gone from it, and live only in
  Compare for QA; the page refreshes when it opens, without a Refresh button (#80).
- Dark mode uses GitHub's dark palette (a near-black canvas, neutral greys, GitHub's blue, red, green and
  amber) instead of Lumo's slate blue; light mode is unchanged (#75).

### Fixed

- A person waiting for an invitation can leave the request page: the arrow at the top signs them out and
  goes back to the welcome page, which they can also open by its address (#76).
- The gardens map's popups show the weather now for a garden just added or moved, and for gardens where
  nothing grows: a new place's forecast is fetched in the background when the garden is saved, and every
  garden's forecast is refreshed every 6 hours (#74).
- A notification of a waiting question that someone answered no longer stays in the bell's count with no way
  to open it: it counts as read once the question has its answer, and those left unread before are cleared
  (#73).

## [0.3.0] - 2026-11-11

### Added

- A person can request an invitation from the welcome page after signing in with GitHub or Google; admins
  approve or reject it in Administration and are notified by email and Telegram (#65).
- The gardens of a space are on a map, beside the table (below it on a narrow screen); the map alone is
  shown first, and the person chooses map, table or both, which the browser remembers. A marker opens a
  garden's essentials (town, altitude, approximate coordinates, what grows today, the questions waiting) with
  the same actions as its row: open, edit and delete. A click on a marker selects its row, a selected row
  centres the map on it, and gardens on the same rounded point share one marker with their count. Each row of
  the table has a gear menu with those actions. Zoom buttons, and a search that moves the map to a place by
  name. A click on land offers to add a garden there, for whoever may change the garden, and opens a shorter
  dialog with the place already filled in; a click on water says so instead, and coordinates typed by hand that
  look like open sea are questioned before saving. The map is MapLibre GL JS on the free OpenFreeMap tiles and
  never blocks the page: when it cannot load, the table keeps working (#66).
- A question can name a garden of the space ("cosa cresce nel terrazzo grande?") or a plot of the current
  garden ("cosa semino nel davanzale?"): it is answered for that garden or plot, says so, and is kept in that
  garden's history; a name that matches no garden or several waits for a person instead of being guessed
  (#57).
- The gardens map's popups show the weather now, from the stored forecast: MET Norway's symbol with the
  temperature and, when the hour covers one hour, the precipitation (#67).
- A garden can be deleted, from its row's menu or its popup, after a confirmation that names it and its plots:
  its plots and plantings go with it, its questions and notifications stay in the space among the questions
  without a garden (#66).
- Open, edit and delete in the app's rows and in the map's popups are icons, with their word on hover and for
  screen readers; in a popup the altitude, the crops growing and the questions waiting are icons with a value
  or a count, the questions waiting highlighted when there are some. Deleting a plot or a crop now asks first, like a
  garden (#66).

- Notifications also reach the phone: a person links their Telegram account once, with a one-time code from the
  Notifications page, and from then on waiting questions, answers and weather alerts arrive there too, promptly
  and without opening the app. A Telegram message says the space, the garden and the title, with a link back to
  the app; the question, the answer and the alert text stay here. Each channel has its own switches per kind, the
  quiet hours cover both, a blocked bot is reported in the app, and the link can be removed at any time. Without
  a bot token the feature does not appear at all (#38).

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

- Sowing and harvest windows are now each garden's own, derived from its climate profile instead of an
  Italian zone: AgERA5's 1-in-5-years frost dates, corrected for the garden's altitude, move the central-Italy
  calendar only where the garden's frosts make a window impossible. The window rule `frost-shift-v1` replaces
  `zone-shift-v1`; the curated central-Italy windows are Vasto's, a zone named in the question uses that
  zone's reference site, and a garden abroad gets its own calendar with a note. The garden's home shows
  "Clima dell'orto" with the altitude, both frost dates, the cell and "AgERA5 1996–2025"; the answer's context
  line shows the last frost the calendar used (#62).

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

[Unreleased]: https://github.com/vincenzofiore/rootology/compare/v0.3.0...develop
[0.3.0]: https://github.com/vincenzofiore/rootology/releases/tag/v0.3.0
[0.2.0]: https://github.com/vincenzofiore/rootology/releases/tag/v0.2.0
[0.1.0]: https://github.com/vincenzofiore/rootology/releases/tag/v0.1.0
