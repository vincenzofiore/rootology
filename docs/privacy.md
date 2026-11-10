# Personal data in Rootology

What personal data Rootology processes, why, where it is kept, for how long and who receives it (#40).
This is the working inventory behind the public privacy page (`/privacy`); it is not legal advice,
and the open questions at the end go to a professional before the app opens beyond a few invited people.

The controller is the owner of the deployment, named on the privacy page through
`ROOTOLOGY_PRIVACY_CONTROLLER` and `ROOTOLOGY_PRIVACY_CONTACT` (not in this repository).

## What is kept, and why

| Data | Where | Purpose | Legal basis | Kept |
|---|---|---|---|---|
| Login (the internal user name), first sign-in | `app_user` | sign-in, membership, who did what | contract (the service the person asked for) | until the account is deleted |
| GitHub or Google account: the numeric id GitHub gives, or Google's `sub`, and the verified email address the provider gives as primary | `user_identity` | recognise the person at sign-in, even when their name or address there changes | contract | until the account is deleted |
| Verified email addresses | session only (`verified_emails`), not stored, except the primary one, kept in `user_identity` | accept an invitation bound to an address; suggest a notification address | contract | the session |
| Notification address, email on/off, quiet hours, and the Telegram and weather switches | `notification_preference` | send the notifications the person chose | contract | until changed or the account is deleted |
| Telegram chat id, when linked, whether it still works | `telegram_link` | sending the notifications the person chose to their phone | contract | until the person unlinks it or deletes the account; a link Telegram no longer delivers to is deleted after 30 days |
| Telegram one-time link codes | `telegram_link_code` (only the SHA-256) | linking a chat from the app | contract | ten minutes; deleted 30 days after use or expiry |
| Spaces and memberships, roles in a space | `space`, `space_member` | sharing a garden | contract | until the person leaves or the account is deleted |
| Invitations (invited login or email address, who invited) | `invitation` | joining a space | contract | the address is cleared when the invitation is accepted, revoked or expires; the row is deleted 30 days after |
| Gardens (name, town, country, coordinates rounded to 0.01°, about 1 km), plots (with their optional declared exposure: orientation, direct sun, wind, floor), plantings with optional notes | `garden`, `plot`, `planting` | garden questions, weather, alerts | contract | until deleted by the space, or with the space |
| Questions (free text) and answers, who asked and who answered, the garden they are about (a question asked outside a garden has none; the rows of a deleted garden keep none), and the context the answer used as a snapshot (the space's and garden's names as they were, the zone, the month, the plot) | `pipeline_run` (`context_json`); `pending_escalation` while the question waits | history, escalations to a person | contract | 12 months; resolved escalations 90 days. The names in the snapshot stay with the question when the garden is renamed or deleted, which is why they are kept |
| Notifications, with the garden an alert or a question is about | `notification` | in-app bell and email digest | contract | 90 days after they are resolved or read; weather alerts 90 days, read or not |
| Usage records (who, when, calls, tokens, cost) and personal limits | `usage_record`, `usage_limit` | fair use, cost control | legitimate interest | 12 months |
| Knowledge proposals (who proposed, who reviewed, source, note) | `knowledge_proposal`, Neo4j | attribution of contributed facts | legitimate interest | as long as the fact is in use; the login is replaced when the account is deleted |
| App roles with who granted and revoked them | `app_role_grant` | accountability of administration | legitimate interest | kept; the login is replaced when the account is deleted |
| Weather forecasts and recent past hours for a rounded position | `weather_location`, `weather_hour` | weather in answers and alerts | contract | 30 days |
| Application logs (logins in security events: invitations, joins; masked email addresses; a random id per request; never questions, prompts or coordinates) | the host's logs | security, troubleshooting | legitimate interest | set by the host (#12) |
| Metrics (counts and durations by service, graph and outcome; never a person, a space or a question) | the app's memory, since the last start | operations (#27) | not personal data | until the next restart |

The person may link a second account (GitHub or Google) to their own profile from My account, and nothing is merged by email: linking only happens when they ask for it while signed in. Unlinking one deletes its `user_identity` row; the last one cannot be unlinked, so nobody is left without a way in.

Nothing is used for advertising, profiling or sold.

### What stays in the browser (#64)

Two things only, and both are strictly necessary or chosen by the person, so no consent banner is needed:

| What | Name | Attributes, lifetime | Purpose |
|---|---|---|---|
| Session cookie | `JSESSIONID` | `Path=/`, `HttpOnly` (Tomcat's default), `SameSite=Lax` (`server.servlet.session.cookie` in `application.yaml`), `Secure` in production (`application-prod.yaml`), no `Max-Age`: a browser-session cookie, gone when the browser is closed. Server side the session ends after 30 minutes without requests (an open page keeps it alive through Vaadin's heartbeat) — Tomcat's default, since neither `application.yaml` nor `application-prod.yaml` sets `server.servlet.session.timeout` — or at sign-out | sign-in and the Vaadin UI. The same session holds what signing in needs: the token of an open invitation (`PendingInvitation`), the marker of a second account being linked (`PendingLink`), the OAuth state and PKCE verifier of the sign-in in progress and the CSRF token (all in memory, never in a cookie) |
| Colour scheme | localStorage key `rootology.colorScheme` | until the person clears the browser's data; never sent to the server | the light / dark / automatic choice (`MainLayout.SCHEME_KEY`; values `LIGHT_DARK`, `LIGHT`, `DARK`) |

During sign-in, GitHub and Google set their own cookies on their own domains under their own policies
([GitHub subprocessors and cookies](https://docs.github.com/en/site-policy/privacy-policies/github-subprocessors-and-cookies),
[GitHub privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement),
[Google cookies](https://policies.google.com/technologies/cookies), [Google privacy policy](https://policies.google.com/privacy));
Rootology neither sees nor controls them. Nothing else is stored for Rootology by the app itself: the few
keys the Vaadin client adds (`vaadin.live-reload.*`, `vaadin.statistics.*`, `vaadin-hotswap-scroll`) belong to
its development tools and exist only while the app runs in development mode, not in the production build.
Session data lives in the server's memory: a restart or a timeout loses it, and the person signs in again.

## Who receives it

| Service | Receives | Role and terms | Where | Retention, training |
|---|---|---|---|---|
| Anthropic (Claude) | the question text, the facts and notes for the answer | processor; DPA incorporated into the Commercial Terms, with EU SCCs | USA | API inputs and outputs deleted within 30 days (up to 2 years if flagged for a usage policy violation); no training on customer content |
| TypeSafe (Jev) | the question text for the domain check (#53), then the understood intent (kind, subject, zone, month) and the facts for the decision; never the login. The subject may be a plot name. Purpose: keeping clearly off-topic questions away from reviewers | processor; DPA (24 April 2026) part of the agreement, with EU SCCs (module 2). The MCA (23 September 2026, 4.1) grants no training on Customer Data without consent, but a perpetual licence to derive "Telemetry" (logs, hashes, statistics, classifications, learnings) from it to improve the services | USA | "as long as necessary"; no training on customer content; zero data retention for enterprise customers on request (through the support form), to ask |
| GitHub | sign-in (OAuth, scopes `read:user`, `user:email`) | independent controller of the person's GitHub account | USA, EU-US Data Privacy Framework | GitHub's own policy |
| Google | sign-in (OpenID Connect, scopes `openid`, `email`, `profile`) | independent controller of the person's Google account | USA, EU-US Data Privacy Framework | Google's own policy |
| Apple iCloud Mail | notification and invitation emails (address, text) | consumer terms, no DPA; "designed and intended for personal use" | Ireland / USA | Apple's own policy. Accepted for the prototype; replaced by a transactional email service before opening beyond invited friends (#12) |
| GeoNames (Unxos GmbH) | place names typed in the garden dialog; rounded coordinates for the nearest town | no published request logging policy | Switzerland (EU adequacy decision) | unknown |
| MET Norway | rounded coordinates | logs the caller's IP (the server's, not the person's) and coordinates | Norway (EEA) | MET Norway's own policy |
| Telegram | the chat id as addressee, and the message: the space and garden names, a title and a link — never a question, an answer or a weather alert's text; the person's Telegram username, phone number and photo never leave their app | recipient and independent controller of the person's Telegram account; bot chats are stored in Telegram's cloud (for EEA users, data centres in the Netherlands), group-company transfers under EU SCCs | Netherlands (EEA); Telegram Group Inc (BVI) and Telegram FZ-LLC (Dubai) for the service | Telegram's own policy; our messages stay in the chat and in Telegram's cloud until the person deletes them |
| Hetzner Online (hosting) | everything stored: the server runs the app and its databases | processor; DPA (art. 28) accepted in the customer account on 2026-11-05 | Germany | backups are ours (`deploy/backup.sh`), Hetzner's own backups are off |
| OVHcloud (domain `rootology.app`) | only the owner's registrant data; DNS answers for the domain | the owner's own provider, not a processor of users' data | France | |

The person's browser talks only to Rootology, plus GitHub and Google for sign-in (which set their own cookies
there, see above). The browser's position
(`navigator.geolocation`) is read only when the person presses the button, and rounded to 0.01° before the server sends it to any service.

## How it is applied

- `privacy.RetentionJob` runs every night at 3:30 Italian time (`rootology.retention`) and deletes old runs,
  answered escalations, read or resolved notifications and old weather alerts, usage records and closed invitations.
- `privacy.AccountDeletion` deletes an account in one transaction, through a small class in each module
  (`SpacePersonalData`, `GardenPersonalData`, `RunPersonalData`, `EscalationPersonalData`,
  `NotificationPersonalData`, `TelegramPersonalData`, `UsagePersonalData`, `ProposalPersonalData`,
  `RolePersonalData`); space facts leave
  the Neo4j graph after the commit. An approved global fact proposed in a deleted space keeps no space (Flyway `V18`).
- `privacy.DataExport` builds the JSON file from the same classes. The My account page (`/account`) offers both.

## Rights

- **Access and export**: a JSON file with the person's data and the spaces they belong to (#40).
- **Deletion**: the person deletes their account. Their personal space and everything in it,
  their preferences, usage, notifications, limits and open invitations they created are deleted;
  in what stays in shared spaces (questions, answers, reviewed proposals, role history) the login
  is replaced by "deleted user". Not possible while they are the only owner of a space with other
  members (hand it over first) or the last admin.
- **Rectification**: gardens, preferences and proposals are editable in the app.
- **Objection, restriction, complaint**: by email to the contact on the privacy page; complaints
  to the Italian supervisory authority (Garante per la protezione dei dati personali).

## Known risks accepted for the prototype

- iCloud Mail for sending, without a processor agreement (see above).
- Questions are free text sent to Claude: the privacy page asks people not to write personal data in them.
- Telegram (only for people who link it): the space and garden names and the titles of the notifications sit in
  Telegram's cloud and on the phone's lock screen, and Telegram keeps bot messages like any chat. Telegram's
  Bot Platform terms also require developers to encrypt user data at rest; the server has no disk encryption
  yet (to decide with #12).
- Transfers to the USA rely on the processors' SCCs and the Data Privacy Framework.

## Open questions for a professional

1. The legal basis for a free service to a few invited people that may become commercial: is "contract" right, and does anything need consent?
2. Is a record of processing (art. 30) needed at this size, and is this document enough as one?
3. Are the processors' online DPAs (Anthropic, TypeSafe) sufficient as accepted, or is a signed copy needed?
4. Is a transfer impact assessment needed for Anthropic and TypeSafe (USA)?
5. The retention periods above: are they proportionate?
6. Logins in shared records replaced by "deleted user" after deletion: is that enough, or must those rows go too?
7. Free text that may contain personal data (the question, for the domain check) now goes to TypeSafe as well: does the DPA cover that as it stands, or is something more needed?
8. The MCA's perpetual licence to derive "Telemetry" (logs, hashes, statistics, classifications, learnings) and use it to improve the services: is it acceptable, and does it need a documented decision?
