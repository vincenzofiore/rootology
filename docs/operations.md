# Operations

Steps done by hand on an environment, outside the code. Data about real people (who is admin, who
signs in) never goes into the repository, which is public.

## First admin of an environment (#32)

App roles live in `app_role_grant`. The first admin is inserted once per environment, after Flyway has
created the table (start the app once) and **after that person has signed in once**: a login with no person
behind it could otherwise be given to someone signing in with Google, whose login is derived from their email
(#28). Every later role is granted in the Admin view by an admin.
GitHub logins are stored in lower case.

```sql
insert into app_role_grant (github_login, role, granted_by, granted_at)
values ('<github-login-in-lower-case>', 'ADMIN', 'manual', now());
```

Local development (Docker Compose):

```sh
docker exec -it rootology-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Roles become Spring Security authorities at sign-in, so sign out and in again after a change. There is
always at least one admin: the app refuses to revoke the last one.

## Sending email (#25)

Without SMTP settings, every email goes to Mailpit (started from `compose.yaml`), visible on
http://localhost:8025 and never delivered. Any mailbox that allows SMTP with an app password works; the
code only reads these variables.

**iCloud Mail** (the owner's choice for now; https://support.apple.com/en-us/102525): server
`smtp.mail.me.com`, port 587 with STARTTLS, the full iCloud address as username and an app-specific
password generated on https://account.apple.com (needs two-factor authentication on the Apple ID, no SMS).
Limits: 1000 messages and 1000 recipients a day (https://support.apple.com/en-us/102198); iCloud Mail is
meant for personal use and forbids unsolicited bulk email, which a few invitations are not. To keep the
personal address private, send from an iCloud Mail alias (check in iCloud Mail's settings that sending
from it is allowed).

```sh
export ROOTOLOGY_SMTP_HOST=smtp.mail.me.com
export ROOTOLOGY_SMTP_PORT=587
export ROOTOLOGY_SMTP_AUTH=true
export ROOTOLOGY_SMTP_STARTTLS=true
export ROOTOLOGY_SMTP_USERNAME=<the iCloud address>
export ROOTOLOGY_SMTP_PASSWORD=<the app-specific password>
export ROOTOLOGY_SMTP_FROM="Rootology <the alias>"
```

Gmail works the same way (`smtp.gmail.com`, 587, an app password, about 500 emails a day), but creating an
account needs an SMS verification. Set the variables in the environment (for example `~/.zshrc`), never in
the repository. `ROOTOLOGY_BASE_URL` is the address links in emails point to (default
http://localhost:8080). With a domain (#12), move to a transactional service and set SPF, DKIM and DMARC.

## Production server (#12)

One small server (Hetzner Cloud CX23: 2 vCPU, 4 GB, Ubuntu 26.04 LTS, Germany), created with
`deploy/cloud-init.yaml` (the owner's public key pasted in): user `rootology` signs in with that key only,
no passwords and no root login, Docker with Compose, fail2ban, automatic security updates. Hetzner's
firewall allows only TCP 22, 80, 443 and ICMP in. The domain `rootology.app` (OVHcloud, DNSSEC on) points to
the server with an `A` and an `AAAA` record. Server address and keys stay out of this repository.

`deploy/compose.prod.yaml` runs Caddy (HTTPS from Let's Encrypt, HTTP redirected), the app image
`ghcr.io/vincenzofiore/rootology` (built by the `publish` job of `.github/workflows/build.yml` once the tests pass on `main`),
Postgres and Neo4j. Only Caddy publishes ports. The app runs with the `prod` profile
(`application-prod.yaml`: forwarded headers from Caddy, `Secure` cookie; Spring then sends HSTS).

### First start

1. A separate GitHub OAuth App for production: homepage `https://rootology.app`, callback
   `https://rootology.app/login/oauth2/code/github`. Production keys for Anthropic and TypeSafe, separate
   from the development ones.
   Google sign-in (#28), optional: a Google Cloud project with the Google Auth Platform set up (External
   audience, branding with home page `https://rootology.app` and privacy policy `https://rootology.app/privacy`,
   `rootology.app` as authorized domain, verified in Search Console by a DNS TXT record at OVH that must stay
   there), only the non-sensitive scopes `openid`, `email` and `profile`, published "In production". One web
   client per environment: production with callback `https://rootology.app/login/oauth2/code/google`,
   development with `http://localhost:8080/login/oauth2/code/google`. Its id and secret go in
   `ROOTOLOGY_GOOGLE_CLIENT_ID` and `ROOTOLOGY_GOOGLE_CLIENT_SECRET`; without them the Google buttons are not
   shown. `ROOTOLOGY_ALLOWED_GOOGLE_EMAILS` stays empty in production: people come in by invitation, and
   whoever already signs in with GitHub links Google from My account. To rotate the secret, add a new one to
   the client in the console, update `.env`, `up -d`, then delete the old one.
2. On the server:
   ```sh
   git clone https://github.com/vincenzofiore/rootology.git ~/rootology
   cd ~/rootology/deploy
   cp .env.example .env && chmod 600 .env   # then fill it in: passwords from `openssl rand -base64 24`
   docker compose -f compose.prod.yaml up -d
   docker compose -f compose.prod.yaml logs -f app   # until "Started RootologyApplication"
   ```
   Caddy gets the certificate on the first request to `https://rootology.app`.
3. The first admin, as in "First admin of an environment" above:
   ```sh
   docker compose -f compose.prod.yaml exec postgres psql -U rootology -d rootology
   ```

### Releases

Each change people or the server notice goes into `CHANGELOG.md` under `[Unreleased]` as it lands on
`develop`. Before a release, that section is renamed `[X.Y.Z] - <date>` (with its compare link) and a new
empty `[Unreleased]` is opened; after the tag, the section becomes the text of the GitHub Release, above the
generated notes.

A release is a tag `vX.Y.Z` on a commit of `main`, made by the owner once `main` is updated:
```sh
git fetch origin && git tag -a v0.1.0 -m "Rootology 0.1.0" origin/main && git push origin v0.1.0
```
(or GitHub's Releases page, "Draft a new release", new tag on `main`). The `build` workflow tests it, builds
the jar with `-Drevision=X.Y.Z`, pushes the image as `ghcr.io/vincenzofiore/rootology:X.Y.Z` and creates the
GitHub Release with notes generated from the pull requests and commits. A tag that is not on `main` is
refused. Then set `revision` in `pom.xml` to the next `X.Y.Z-SNAPSHOT` on `develop`.

### Updating

The owner updates `main`; once its tests pass, the `publish` job pushes the new image. Then on the server:
```sh
cd ~/rootology && git pull && cd deploy
docker compose -f compose.prod.yaml pull && docker compose -f compose.prod.yaml up -d
```
Which version runs is `APP_VERSION` in `.env`: a release (`0.1.0`), `latest` (the head of `main`) or a commit sha; to roll back, set the previous release and `up -d` again (Flyway migrations only move forward, so a rollback across a migration needs the backup taken before it). Flyway migrates the database at start.

### Backups

`deploy/backup.sh` dumps Postgres every night (1:30 UTC, the server's time zone: before the app's retention job at 3:30 Italian time) and keeps 14 days; with `BACKUP_REMOTE` set, it mirrors them
off the server. In the `rootology` user's crontab (`crontab -e`):
```
30 1 * * * BACKUP_REMOTE=... BACKUP_SSH="ssh -p 23" $HOME/rootology/deploy/backup.sh >> $HOME/backup.log 2>&1
```
Restore (stops nothing else; the app may need a restart afterwards):
```sh
docker compose -f compose.prod.yaml exec -T postgres \
    pg_restore -U rootology -d rootology --clean --if-exists < ~/backups/rootology-<stamp>.dump
docker compose -f compose.prod.yaml restart app
```
Neo4j is rebuilt from the curated files at every start of the app, and contributed facts are applied again
from Postgres: it needs no backup.

### Watching it

`https://rootology.app/actuator/health` answers `{"status":"UP"}` without details (database, Neo4j, disk and
the external services; email is left out: it is sent in the background with retries). An external uptime
monitor checks it every few minutes and emails the owner. While Claude, Jev, MET Norway or GeoNames keep
failing (an open circuit, #29) the status is `DEGRADED`, still with HTTP 200, since the app works with its
fallbacks. `/actuator/health/liveness` and `/actuator/health/readiness` (Postgres and Neo4j) are public too.

Logs are one JSON object per line in production (Spring Boot's Logstash format), with a `traceId` per HTTP
request, also sent back as the `X-Trace-Id` header: `docker compose -f compose.prod.yaml logs --since 1h app`,
and `| grep <traceId>` to follow one request. No questions, prompts, coordinates or email addresses in them.

Metrics (#27), for a signed-in admin at `/actuator/metrics/<name>`:

- `rootology.questions`: questions people asked, timed from start to answer or escalation, by `scope`
  (`knowledge-base` for real questions, `suite`) and `outcome` (`answered`, `escalated`: waiting for a
  person, `failed`). The escalation rate is escalated over answered plus escalated:
  `/actuator/metrics/rootology.questions?tag=scope:knowledge-base&tag=outcome:escalated`.
- `rootology.questions.confidence`: the decision layer's confidence.
- `rootology.external.calls`: calls to Claude, Jev, MET Norway and GeoNames, timed, by `service` and
  `outcome` (`success`, `failure`).
- Tokens and cost per person are in `usage_record` (#19), shown in the Admin view.

They are kept in memory since the last start; a place to keep and chart them comes with #27's second part.

Alerts (#27): every minute the app checks the circuits and the disk (`rootology.operations`), and emails the
admins once per event, in Italian: when a service's circuit opens, and when the disk is 90 % full. An admin
receives them only with an address in Notifications (set from GitHub at sign-in); quiet hours do not apply.
