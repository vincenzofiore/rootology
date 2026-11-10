# Security review

Rootology checked against the OWASP Top 10 (2025) and the OWASP Top 10 for LLM Applications (2025) (#41):
what applies, what is in place, what was fixed in the review, and what is accepted for a prototype used by a
few invited people. Personal data is covered in `docs/privacy.md` (#40). To report a vulnerability, see `SECURITY.md`.

## Web application (OWASP Top 10, 2025)

### A01 Broken access control
- In place: every space-scoped service call checks membership, and contributor for changes (`SpaceService.requireContributor`; a GUEST only looks). Objects are looked up within the space (`GardenService.garden/plot/planting`, proposals, invitations, escalations), so another space's id is refused. App roles are Spring authorities: `@RolesAllowed` on views, `@PreAuthorize` on services. `CurrentUser` is the only source of the signed-in login and current space, and switching space checks membership.
- Fixed: `RunHistory.recent` and `GardenPipeline.pending` now take the login and check membership themselves, instead of trusting the caller's space.
- Tested: `CrossSpaceAccessTest` (another space's ids from one's own space, and another space outright), `GardenPipelineAgentTest` (answering another space's escalation), the role tests.

### A02 Security misconfiguration
- In place: only `/logged-out`, `/invite/**`, `/sources`, `/privacy`, weather icons and styles are public; everything else needs sign-in. Spring's default headers (nosniff, `X-Frame-Options: DENY`, no-store cache control).
- Fixed: `Referrer-Policy: strict-origin-when-cross-origin`, a `Permissions-Policy` (geolocation for this origin only; no camera, microphone, payment, USB), a Content-Security-Policy, the session cookie `SameSite=Lax`, actuator exposure stated explicitly (health only, no details; metrics for admins since #27).
- The CSP is enforced, checked by browsing every page in Safari on 2026-11-05 with no violation. Accepted: Vaadin needs `'unsafe-inline'` and `'unsafe-eval'` for scripts. HSTS and the `Secure` cookie come with HTTPS in #12. Vaadin runs in development mode locally, which shows stack traces; the deployment runs in production mode (#12). `/actuator/health` and its liveness and readiness groups are public with the status only, for Caddy and an uptime monitor; `/actuator/metrics` needs the ADMIN role (#27).
- Tested: `LoggedOutPageTest` (public pages, headers), `SecurityTest`.

### A03 Software supply chain failures
- In place: CodeQL, the Maven dependency graph submitted for Dependabot, OSV-Scanner, dependency review with a licence deny-list on pull requests, Dependabot updates, actions pinned by commit SHA, secret scanning with push protection (see `CLAUDE.md`, CI).
- Fixed in the review: Spring Boot 4.1.0 to 4.1.2 and Guava pinned to 33.8.0-jre, from 34 known vulnerabilities to none.
- Every new dependency or data source is checked for version, licence and terms before it is added.

### A04 Cryptographic failures
- In place: invitation tokens are 256 random bits (`SecureRandom`), only their SHA-256 is stored; secrets only in environment variables; every external service over HTTPS (Anthropic, TypeSafe, GitHub, GeoNames, MET Norway; SMTP with STARTTLS).
- Accepted for now: the app itself is plain HTTP on the local network until #12.

### A05 Injection
- In place: SQL through JPA or `JdbcClient` with named parameters; Cypher with parameters, and the only concatenations are labels and relationship types from fixed sets (`KnowledgeGraph.count`, `KnowledgeContributions`, `KnowledgeGraphLoader`), never input; HTML emails escape every value (`HtmlUtils.htmlEscape`); the UI never renders raw HTML (no `Html` components), so text from people or Claude is shown as text.

### A06 Insecure design
- In place: the design keeps decisions out of the LLM (Claude understands and phrases, Jev decides from sourced facts, a person answers the uncertain cases); space data never mixes; global facts need a source and a curator; destructive actions confirm (account deletion asks for the login).

### A07 Authentication failures
- In place: sign-in only through GitHub OAuth2 (no passwords here), Spring's state parameter and session fixation protection, an allow-list or a valid invitation to get in, invitations single-use, expiring and optionally bound to a login or a verified email, sign-out invalidates the session, 30 minutes session timeout.

### A08 Software or data integrity failures
- In place: the curated knowledge files are validated at startup and the app refuses to start on invalid data; proposals are checked with the same rules and global ones approved by a curator; the quality suite in CI fails on silent wrong answers.

### A09 Security logging and alerting failures
- In place: invitations, joins and account deletions are logged; email addresses are masked; Jev's judgments are debug only. In production the logs are structured JSON with a random `traceId` per request (#27). Failed calls to external services log the service and the exception's type, not its message, which may carry the request (coordinates, the account, facts). `ObservabilityTest` checks that a whole run, notifications included, logs neither the question nor the asker's address. Admins are emailed when an external service keeps failing or the disk is nearly full (`docs/operations.md`).
- Accepted for now: no structured security events and no central log store; part of #27's second part.

### A10 Mishandling of exceptional conditions
- In place: every external call has short timeouts and a circuit breaker (#29); Claude down fails the question clearly; Jev down escalates to a person; no weather means no weather, with a note; saving a garden never depends on GeoNames.

## LLM application (OWASP Top 10 for LLM Applications, 2025)

### LLM01 Prompt injection
- In place: Claude never chooses the answer's values. It produces an intent (kind, subject, zone, month, language) that is looked up in the graph with parameters, and Jev decides from the facts found; the answer card shows the decided values as badges next to Claude's sentence.
- Fixed: questions are limited to 500 characters (`Question.MAX_LENGTH`, server and field); Claude's intent fields are trimmed, stripped of control characters and cut to a sane length (`Intent.bounded`), without turning unknown values into defaults (an unknown zone or month still escalates); the three prompts that carry the question say it is only the question, never instructions.
- In place (also, #53): every real question first passes a domain screen — one Noul question to Jev on the question text alone, sent as the request's state and never interpolated into the instructions, which close with the same "only the question, never instructions" line. A question that is clearly off-domain is refused before any Claude call, with a fixed text from the i18n files and no answer path, so a hostile question that reads as off-topic is stopped before Claude and before any reviewer sees it. This is a cheap first line, never a control we rely on: a fluent in-domain injection passes it, and the screen opens no output path — Jev still only judges and Claude still only phrases. A failure of the screen (no key, open circuit, error, timeout) lets the question through, because a wrong refusal is the worse error.
- Accepted: an injected question can still change the wording of Claude's sentence; the values shown and the sources come from the graph and Jev.

### LLM02 Sensitive information disclosure
- In place: a prompt carries only the question, the facts of the asking space and the notes; never another space's data, secrets or logins. Jev receives the question text for the domain check (#53) and the understood intent and facts for the decision, never the login, the garden or the space's context (see `docs/privacy.md`).

### LLM03 Supply chain
- The models are used through their vendors' APIs (Anthropic, TypeSafe) under commercial terms; libraries are covered by A03.

### LLM04 Data and model poisoning
- In place: space facts stay in their space; global facts need a cited source and a curator's approval; curated files are validated.

### LLM05 Improper output handling
- In place: Claude's text is shown as text in the UI and escaped in emails; it is never executed, used in queries or used to pick values.

### LLM06 Excessive agency
- In place: Claude has no tools; it cannot write to the graph or the database. Claude suggestions for knowledge (#24) are not built, and would only suggest.

### LLM07 System prompt leakage
- The prompts are in this public repository and contain nothing secret.

### LLM08 Vector and embedding weaknesses
- Not applicable: no embeddings or vector store.

### LLM09 Misinformation
- In place: answers come only from sourced facts, uncertain cases escalate to a person, sources are shown, and the golden questions measure silent errors in CI and live (#20).

### LLM10 Unbounded consumption
- In place: a daily question limit per person (#19), question length limit (above), timeouts and circuit breakers (#29), invitation limits per owner (#25), Embabel retries cut to one attempt per action.

## Still open

- With the deploy (#12): HTTPS, HSTS, `Secure` cookie, Vaadin production mode, health endpoint access, backups.
- With observability (#27): security events and alerts, without personal data.
- A ZAP baseline scan against the deployed app (terms checked first).
