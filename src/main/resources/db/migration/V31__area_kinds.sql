-- Areas (#85): the kinds of a growing unit are open field, raised bed, planter, pot and greenhouse.
-- The old CONTAINER becomes POT: the word stays `plot` in the database, only its values change.
alter table plot drop constraint plot_kind_check;
update plot set kind = 'POT' where kind = 'CONTAINER';
alter table plot add constraint plot_kind_check
    check (kind in ('GROUND', 'RAISED_BED', 'PLANTER', 'POT', 'GREENHOUSE'));
