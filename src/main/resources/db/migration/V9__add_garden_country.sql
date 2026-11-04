-- The garden's country (ISO 3166-1 alpha-2), from GeoNames (#30). The sowing calendars and zones of the
-- knowledge base are Italian, so they apply only to gardens in Italy. Gardens saved before have none
-- and are treated as Italian, as every garden was then.
alter table garden add column country_code varchar(2);
