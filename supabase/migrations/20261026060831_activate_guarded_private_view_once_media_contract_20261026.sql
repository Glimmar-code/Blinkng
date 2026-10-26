-- Production activation recorded with migration name activate_guarded_private_view_once_media_contract_20261026.
-- This matches the earlier staged migration and is idempotent on already-upgraded databases.
-- No public direct Storage access; RPCs remain available only to service_role.
-- See scripts/testing/chat_media_contract_assertions.sql for replay and isolation checks.
set local lock_timeout='5s';
set local statement_timeout='30s';
-- Stage-only, server-owned single-view image contract.
-- Must be exercised against disposable PostgreSQL/Auth/Storage before deployment.
-- No ordinary user gets Storage SELECT or an image signed URL.
create schema if not exists blink_chat_internal;
revoke all on schema blink_chat_internal from public, anon, authenticated;
grant usage on schema blink_chat_internal to service_role;

create table if not exists blink_chat_internal.view_once_images (
    message_id uuid primary key references public.messages(id) on delete cascade,
    conversation_id uuid not null,
    sender_id uuid not null,
    storage_path text not null unique,
    mime_type text not null
        check (mime_type in ('image/jpeg','image/png','image/webp','image/heic','image/heif')),
    consumed_by uuid,
    consumed_at timestamptz,
    created_at timestamptz not null default now(),
    check ((consumed_by is null) = (consumed_at is null)),
    check (length(storage_path) < 512)
);
alter table blink_chat_internal.view_once_images enable row level security;
revoke all on blink_chat_internal.view_once_images from public, anon, authenticated;
grant select, insert, update, delete on blink_chat_internal.view_once_images to service_role;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values (
    'message-view-once','message-view-once',false,15728640,
    array['image/jpeg','image/png','image/webp','image/heic','image/heif']::text[]
) on conflict (id) do update set
    public=false,
    file_size_limit=15728640,
    allowed_mime_types=excluded.allowed_mime_types;

-- A restrictive policy prevents any older broad/permissive Storage policy
-- from exposing one-time image bytes to anonymous or authenticated clients.
-- Service-role Storage operations bypass RLS; all clients must use the Edge API.
do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname='storage' and tablename='objects'
          and policyname='blink_view_once_private_restrictive_v1'
    ) then
        create policy blink_view_once_private_restrictive_v1
        on storage.objects as restrictive for all to anon, authenticated
        using (bucket_id <> 'message-view-once')
        with check (bucket_id <> 'message-view-once');
    end if;
end $$;

alter table public.messages
    add column if not exists image_view_once boolean not null default false;
do $$
begin
    if not exists (
        select 1 from pg_constraint where conname='blink_view_once_image_only_v1'
        and conrelid='public.messages'::regclass
    ) then
        alter table public.messages add constraint blink_view_once_image_only_v1
        check (not image_view_once or
            (message_type='image' and media_url is null));
    end if;
end $$;

create or replace function public.blink_register_view_once_image(
    p_message_id uuid, p_conversation_id uuid, p_sender_id uuid,
    p_storage_path text, p_mime_type text
) returns boolean language plpgsql security definer
set search_path = pg_catalog, public, blink_chat_internal
as $$
begin
    if p_storage_path !~ (
         '^conversations/' || p_conversation_id::text || '/users/' ||
         p_sender_id::text || '/' || p_message_id::text || '[.](jpg|png|webp|heic|heif)$'
    ) then
        raise exception 'Unexpected private picture path';
    end if;
    if not exists (
        select 1 from public.messages m
        where m.id=p_message_id and m.conversation_id=p_conversation_id
            and m.sender_id=p_sender_id and m.image_view_once=true
            and m.message_type='image' and m.media_url is null
    ) then
        raise exception 'Message was not authorized';
    end if;
    insert into blink_chat_internal.view_once_images(
        message_id,conversation_id,sender_id,storage_path,mime_type
    ) values(p_message_id,p_conversation_id,p_sender_id,p_storage_path,p_mime_type);
    return true;
end;
$$;
revoke all on function public.blink_register_view_once_image(
    uuid,uuid,uuid,text,text) from public,anon,authenticated;
grant execute on function public.blink_register_view_once_image(
    uuid,uuid,uuid,text,text) to service_role;

create or replace function public.blink_claim_view_once_image(
    p_message_id uuid, p_viewer_id uuid
) returns table(path text,mime_type text) language plpgsql security definer
set search_path = pg_catalog, public, blink_chat_internal
as $$
begin
    return query
    update blink_chat_internal.view_once_images v
    set consumed_by=p_viewer_id,consumed_at=now()
    where v.message_id=p_message_id
      and v.consumed_at is null
      and v.sender_id <> p_viewer_id
      and exists (
          select 1 from public.messages m
          where m.id = v.message_id
            and m.conversation_id = v.conversation_id
            and m.image_view_once = true
            and m.deleted_for_everyone = false
      )
      and exists (
          select 1 from public.conversation_participants cp
          where cp.conversation_id=v.conversation_id
            and cp.user_id=p_viewer_id
      )
    returning v.storage_path,v.mime_type;
end;
$$;
revoke all on function public.blink_claim_view_once_image(uuid,uuid)
    from public,anon,authenticated;
grant execute on function public.blink_claim_view_once_image(uuid,uuid)
    to service_role;

-- Edge Function must validate the user's JWT against Auth on every request,
-- then call these service_role-only RPCs. It must never return signed URLs.

do $$
begin
 if not exists (select 1 from storage.buckets where id='message-view-once' and public=false and file_size_limit=15728640 and allowed_mime_types @> array['image/jpeg','image/png','image/webp','image/heic','image/heif']::text[])
   then raise exception 'view-once storage restrictions missing'; end if;
 if has_function_privilege('anon','public.blink_claim_view_once_image(uuid,uuid)','execute')
   or has_function_privilege('authenticated','public.blink_claim_view_once_image(uuid,uuid)','execute')
   or has_function_privilege('anon','public.blink_register_view_once_image(uuid,uuid,uuid,text,text)','execute')
   or has_function_privilege('authenticated','public.blink_register_view_once_image(uuid,uuid,uuid,text,text)','execute')
   then raise exception 'view-once RPC privilege leak'; end if;
 if has_schema_privilege('authenticated','blink_chat_internal','usage')
   then raise exception 'private schema privilege leak'; end if;
end $$;
