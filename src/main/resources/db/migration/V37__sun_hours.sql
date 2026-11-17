-- Hours of direct sun of an area, by season (#89): optional, whole hours from 0 to 16, summer (April to
-- September) and winter (October to March). When a season has its hours, light-need-v1 reads the level from
-- them (6 or more full sun, 3 to 5 part shade, less than 3 shade); otherwise the declared level, as before.
-- Who last declared the area's sun (level or hours) and when, as for other declared facts.
alter table plot add column sun_hours_summer integer check (sun_hours_summer between 0 and 16);
alter table plot add column sun_hours_winter integer check (sun_hours_winter between 0 and 16);
alter table plot add column sun_declared_by varchar(100);
alter table plot add column sun_declared_at timestamp with time zone;
