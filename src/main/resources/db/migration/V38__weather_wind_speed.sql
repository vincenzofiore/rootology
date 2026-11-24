-- The mean wind speed of a forecast step in m/s (#137), for the strong wind alert. MET Norway's compact
-- endpoint gives no gusts, so only the mean is stored; null when the forecast gives none.
alter table weather_hour add column wind_speed_ms double precision;
