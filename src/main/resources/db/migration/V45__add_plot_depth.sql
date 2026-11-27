-- Soil depth of an area (#186): optional, whole centimetres from 5 to 200. Only a pot, a planter and a raised
-- bed carry one; in open ground and under a greenhouse the field is not shown and the value is cleared, since
-- container-depth-v1 only asks how deep the soil of a container is. Declared by the gardener, never measured.
alter table plot add column depth_cm integer check (depth_cm between 5 and 200);
