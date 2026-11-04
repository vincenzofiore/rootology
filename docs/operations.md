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
