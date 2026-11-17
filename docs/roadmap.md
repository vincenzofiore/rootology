# Roadmap and its history

The working order of the plan as it was kept in CLAUDE.md until #128, verbatim, with what was done and when.
The open items and their order live in CLAUDE.md; each item is a GitHub issue, which holds the detailed plan.

Each item is a GitHub issue on `vincenzofiore/rootology` (labels `plan` and `phase-1` / `phase-2`), listed in working order. The issue is the detailed plan; this list only orders it.

Phase 0, done 2026-11-03: reframe from toy experiment to prototype (this file, `docs/experiment-log.md`, the issue list; #8 closed as no longer needed).

Phase 1, a personal assistant for a few invited users:

1. #13 (done) CI with GitHub Actions (offline build on every push).
2. #15 (done) Shared spaces: `app_user`, `space`, `space_member` (`OWNER` / `MEMBER`); a personal space created on first login; runs and escalations owned by a space, with who did what recorded; a current-space switcher; owners add members by GitHub login. The space filter lives in services and repositories, from a single `CurrentUser`.
3. #23 (done) Invitations to a shared space, and removing members: single-use expiring links (tokens stored hashed), revocable; accepting one also lets that GitHub account in, so the allow-list moves to the database. No email in the first version.
4. #14 (done) My garden: plots, crops, coarse location and soil, owned by a space.
5. #16 (done) Knowledge base v1: real crops, with a source and license for every fact, and an ontology layer designed with the data: a light schema in Neo4j (classes, hierarchies, constraints), AGROVOC identifiers where they fit, explicit and tested inference rules, inferred facts tagged `INFERRED`. Choose the sources with the owner first.
6. #24 (done) Knowledge enrichment: members propose missing facts (names, crops, varieties, pairs, windows) with a source; a curator approves global facts; space-level facts stay in their space; Claude may suggest, never write. Sources and the crop list: `docs/knowledge-sources.md`, `docs/crops-v1.md` (commercial use is a goal, so no non-commercial sources).
7. #17 (done) New question types (what to sow now, what not to plant together, pest risk this week, when to harvest).
8. #18 (done) Local weather from MET Norway, with the frost and Smith period rules.
9. #29 (done) Fail fast on external services: short timeouts on every client and a minimal circuit breaker per service (no new dependency; Resilience4j later with #27).
10. #30 (done) Garden coordinates from the town name (Nominatim/OpenStreetMap, policy checked first) or the browser's geolocation; rounded to 0.01 degrees; saving never blocked by the geocoder.
11. #32 (done) App roles and administration: ADMIN, CURATOR, QA in Postgres, granted and revoked by an admin from the UI with history; the first admin is inserted by hand (`docs/operations.md`); curators move to the database.
12. #33 (done) GUEST role in a space: a plain observer who sees everything and changes nothing. #34 (to evaluate later) a knowledge contributor app role and an operational collaborator space role. #52 (after the deploy, with #34) a review policy per space and non-blocking second opinions on answered escalations.
13. #20 (done) Quality suite: golden questions in CI, failing on silent wrong answers. The Compare view is already QA-only (#32); it becomes the QA view of the suite's and golden questions' results.
14. #19 (done) Usage limits per user for Claude and Jev.
15. #25 (done) Send email: SMTP (iCloud Mail app-specific password for now, a transactional service with a domain later), Mailpit in development, invitations by email bound to a verified address.
16. #26 (done) Notifications: in-app and email, starting with escalations; per-person preferences; Telegram, web push or ntfy later for sensors.
17. #37 (done) Weather alerts: a daily job per garden applies frost-risk-v1 and smith-period-v1 to what grows and notifies (#26) once per event; later rain, wind and official warnings (Meteoalarm, terms first).
18. #36 (done) Persist pending escalations so they survive a restart (before the deploy); guaranteed events via the Modulith registry, Namastack Outbox evaluated.
19. #38 (done) Telegram notifications: a bot of the owner's, linked with a one-time code; escalations, answers and weather alerts on the phone, the same outbox and quiet hours as email, two circuit breakers, nothing that changes data from Telegram. WhatsApp Business Platform evaluated and not built (per-message prices, templates and a webhook for a phone that already has Telegram and email).
20. #27 Observability: structured logs, domain metrics (escalation rate, confidence, Claude/Jev latency and errors, cost per user), traces through the agent, health checks, alerts; no personal data in logs or traces.
21. #31 (done) UX v1: typed notes instead of strings, a weather card (7 days, frost days, MET Norway icons), answers as cards (values, confidence, warnings, sources), a garden home, Italian UI, usable on a phone.
22. #40 (done) GDPR review: data inventory (`docs/privacy.md`), processors and transfers, account deletion and data export, retention rules, a public privacy page; open points for a professional.
23. #41 (done) OWASP review: Top 10 and Top 10 for LLM applications (`docs/security.md`): cross-space access tests, actuator and security headers, dependency scanning in CI, prompt injection and output handling; fix what is cheap, record the rest.
24. #56 (done) Navigation around spaces and gardens: the space switcher stays, the garden is the working context (overview after sign-in, `/spaces/{id}/gardens/{id}/...` routes, menu levels inside and outside a garden), questions, escalations and history carry the garden, notifications never filtered and switch context with confirmation.
25. #55 (done) Question context (after #56): one resolver for space, garden, plot, zone, month and weather location from the current garden; the question wins over the context, with a note; ambiguity escalates; the context used shown in the answer and stored in the history.
26. #61 (done) Unique agent process ids (UUID) instead of Embabel's random names. #60 (done) Notes of a waiting question shown to whoever answers and checked by the golden questions. #58 (done) no calendar note on pest questions abroad. #63 (done) Sources and Privacy at the bottom of the menu. #64 (done) the cookie notice names the cookies and the browser storage. #62 (done) Climate profile of a garden's site instead of NORD/CENTRO/SUD (AgERA5 frost dates per 0.1° cell of Europe, garden altitude, `frost-shift-v1`, a constraint rule by crop frost group, replacing `zone-shift-v1`; live run 2026-11-10T1422: no silent error, the north answered; it supersedes #59, closed). #57 (done) a garden or plot named in the question (names only to Claude, same precedence as #55). #53 (done) a domain filter: Jev decides whether a question is about the garden, and one clearly outside gets a fixed answer instead of an escalation.
27. #12 (done) Deploy: Hetzner Cloud, HTTPS, invite-only, nightly backups with a heartbeat, uptime monitor. #70 off-server backups, production restore test and domain email before the first outside users. #71 TypeSafe zero data retention. #68 intermittent Testcontainers failures in long runs. #69 Jev's confidence around the 0.5 threshold.
28. #28 (done) Sign in with Google and provider-independent identities (verified email), once the deployment has a domain name.
29. #65 (done) Request an invitation from the welcome page (after the deploy): sign in with GitHub or Google first, a pending state with no space and no paid call, a motivation approved or rejected by an admin, one open request per identity and a daily cap, no CAPTCHA for now.
30. #66 (done) Gardens on a map in the overview, linked to the table (marker and row select each other, a popup with the garden's essentials, add a garden from the map, map, table or both remembered in the browser); MapLibre GL JS with the OpenFreeMap public instance, no commercial component or tile service.
31. #10 (done) Trim unused Initializr dependencies; #67 (done) the current weather in the map's popups, from the stored forecast only. #4 Jev client library (deferred, see Open questions).
32. UI v2, from the screenshot review of 2026-11-11 (desktop and phone, light and dark, keyboard), in this order: #79 quick fixes (person menu off screen on a phone, page names, the personal space's name, repeated notes, errors, dates); #80 history for gardeners; #81 empty states and guided first steps; #82 Ask without blocking the page, with suggested questions; #83 a lighter answer card; #84 garden home as cards; #85 from plot to Area (Italian "Area", kinds open field, raised bed, tank, pot, greenhouse; area cards); #86 waiting questions as cards; #87 brand and visual identity (slogan "Ask. Know. Grow.", English only; drawings in one line style, few words; designed together, no external designer); #88 phone (bottom navigation, installable). Then #89 hours of direct sun of an area by season, and #90 layout of areas and crops in the garden, in steps (map, grid, neighbour rules; also where the sensors of phase 2 go).

Phase 2, precision agriculture (umbrella #22):

1. #21 The owner's weather station on their terrace as the first real sensor and test bed: ingest its readings, show them next to the forecast, feed one decision (for example frost or watering), treat gaps and outliers as uncertain readings. Model sensors and readings on SOSA/SSN (W3C).
2. Then split #22: field sensors (soil moisture and temperature) as time series with per-sensor reliability, and operational decisions (irrigation, disease risk) that escalate when readings are missing or uncertain. #9 (Embabel DICE, confidence-weighted propositions) may fit here.

