-- The forecast's weather symbol per step (#31), for the weather card's icons (MET Norway's symbol codes).
alter table weather_hour add column symbol_code varchar(40);
