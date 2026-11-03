-- Allow existing album metadata to group video messages, maintaining exactly
-- the same authorization and owner conversation checks as photo albums.
-- This is additive: existing image data is not rewritten or removed.
begin;
alter table public.messages drop constraint if exists blink_chat_album_invariants_v1;
alter table public.messages add constraint blink_chat_album_invariants_v1
  check (
    (image_album_id is null and image_album_position is null and image_album_size is null)
    or (
      message_type in ('image','video')
      and image_album_id is not null
      and image_album_position between 0 and 9
      and image_album_size between 2 and 10
      and image_album_position < image_album_size
    )
  );

-- Explicit capability check: old clients continue to send ordinary videos,
-- and new clients only send album columns after this migration is active.
create function public.blink_chat_video_album_supported()
returns boolean language sql stable security invoker set search_path='' as $$
  select true;
$$;
revoke all on function public.blink_chat_video_album_supported()
  from public, anon, authenticated;
grant execute on function public.blink_chat_video_album_supported() to authenticated;
notify pgrst, 'reload schema';
commit;
