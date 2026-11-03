-- A person can be added to a space before they ever sign in (#15). Their personal space is
-- created on their first real login, so that moment is recorded.
alter table app_user add column first_login_at timestamp with time zone;

-- Everyone who already owns a space has signed in before.
update app_user u set first_login_at = u.created_at
where exists (select 1 from space_member m where m.user_id = u.id and m.role = 'OWNER');

-- GitHub logins are case-insensitive: store them in lower case.
update app_user set github_login = lower(github_login);
