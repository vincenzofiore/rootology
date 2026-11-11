-- The garden's altitude from GeoNames GTOPO30 (#62), used by the climate profile. Null when not known;
-- gardens saved before this migration get it on their next save, with no startup job.
ALTER TABLE garden ADD COLUMN elevation_m INTEGER;
