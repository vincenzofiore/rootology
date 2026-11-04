-- Invitations by email (#25): the invited address, kept only while the invitation is open; it is cleared
-- when the invitation is accepted, revoked or expired. Only someone with that address verified may accept.
alter table invitation add column invited_email varchar(254);
