-- The garden's time zone, an IANA id such as "Europe/Paris", from GeoNames when its coordinates are saved
-- (#158): the garden's local time comes from its coordinates, not from the person asking. Null when unknown,
-- and the app's own zone (Europe/Rome) is used. Gardens saved before this migration get it on their next save.
alter table garden add column time_zone varchar(64);
