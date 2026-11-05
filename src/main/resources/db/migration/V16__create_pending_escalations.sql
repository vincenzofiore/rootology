-- Questions waiting for a person (#36), so they survive a restart: what the paused agent knew, as JSON.
create table pending_escalation (
    process_id    varchar(100) primary key,
    space_id      bigint       not null,
    started_by    varchar(100),
    question_id   varchar(100) not null,
    question_text varchar(2000) not null,
    decision_json text         not null,
    facts_json    text         not null,
    noise_json    text         not null,
    created_at    timestamp with time zone not null,
    resolved_at   timestamp with time zone,
    resolved_by   varchar(100)
);

create index pending_escalation_space on pending_escalation (space_id) where resolved_at is null;
