-- An approved global fact outlives the space it was proposed in (#40): when that space is deleted with an
-- account, the fact stays for everyone, with no space and the proposer's login replaced.
alter table knowledge_proposal alter column space_id drop not null;
