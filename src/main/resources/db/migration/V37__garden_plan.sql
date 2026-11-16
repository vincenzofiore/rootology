-- The garden plan (#90, step 1): a rectangle in metres, and each area placed on it as a box with its size.
-- All optional: a garden without a plan, an area not placed yet. An area's position is its top-left corner
-- from the plan's top-left, and the four values come together or not at all.
alter table garden add column plan_width_m numeric(6, 2) check (plan_width_m > 0);
alter table garden add column plan_depth_m numeric(6, 2) check (plan_depth_m > 0);
alter table plot add column plan_x_m numeric(6, 2) check (plan_x_m >= 0);
alter table plot add column plan_y_m numeric(6, 2) check (plan_y_m >= 0);
alter table plot add column plan_width_m numeric(6, 2) check (plan_width_m > 0);
alter table plot add column plan_depth_m numeric(6, 2) check (plan_depth_m > 0);
alter table plot add constraint plot_plan_complete check (
    (plan_x_m is null and plan_y_m is null and plan_width_m is null and plan_depth_m is null)
    or (plan_x_m is not null and plan_y_m is not null and plan_width_m is not null and plan_depth_m is not null));
