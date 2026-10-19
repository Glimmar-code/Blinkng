-- BLINK campaign banner uploads. Staged only; activate after security review.
-- Distinct immutable filenames avoid CDN overwrite races and protect live banners.
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('blink-live-banners','blink-live-banners',true,2097152,
        array['image/jpeg','image/png']::text[])
on conflict (id) do nothing;

-- The public function returns only a boolean for the current caller, never owner ID.
-- The privileged lookup is isolated and requires an active owner account.
create or replace function public.blink_live_can_upload_banner()
returns boolean
language plpgsql stable security definer
set search_path = ''
as $$
begin
  return private.require_blink_owner() is not null;
exception when insufficient_privilege then
  return false;
end;
$$;

revoke all on function public.blink_live_can_upload_banner()
 from public, anon, authenticated;
grant execute on function public.blink_live_can_upload_banner() to authenticated;

drop policy if exists blink_live_banner_owner_insert on storage.objects;
create policy blink_live_banner_owner_insert
on storage.objects for insert to authenticated
with check (
  bucket_id = 'blink-live-banners'
  and name ~ '^banners/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png)$'
  and (select public.blink_live_can_upload_banner())
);

-- Intentionally no UPDATE/DELETE policies. Old public images may remain referenced
-- by existing versions or campaign audit history. No broad SELECT/listing grant.
comment on function public.blink_live_can_upload_banner()
is 'Owner-only authenticated permission check for uploading immutable, public campaign artwork.';
commit;
