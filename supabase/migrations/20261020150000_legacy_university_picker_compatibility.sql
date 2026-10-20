-- Safe compatibility repair: older BLINK APKs submit university names without IDs.
-- Keep explicit mismatches rejected, but automatically reconcile name-only edits.
begin;
create or replace function public.enforce_blink_profile_university_id()
returns trigger language plpgsql
set search_path = public, pg_temp as $$
declare v_name text;
        v_id text;
begin
  if (tg_op = 'INSERT' and new.university_id is null)
     or (tg_op = 'UPDATE'
         and new.university is distinct from old.university
         and new.university_id is not distinct from old.university_id) then
    select u.id into v_id from public.blink_universities u
      where u.active and lower(trim(u.official_name)) = lower(trim(coalesce(new.university, '')))
      limit 1;
    new.university_id := v_id;
  end if;
  if new.university_id is not null then
    select u.official_name into v_name from public.blink_universities u
    where u.id = new.university_id and u.active;
    if v_name is null or lower(trim(coalesce(new.university,''))) <> lower(trim(v_name)) then
      raise exception 'UNIVERSITY_SELECTION_MISMATCH';
    end if;
  end if;
  return new;
end; $$;
commit;
