-- A public preview bucket for media contestants explicitly choose to publish.
-- No automatic uploads. Files have publicly accessible unguessable URLs immediately
-- after upload, even if the application has not yet been approved. Clients must
-- disclose this before upload; avoid private or identifying documents.
begin;
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('blink-contest-media','blink-contest-media',true,26214400,
        array['image/jpeg','image/png','image/webp','video/mp4']::text[])
on conflict (id) do nothing;
create policy "BLINK Contest owner media upload" on storage.objects
  for insert to authenticated
  with check (
    bucket_id='blink-contest-media' and
    (storage.foldername(name))[1] = auth.uid()::text
  );
create policy "BLINK Contest owner media delete" on storage.objects
  for delete to authenticated
  using (
    bucket_id='blink-contest-media' and
    (storage.foldername(name))[1] = auth.uid()::text
  );
commit;
