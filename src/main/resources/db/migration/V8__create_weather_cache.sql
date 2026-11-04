-- Weather (#18): MET Norway forecasts per coarse location (0.01 degrees), with the cache headers.
create table weather_location (
    latitude      numeric(6, 2) not null,
    longitude     numeric(6, 2) not null,
    fetched_at    timestamp with time zone not null,
    expires_at    timestamp with time zone not null,
    last_modified varchar(40),
    primary key (latitude, longitude)
);

-- One row per forecast step; a newer forecast overwrites the same hour, so past hours keep the
-- last forecast made before them (an estimate of the recent past until real observations, #21).
create table weather_hour (
    latitude          numeric(6, 2) not null,
    longitude         numeric(6, 2) not null,
    valid_at          timestamp with time zone not null,
    air_temperature   double precision not null,
    relative_humidity double precision,
    precipitation_mm  double precision,
    step_hours        integer not null,
    primary key (latitude, longitude, valid_at)
);
