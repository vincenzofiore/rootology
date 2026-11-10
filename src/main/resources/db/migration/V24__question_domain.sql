-- #53: a question the domain filter refused is answered with a fixed text and kept in the history,
-- marked here so the row is readable without guessing from an empty answer.
alter table pipeline_run add column off_domain boolean not null default false;

-- A refused question never reached Claude, so there is no sentence to store.
alter table pipeline_run alter column answer_text drop not null;
