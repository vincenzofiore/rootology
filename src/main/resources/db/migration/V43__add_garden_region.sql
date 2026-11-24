-- The garden's first-level administrative code from GeoNames (for Italy the region, "01" Abruzzo …
-- "20" Veneto), used for the official MeteoAlarm warnings (#136). Filled from GeoNames when the place is
-- known; null when unknown. Gardens saved before this migration are filled by the background lookup.
alter table garden add column region_code varchar(3);
