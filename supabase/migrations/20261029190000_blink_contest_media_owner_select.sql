-- Storage delete first selects the requested objects under the caller's RLS.
-- Without owner SELECT, delete returns an empty result and retains the file.
-- Public previews remain governed by the existing explicitly public bucket.
begin;
create policy "BLINK Contest owner media select" on storage.objects
  for select to authenticated
  using (
    bucket_id='blink-contest-media' and
    (storage.foldername(name))[1] = auth.uid()::text
  );
commit;
