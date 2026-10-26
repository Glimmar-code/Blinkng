-- BLINK chat media: additive backwards-compatible schema.
-- Stage and validate in isolated Supabase before promotion to production.
-- Existing non-picture messages and older clients continue to work unchanged.
-- A missing or public bucket must not turn this security migration into a
-- silent no-op. Check prior to changing the message schema.
do $
begin
    if not exists (
        select 1 from storage.buckets
        where id='message-media' and public=false
    ) then
        raise exception 'BLINK_MESSAGE_MEDIA_PRIVATE_BUCKET_REQUIRED';
    end if;
end;
$;

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

-- Enforce the current largest allowed chat-media upload at the bucket boundary:
-- videos <= 80 MiB; image <= 15 MiB, voice <= 15 MiB, documents <= 25 MiB
-- are ALSO enforced individually by the clients (but need trusted validation).
-- This global limit must never be mistaken for per-file-type validation.
update storage.buckets
set file_size_limit=83886080,
    allowed_mime_types = array[
       'image/jpeg','image/png','image/webp','image/gif','image/heic','image/heif',
       'video/mp4','video/webm','video/quicktime','video/3gpp',
       'audio/mp4','audio/m4a','audio/aac','audio/3gpp','audio/mpeg',
       'application/pdf','text/plain','text/csv','application/msword',
       'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
       'application/vnd.ms-excel',
       'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
       'application/vnd.ms-powerpoint',
       'application/vnd.openxmlformats-officedocument.presentationml.presentation'
    ]::text[]
where id='message-media' and public=false;

-- Never grant anonymous access to photo references or modify the existing
-- member-scoped Storage SELECT/INSERT RLS policies in this migration.
-- A secure view-once feature requires a separate access-controlled service:
-- normal message-media storage is deliberately NOT marked view-once.

-- Fail closed if the Storage UPDATE did not apply the server-side limits.
-- MIME entries mirror the shared Android/Windows attachment policy.
do $$
begin
    if not exists (
        select 1 from storage.buckets
        where id='message-media'
          and public=false
          and file_size_limit=83886080
          and cardinality(allowed_mime_types)=24
          and allowed_mime_types @> array[
            'image/jpeg','image/png','image/webp','image/gif','image/heic','image/heif',
            'video/mp4','video/webm','video/quicktime','video/3gpp',
            'audio/mp4','audio/m4a','audio/aac','audio/3gpp','audio/mpeg',
            'application/pdf','text/plain','text/csv','application/msword',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.ms-excel',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'application/vnd.ms-powerpoint',
            'application/vnd.openxmlformats-officedocument.presentationml.presentation'
          ]::text[]
    ) then
        raise exception 'BLINK_MESSAGE_MEDIA_BUCKET_CONSTRAINTS_MISSING';
    end if;
end;
$$;
