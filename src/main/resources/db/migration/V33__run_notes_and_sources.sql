-- A past answer shows the same card as a fresh one (#110): the notes (frost, late blight, missing data) and the
-- ids of the sources behind the values. Rows stored before have neither.
alter table pipeline_run add column notes_json text;
alter table pipeline_run add column sources text[];
