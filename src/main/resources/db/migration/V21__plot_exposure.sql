-- Sun and wind exposure of a plot (#54): declared by the gardener, not measured, and all optional.
alter table plot add column orientation varchar(2)
    check (orientation in ('N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'));
alter table plot add column direct_sun varchar(20)
    check (direct_sun in ('FULL_SUN', 'PART_SHADE', 'SHADE'));
alter table plot add column wind varchar(20)
    check (wind in ('SHELTERED', 'EXPOSED'));
-- For balconies and terraces: the floor, as the building numbers it.
alter table plot add column floor integer check (floor between 0 and 200);
