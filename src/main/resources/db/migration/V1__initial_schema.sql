-- Buoy initial schema.
-- Conventions: bigint identity PKs; enums as varchar + CHECK (portable, no ALTER TYPE pain);
-- timestamptz everywhere; rule/clause documents as JSONB (Unleash-style) so a flag config
-- mutates atomically under one optimistic-lock version.

create table project (
    id          bigint generated always as identity primary key,
    key         varchar(100) not null unique,
    name        varchar(200) not null,
    description text,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table environment (
    id         bigint generated always as identity primary key,
    project_id bigint       not null references project (id) on delete cascade,
    key        varchar(100) not null,
    name       varchar(200) not null,
    created_at timestamptz  not null default now(),
    constraint uq_environment_project_key unique (project_id, key)
);

create table flag (
    id          bigint generated always as identity primary key,
    project_id  bigint       not null references project (id) on delete cascade,
    key         varchar(100) not null,
    name        varchar(200) not null,
    description text,
    type        varchar(20)  not null default 'BOOLEAN' check (type in ('BOOLEAN')),
    tags        jsonb        not null default '[]',
    archived    boolean      not null default false,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uq_flag_project_key unique (project_id, key)
);

create table flag_config (
    id                bigint  generated always as identity primary key,
    flag_id           bigint      not null references flag (id) on delete cascade,
    environment_id    bigint      not null references environment (id) on delete cascade,
    enabled           boolean     not null default false,
    rules             jsonb       not null default '[]',
    default_variation boolean     not null default true,
    off_variation     boolean     not null default false,
    version           bigint      not null default 0,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    constraint uq_flag_config_flag_environment unique (flag_id, environment_id)
);

create table segment (
    id          bigint generated always as identity primary key,
    project_id  bigint       not null references project (id) on delete cascade,
    key         varchar(100) not null,
    name        varchar(200) not null,
    description text,
    clauses     jsonb        not null default '[]',
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    constraint uq_segment_project_key unique (project_id, key)
);

create table api_key (
    id             bigint generated always as identity primary key,
    environment_id bigint       not null references environment (id) on delete cascade,
    kind           varchar(20)  not null check (kind in ('SERVER_SDK', 'ADMIN')),
    name           varchar(200) not null,
    token_hash     varchar(64)  not null unique,
    token_prefix   varchar(20)  not null,
    created_at     timestamptz  not null default now(),
    revoked_at     timestamptz
);

create table app_user (
    id             bigint generated always as identity primary key,
    username       varchar(100) not null unique,
    password_hash  varchar(100) not null,
    display_name   varchar(200),
    instance_admin boolean      not null default false,
    created_at     timestamptz  not null default now(),
    updated_at     timestamptz  not null default now()
);

create table project_member (
    id         bigint generated always as identity primary key,
    project_id bigint      not null references project (id) on delete cascade,
    user_id    bigint      not null references app_user (id) on delete cascade,
    role       varchar(20) not null check (role in ('OWNER', 'EDITOR', 'VIEWER')),
    created_at timestamptz not null default now(),
    constraint uq_project_member unique (project_id, user_id)
);

-- Audit entries are immutable and must outlive the entities they describe:
-- ids and keys are recorded as plain values, deliberately without foreign keys.
create table audit_log_entry (
    id              bigint generated always as identity primary key,
    occurred_at     timestamptz  not null default now(),
    actor_type      varchar(20)  not null check (actor_type in ('USER', 'API_KEY', 'SYSTEM')),
    actor_name      varchar(200) not null,
    action          varchar(20)  not null check (action in ('CREATED', 'UPDATED', 'DELETED')),
    entity_type     varchar(40)  not null,
    entity_id       bigint       not null,
    entity_key      varchar(100),
    project_id      bigint,
    environment_id  bigint,
    diff            jsonb,
    source          varchar(200)
);

create index idx_audit_project_time on audit_log_entry (project_id, occurred_at desc);
create index idx_flag_config_environment on flag_config (environment_id);
