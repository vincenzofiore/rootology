-- #94: a question's notification stores its data, not an English sentence built by the service: who asked,
-- and the question itself in body. The Notifications page and the email digest word them in the reader's
-- language, and an answer's notification needs no body at all (resolved_by holds who answered).
-- Rows stored before keep their sentence and have asked_by null, which is how the two are told apart.
alter table notification add column asked_by varchar(100);
alter table notification alter column body drop not null;
