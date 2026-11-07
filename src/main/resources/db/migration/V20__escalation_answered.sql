-- The asker's notification "your question has an answer" (#51): a new kind of notification.
alter table notification drop constraint notification_kind_check;
alter table notification add constraint notification_kind_check
    check (kind in ('ESCALATION_WAITING', 'ESCALATION_ANSWERED', 'WEATHER_ALERT'));
