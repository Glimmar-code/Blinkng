-- Production activation recorded on 2026-10-26 through the authenticated Supabase migration tool.
-- Additive schema only: existing RLS policies, messaging rows and Storage permissions are preserved.
-- Keep this migration idempotent with the earlier staged 20261025100000 script.
set local lock_timeout = '5s';
set local statement_timeout = '30s';
-- BLINK chat media: additive backwards-compatible schema.
-- Stage and validate in isolated Supabase before promotion to production.
-- Existing non-picture messages and older clients continue to work unchanged.
alter table public.messages
    add column if not exists image_album_id uuid,
    add column if not exists image_album_position smallint,
    add column if not exists image_album_size smallint,
    add column if not exists image_preview_url text;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname='blink_chat_album_invariants_v1'
          and conrelid='public.messages'::regclass
    ) then
        alter table public.messages
        add constraint blink_chat_album_invariants_v1 check (
            (image_album_id is null and image_album_position is null and image_album_size is null)
            or
            (message_type='image'
             and image_album_id is not null
             and image_album_position between 0 and 9
             and image_album_size between 2 and 10
             and image_album_position < image_album_size)
        );
    end if;
    if not exists (
        select 1 from pg_constraint
        where conname='blink_chat_preview_owner_v1'
          and conrelid='public.messages'::regclass
    ) then
        alter table public.messages
        add constraint blink_chat_preview_owner_v1 check (
            image_preview_url is null or (
                message_type='image'
                and media_url like 'message-media:conversations/%'
                and image_preview_url like
                    ('message-media:conversations/' || conversation_id::text ||
                     '/users/' || sender_id::text || '/preview/%')
                and char_length(image_preview_url) <= 512
            )
        );
    end if;
end $$;

create index if not exists blink_chat_album_group_v1
    on public.messages (conversation_id, image_album_id, image_album_position)
    where image_album_id is not null;

create index if not exists blink_chat_conversation_photos_v1
    on public.messages (conversation_id, created_at desc, id desc)
    where message_type='image' and deleted_for_everyone=false;


do $$
begin
    if (select count(*) from information_schema.columns
        where table_schema='public' and table_name='messages'
          and column_name in ('image_album_id','image_album_position','image_album_size','image_preview_url')) <> 4
    then raise exception 'BLINK photo schema verification failed'; end if;
    if not (select relrowsecurity from pg_class where oid='public.messages'::regclass)
    then raise exception 'messages RLS unexpectedly disabled'; end if;
    if not exists(select 1 from storage.buckets where id='message-media' and public=false
        and file_size_limit = 83886080 and cardinality(allowed_mime_types) = 24)
    then raise exception 'BLINK private media bucket restrictions missing'; end if;
end $$;
