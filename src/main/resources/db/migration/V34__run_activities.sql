-- How each value of a sowing or planting answer goes into the ground (#112): in a seedbed, transplanted,
-- sown or planted, as JSON of the answer's activities. Rows stored before have none.
alter table pipeline_run add column activities_json text;
