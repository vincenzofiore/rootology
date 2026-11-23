-- Account block (#165): an admin blocks a whole account instead of the member of one space. The person keeps
-- everything they have (spaces, gardens, questions, history) and only loses the app: they cannot sign in or
-- use it until an admin unblocks them. Unblocking clears these three columns; who unblocked and when stays in
-- the log only.
alter table app_user add column blocked_at timestamp with time zone;
alter table app_user add column blocked_by varchar(100);
alter table app_user add column blocked_reason varchar(200);
