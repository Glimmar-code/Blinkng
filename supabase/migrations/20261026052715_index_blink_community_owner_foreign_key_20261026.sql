-- Reconciles hosted production migration 20261026052715.
-- Prevent owner-side community FK scans; idempotent on future releases.
begin;
set local lock_timeout='5s';
set local statement_timeout='30s';
create index if not exists blink_communities_owner_idx
  on public.blink_communities(owner_id);
do $check$
begin
 if not exists (select 1 from pg_indexes where schemaname='public'
    and tablename='blink_communities'
    and indexname='blink_communities_owner_idx') then
   raise exception 'BLINK_COMMUNITY_OWNER_INDEX_MISSING';
 end if;
end;
$check$;
commit;
