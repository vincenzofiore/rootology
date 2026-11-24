-- #165: the domain filter was asked but could not judge the question (Jev unreachable), so the run went on
-- unscreened (#161). Kept with the run so the nightly signal can leave such runs out of its counts.
alter table pipeline_run add column domain_unjudged boolean not null default false;
