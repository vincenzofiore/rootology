-- Members page (#105): an owner blocks a member instead of removing them. A blocked member stays listed as
-- blocked, sees and uses nothing of the space, keeps their past questions attributed, and can be unblocked.
alter table space_member add column blocked_at timestamp with time zone;
alter table space_member add column blocked_by varchar(100);

-- A change of membership is a notification (#105): a role changed, a block or unblock (to the member), a
-- member who left (to the owners). event says which ("ROLE:GUEST", "BLOCKED", "UNBLOCKED", "LEFT"), asked_by
-- holds who acted, body the optional message they wrote, and space_name the space's name then, since a blocked
-- member no longer sees the space.
alter table notification add column event varchar(40);
alter table notification add column space_name varchar(200);
alter table notification drop constraint notification_kind_check;
alter table notification add constraint notification_kind_check
    check (kind in ('ESCALATION_WAITING', 'ESCALATION_ANSWERED', 'WEATHER_ALERT', 'ACCESS_REQUEST', 'MEMBERSHIP'));
