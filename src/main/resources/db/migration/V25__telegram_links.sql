-- Telegram notifications (#38). A person links their Telegram account once, with a one-time code shown in the
-- app: only the code's SHA-256 is stored, like invitations. Only private chats are ever written: one person,
-- one chat. 'login' is the internal login (GitHub or Google, #28), not always a GitHub login.
create table telegram_link (
    login     varchar(100) primary key,
    chat_id   bigint       not null unique,
    linked_at timestamp with time zone not null,
    -- Set when Telegram no longer delivers to this chat (blocked, deleted): the person is warned in the app.
    failed_at timestamp with time zone
);

create table telegram_link_code (
    code_hash  varchar(64) primary key,
    login      varchar(100) not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    used_at    timestamp with time zone
);

create index telegram_link_code_login on telegram_link_code (login);

-- The same outbox as email (#26), with its own delivery mark: a notification can go out by both channels.
alter table notification add column telegramed_at timestamp with time zone;

-- Per kind, as email_weather is (#37): the person chooses what reaches the phone.
alter table notification_preference add column telegram_escalations boolean not null default true;
alter table notification_preference add column telegram_weather boolean not null default true;
