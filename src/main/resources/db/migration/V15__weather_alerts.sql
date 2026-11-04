-- Weather alerts (#37): a new kind of notification, never repeated for the same event (dedup_key), with
-- its own email preference.
alter table notification drop constraint notification_kind_check;
alter table notification add constraint notification_kind_check check (kind in ('ESCALATION_WAITING', 'WEATHER_ALERT'));
alter table notification add column dedup_key varchar(200);
create unique index notification_dedup on notification (recipient, dedup_key);
alter table notification_preference add column email_weather boolean not null default true;
