-- Contestant submissions must not be publicly retrievable before approval.
-- Keep the legacy public bucket for administrative contest cover artwork only.
-- New contestant uploads go to a separate owner-scoped PRIVATE bucket.
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('blink-contest-private', 'blink-contest-private', false, 26214400,
        array['image/jpeg','image/png','image/webp','video/mp4']::text[])
on conflict (id) do update set public=false, file_size_limit=26214400,
  allowed_mime_types=excluded.allowed_mime_types;

create policy "BLINK Contest private owner upload" on storage.objects
  for insert to authenticated
  with check (
    bucket_id='blink-contest-private' and
    (storage.foldername(name))[1] = auth.uid()::text
  );

create policy "BLINK Contest private owner view" on storage.objects
  for select to authenticated
  using (
    bucket_id='blink-contest-private' and
    (storage.foldername(name))[1] = auth.uid()::text
  );

-- Deliberately no owner DELETE or UPDATE policy: contestants cannot erase or
-- replace an already submitted image while admins are reviewing applications.
-- Orphan cleanup belongs to a privileged, auditable maintenance process.

create function private.blink_contest_validate_private_submission()
returns trigger language plpgsql security definer set search_path = '' as $$
declare v_path text;
begin
  if new.legacy_text_only then
    return new; -- Existing historical entries only; defaults false for new ones.
  end if;

  v_path := substring(
    new.media_url from
    '/functions/v1/blink-contest-private-media/([0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|jpeg|png|webp|mp4))$'
  );

  if v_path is null
    or split_part(v_path,'/',1) <> new.user_id::text
    or not exists (
       select 1 from storage.objects o
       where o.bucket_id='blink-contest-private' and o.name=v_path
    )
  then
    raise exception 'PRIVATE_CONTEST_MEDIA_REQUIRED' using errcode='23514';
  end if;
  return new;
end;
$$;

revoke all on function private.blink_contest_validate_private_submission()
  from public, anon, authenticated;

create trigger blink_contest_private_submission_guard
before insert or update of media_url on public.blink_contest_entries
for each row execute function private.blink_contest_validate_private_submission();

notify pgrst, 'reload schema';
commit;
