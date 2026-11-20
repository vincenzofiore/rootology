-- The garden's altitude in metres (#135): MET Norway adjusts the forecast's air temperature to it, and a
-- garden on a hill gets its own minimum temperatures. Null is a garden whose altitude is unknown, which
-- MET Norway treats as the 1 km topography; it is a key of its own, never a wildcard.
alter table weather_location add column altitude integer;
alter table weather_hour add column altitude integer;

-- The cache is per place and altitude: two gardens at the same rounded point with different altitudes never
-- share a forecast. nulls not distinct makes the altitude part of the key even when it is null, so a garden
-- without one keeps a single entry (the rows stored before #135 are such an entry, and expire naturally).
alter table weather_location drop constraint weather_location_pkey;
alter table weather_location add constraint weather_location_key
    unique nulls not distinct (latitude, longitude, altitude);

alter table weather_hour drop constraint weather_hour_pkey;
alter table weather_hour add constraint weather_hour_key
    unique nulls not distinct (latitude, longitude, altitude, valid_at);
