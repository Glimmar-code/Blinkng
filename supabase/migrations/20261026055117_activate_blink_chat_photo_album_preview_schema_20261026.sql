-- Reconciles externally applied hosted migration 20261026055117.
-- The earlier 20261025100000 chat-media migration creates the schema.
-- This invariant-only migration ensures a clean-rebuild and the hosted ledger agree.
do $check$
begin
  if to_regclass('public.messages') is null then
    raise exception 'BLINK_CHAT_MEDIA_MESSAGES_MISSING';
  end if;
  if (select count(*) from information_schema.columns
      where table_schema='public' and table_name='messages'
        and column_name in ('image_album_id','image_album_position',
                            'image_album_size','image_preview_url')) <> 4 then
    raise exception 'BLINK_CHAT_ALBUM_PREVIEW_SCHEMA_MISSING';
  end if;
  if not exists(select 1 from storage.buckets
      where id='message-media' and public=false and file_size_limit=83886080
        and cardinality(allowed_mime_types)=24) then
    raise exception 'BLINK_CHAT_MEDIA_LIMITS_NOT_ACTIVE';
  end if;
end;
$check$;
