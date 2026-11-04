# Operations

Steps done by hand on an environment, outside the code. Data about real people (who is admin, who
signs in) never goes into the repository, which is public.

## First admin of an environment (#32)

App roles live in `app_role_grant`. The first admin is inserted once per environment, after Flyway has
created the table (start the app once); every later role is granted in the Admin view by an admin.
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
