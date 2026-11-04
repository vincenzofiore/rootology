-- GUEST (#33): a plain observer of a space, who sees everything and changes nothing.
alter table space_member drop constraint space_member_role_check;
alter table space_member add constraint space_member_role_check check (role in ('OWNER', 'MEMBER', 'GUEST'));
alter table invitation drop constraint invitation_role_check;
alter table invitation add constraint invitation_role_check check (role in ('OWNER', 'MEMBER', 'GUEST'));
