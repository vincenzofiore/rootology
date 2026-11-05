-- A space fact, or a global proposal still waiting for review, can be withdrawn by a member of its space (#24).
alter table knowledge_proposal drop constraint knowledge_proposal_status_check;
alter table knowledge_proposal add constraint knowledge_proposal_status_check
    check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'));
alter table knowledge_proposal add column withdrawn_by varchar(100);
alter table knowledge_proposal add column withdrawn_at timestamp with time zone;
