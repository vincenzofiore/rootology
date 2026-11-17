-- A display name chosen by each person (#106): the login stays the key everywhere and is not shown to other
-- people any more. Empty until chosen, and then shown as the login. name_asked_at records that the person saw
-- the first-sign-in prompt, chose or skipped, so it is shown once.
alter table app_user add column display_name varchar(40);
alter table app_user add column name_asked_at timestamp with time zone;
create unique index app_user_display_name_key on app_user (lower(display_name));
