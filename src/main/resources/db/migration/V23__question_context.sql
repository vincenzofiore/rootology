-- The context a question was answered in (#55), as a snapshot of names and values (never the pipeline's
-- internal types): the space and garden names, the zone and the month with where they came from, the plot.
-- A row stored before has none: the history shows no context for it.
alter table pipeline_run add column context_json text;
alter table pending_escalation add column context_json text;
