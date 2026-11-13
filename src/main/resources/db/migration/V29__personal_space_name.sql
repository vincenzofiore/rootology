-- Personal spaces are named after their owner (#79). The old default was "<login>'s garden", which read as
-- a garden (not a space) and in English inside an Italian UI. Only that exact default is renamed, so a name
-- the owner chose is never touched.
update space s
set name = u.login
from app_user u
where s.personal_of = u.id
  and s.name = u.login || '''s garden';
