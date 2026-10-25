-- Disposable PostgreSQL 17 fixture ONLY. Never run this on BLINK production.
create role anon nologin;
create role authenticated nologin;
create role service_role nologin bypassrls;
create schema storage;
create table storage.buckets (
    id text primary key,
    name text not null,
    public boolean not null default false,
    file_size_limit bigint,
    allowed_mime_types text[]
);
insert into storage.buckets(id, name, public)
values ('message-media', 'message-media', false);

create table public.messages (
    id uuid primary key,
    conversation_id uuid not null,
    sender_id uuid not null,
    message_type text not null default 'text',
    content text not null default '',
    media_url text,
    deleted_for_everyone boolean not null default false,
    created_at timestamptz not null default now()
);
create table public.conversation_participants (
    conversation_id uuid not null,
    user_id uuid not null,
    primary key(conversation_id, user_id)
);
