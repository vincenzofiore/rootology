-- The garden a question belongs to (#56), its working context. A null garden is the space level: the
-- questions asked outside a garden, the rows stored before this item in spaces with several gardens, and
-- the rows of a deleted garden (on delete set null, so nothing is lost).
alter table pipeline_run add column garden_id bigint references garden (id) on delete set null;
alter table pending_escalation add column garden_id bigint references garden (id) on delete set null;
alter table notification add column garden_id bigint references garden (id) on delete set null;

-- Rows stored before: the space's garden when it has exactly one, otherwise none.
update pipeline_run r set garden_id = g.id
from garden g
where g.space_id = r.space_id
  and (select count(*) from garden g2 where g2.space_id = r.space_id) = 1;

update pending_escalation p set garden_id = g.id
from garden g
where g.space_id = p.space_id
  and (select count(*) from garden g2 where g2.space_id = p.space_id) = 1;

update notification n set garden_id = g.id
from garden g
where g.space_id = n.space_id
  and (select count(*) from garden g2 where g2.space_id = n.space_id) = 1;

create index pipeline_run_garden on pipeline_run (space_id, garden_id, finished_at desc);
create index pending_escalation_garden on pending_escalation (space_id, garden_id) where resolved_at is null;
