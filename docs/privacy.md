# Personal data in Rootology

What personal data Rootology processes, why, where it is kept, for how long and who receives it (#40).
This is the working inventory behind the public privacy page (`/privacy`); it is not legal advice,
and the open questions at the end go to a professional before the app opens beyond a few invited people.

The controller is the owner of the deployment, named on the privacy page through
`ROOTOLOGY_PRIVACY_CONTROLLER` and `ROOTOLOGY_PRIVACY_CONTACT` (not in this repository).

## What is kept, and why

| Data | Where | Purpose | Legal basis | Kept |
|---|---|---|---|---|
| GitHub login, first sign-in | `app_user` | sign-in, membership, who did what | contract (the service the person asked for) | until the account is deleted |
| Verified GitHub email addresses | session only (`verified_emails`), not stored | accept an invitation bound to an address; suggest a notification address | contract | the session |
| Notification address, email on/off, quiet hours | `notification_preference` | send the notifications the person chose | contract | until changed or the account is deleted |
| Spaces and memberships, roles in a space | `space`, `space_member` | sharing a garden | contract | until the person leaves or the account is deleted |
| Invitations (invited login or email address, who invited) | `invitation` | joining a space | contract | the address is cleared when the invitation is accepted, revoked or expires; the row is deleted 30 days after |
| Gardens (name, town, country, coordinates rounded to 0.01°, about 1 km), plots, plantings with optional notes | `garden`, `plot`, `planting` | garden questions, weather, alerts | contract | until deleted by the space, or with the space |
| Questions (free text) and answers, who asked and who answered | `pipeline_run`, `pending_escalation` | history, escalations to a person | contract | 12 months; resolved escalations 90 days |
| Notifications | `notification` | in-app bell and email digest | contract | 90 days after they are resolved or read; weather alerts 90 days, read or not |
| Usage records (who, when, calls, tokens, cost) and personal limits | `usage_record`, `usage_limit` | fair use, cost control | legitimate interest | 12 months |
| Knowledge proposals (who proposed, who reviewed, source, note) | `knowledge_proposal`, Neo4j | attribution of contributed facts | legitimate interest | as long as the fact is in use; the login is replaced when the account is deleted |
| App roles with who granted and revoked them | `app_role_grant` | accountability of administration | legitimate interest | kept; the login is replaced when the account is deleted |
| Weather forecasts and recent past hours for a rounded position | `weather_location`, `weather_hour` | weather in answers and alerts | contract | 30 days |
| Application logs (logins in security events: invitations, joins; masked email addresses) | the host's logs | security, troubleshooting | legitimate interest | set by the host (#12) |

Nothing is used for advertising, profiling or sold. The only cookie is the session cookie;
the colour scheme choice stays in the browser's localStorage. Both are strictly necessary or chosen by the person, so no consent banner is needed.

## Who receives it

| Service | Receives | Role and terms | Where | Retention, training |
|---|---|---|---|---|
| Anthropic (Claude) | the question text, the facts and notes for the answer | processor; DPA incorporated into the Commercial Terms, with EU SCCs | USA | API inputs and outputs deleted within 30 days (up to 2 years if flagged for a usage policy violation); no training on customer content |
| TypeSafe (Jev) | the understood intent (kind, subject, zone, month) and the facts; never the question text or the login. The subject may be a plot name | processor; DPA (24 April 2026) part of the agreement, with EU SCCs (module 2) | USA | "as long as reasonably necessary"; no training on inputs; zero data retention available at no cost (to enable on the owner's account) |
| GitHub | sign-in (OAuth, scopes `read:user`, `user:email`) | independent controller of the person's GitHub account | USA, EU-US Data Privacy Framework | GitHub's own policy |
| Apple iCloud Mail | notification and invitation emails (address, text) | consumer terms, no DPA; "designed and intended for personal use" | Ireland / USA | Apple's own policy. Accepted for the prototype; replaced by a transactional email service before opening beyond invited friends (#12) |
| GeoNames (Unxos GmbH) | place names typed in the garden dialog; rounded coordinates for the nearest town | no published request logging policy | Switzerland (EU adequacy decision) | unknown |
| MET Norway | rounded coordinates | logs the caller's IP (the server's, not the person's) and coordinates | Norway (EEA) | MET Norway's own policy |
| The hosting provider | everything stored | processor, chosen in #12 | to decide (EU preferred) | |

The person's browser talks only to Rootology, plus GitHub for sign-in. The browser's position
(`navigator.geolocation`) is read only when the person presses the button, and rounded to 0.01° before the server sends it to any service.

## How it is applied

- `privacy.RetentionJob` runs every night at 3:30 Italian time (`rootology.retention`) and deletes old runs,
  answered escalations, read or resolved notifications and old weather alerts, usage records and closed invitations.
- `privacy.AccountDeletion` deletes an account in one transaction, through a small class in each module
  (`SpacePersonalData`, `GardenPersonalData`, `RunPersonalData`, `EscalationPersonalData`,
  `NotificationPersonalData`, `UsagePersonalData`, `ProposalPersonalData`, `RolePersonalData`); space facts leave
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
- Transfers to the USA rely on the processors' SCCs and the Data Privacy Framework.

## Open questions for a professional

1. The legal basis for a free service to a few invited people that may become commercial: is "contract" right, and does anything need consent?
2. Is a record of processing (art. 30) needed at this size, and is this document enough as one?
3. Are the processors' online DPAs (Anthropic, TypeSafe) sufficient as accepted, or is a signed copy needed?
4. Is a transfer impact assessment needed for Anthropic and TypeSafe (USA)?
5. The retention periods above: are they proportionate?
6. Logins in shared records replaced by "deleted user" after deletion: is that enough, or must those rows go too?
