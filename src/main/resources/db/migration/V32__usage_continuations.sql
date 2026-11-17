-- A question continued after the person chose a place or a garden (#107): the same question again, with the
-- understanding already paid for. Recorded, never counted by the daily limit.
alter table usage_record drop constraint usage_record_kind_check;
alter table usage_record alter column kind type varchar(16);
alter table usage_record add constraint usage_record_kind_check
    check (kind in ('QUESTION', 'REVIEW', 'CONTINUATION'));
