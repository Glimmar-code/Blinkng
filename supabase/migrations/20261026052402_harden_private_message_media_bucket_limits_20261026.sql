-- Reconciles Supabase-hosted migration 20261026052402.
-- Executed in BLINK production 26 Oct 2026; replay-safe after the 25 Oct
-- chat-media migration. This never makes a private bucket public.
begin;
set local lock_timeout='5s';
set local statement_timeout='30s';
do $guard$
begin
  if (select count(*) from storage.buckets where id='message-media' and public=false) <> 1 then
    raise exception 'BLINK_MESSAGE_MEDIA_MUST_EXIST_AND_BE_PRIVATE';
  end if;
  if exists(select 1 from storage.objects where bucket_id='message-media'
      and (coalesce(lower(metadata->>'mimetype'),'') <> all(array['image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'image/heic',
    'image/heif',
    'video/mp4',
    'video/webm',
    'video/quicktime',
    'video/3gpp',
    'audio/mp4',
    'audio/m4a',
    'audio/aac',
    'audio/3gpp',
    'audio/mpeg',
    'application/pdf',
    'text/plain',
    'text/csv',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation']::text[])
           or coalesce((metadata->>'size')::numeric,0) > 83886080)) then
    raise exception 'BLINK_EXISTING_OBJECTS_REQUIRE_MANUAL_REVIEW';
  end if;
end;
$guard$;
update storage.buckets set file_size_limit=83886080,
  allowed_mime_types=array['image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'image/heic',
    'image/heif',
    'video/mp4',
    'video/webm',
    'video/quicktime',
    'video/3gpp',
    'audio/mp4',
    'audio/m4a',
    'audio/aac',
    'audio/3gpp',
    'audio/mpeg',
    'application/pdf',
    'text/plain',
    'text/csv',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation']::text[]
where id='message-media' and public=false;
do $verify$
begin
  if not exists(select 1 from storage.buckets
      where id='message-media' and public=false
        and file_size_limit=83886080
        and cardinality(allowed_mime_types)=24
        and allowed_mime_types @> array['image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'image/heic',
    'image/heif',
    'video/mp4',
    'video/webm',
    'video/quicktime',
    'video/3gpp',
    'audio/mp4',
    'audio/m4a',
    'audio/aac',
    'audio/3gpp',
    'audio/mpeg',
    'application/pdf',
    'text/plain',
    'text/csv',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation']::text[]) then
    raise exception 'BLINK_MEDIA_BUCKET_LIMITS_NOT_ENFORCED';
  end if;
end;
$verify$;
commit;
