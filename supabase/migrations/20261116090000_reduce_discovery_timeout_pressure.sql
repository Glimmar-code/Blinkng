-- Reduce discovery-feed scoring and personalization lookup pressure.
-- Add targeted hot-path indexes and cover currently unindexed FK columns in public/private.
-- Also repair the confirmed recurring VIP expiry reminder writer schema mismatch.
-- Safe additive indexes for the viewer/creator predicates used by the current
-- ranking function. Preserve all eligibility, privacy, scoring and monetization
-- behavior. This migration intentionally does not change candidate caps/weights.
begin;
set local lock_timeout = '5s';
set local statement_timeout = '90s';

create index if not exists blocks_blocked_blocker_idx
  on public.blocks (blocked_id, blocker_id);

create index if not exists post_views_viewer_post_last_viewed_idx
  on public.post_views (viewer_id, post_id, last_viewed_at desc);

create index if not exists post_likes_user_created_post_idx
  on public.post_likes (user_id, created_at desc, post_id);

create index if not exists post_bookmarks_user_created_post_idx
  on public.post_bookmarks (user_id, created_at desc, post_id);

create index if not exists comments_author_created_post_idx
  on public.comments (author_id, created_at desc, post_id);

-- These SECURITY DEFINER routines are trigger-only, not client RPCs.
-- Existing triggers continue to invoke them; clients must not call them directly.
revoke execute on function public.market_require_approved_seller()
  from public, anon, authenticated;
revoke execute on function public.market_require_approved_seller_for_order()
  from public, anon, authenticated;

-- Add the missing foreign-key-side indexes reported by the production advisor.
-- Existing indexes are checked by their leading key columns so this skips
-- constraints already covered by a primary/composite index.
do $foreign_key_indexes$
declare
  v_fk record;
  v_index_name text;
begin
  for v_fk in
    select
      c.conname,
      n.nspname,
      t.relname,
      (select string_agg(a.attname, '_' order by k.ord)
         from unnest(c.conkey) with ordinality k(attnum, ord)
         join pg_attribute a on a.attrelid=c.conrelid and a.attnum=k.attnum) as column_suffix,
      (select string_agg(format('%I', a.attname), ', ' order by k.ord)
         from unnest(c.conkey) with ordinality k(attnum, ord)
         join pg_attribute a on a.attrelid=c.conrelid and a.attnum=k.attnum) as column_list
    from pg_constraint c
    join pg_class t on t.oid=c.conrelid
    join pg_namespace n on n.oid=t.relnamespace
    where c.contype='f'
      and n.nspname in ('public','private')
      and not exists (
        select 1
        from pg_index i
        where i.indrelid=c.conrelid
          and i.indisvalid
          and i.indisready
          and i.indnkeyatts >= cardinality(c.conkey)
          and (
            select array_agg((i.indkey::smallint[])[s] order by s)
            from generate_series(0,cardinality(c.conkey)-1) s
          ) = c.conkey
      )
  loop
    v_index_name := left('idx_' || v_fk.relname || '_' || v_fk.column_suffix || '_fk', 63);
    execute format(
      'create index if not exists %I on %I.%I (%s)',
      v_index_name, v_fk.nspname, v_fk.relname, v_fk.column_list
    );
  end loop;
end;
$foreign_key_indexes$;

-- The discovery RPC repeatedly selects the highest-ranked remaining candidate
-- for each position. Index its temporary candidate set once, then retain the
-- existing priority lanes and diversity rules. Guard the source rewrite so an
-- unexpected future function revision aborts rather than being overwritten.
do $migration$
declare
  v_oid regprocedure :=
    'private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric)'::regprocedure;
  v_source text := pg_get_functiondef(v_oid);
  v_marker text := E'  for v_position in 1..v_target_count loop';
  v_rewrite text := E'  -- BLINK_TIMEOUT_RANKED_TEMP_INDEXES\n'
    || E'  create index discovery_candidates_score_idx\n'
    || E'    on pg_temp.discovery_candidates(feed_score desc, created_at desc, post_id desc);\n'
    || E'  create index discovery_candidates_lane_idx\n'
    || E'    on pg_temp.discovery_candidates(is_reel, creator_tier, feed_score desc, created_at desc, post_id desc);\n'
    || E'  analyze pg_temp.discovery_candidates;\n\n'
    || v_marker;
begin
  if v_source is null then
    raise exception 'DISCOVERY_FEED_FUNCTION_MISSING';
  end if;

  if position('BLINK_TIMEOUT_RANKED_TEMP_INDEXES' in v_source) > 0 then
    if position('discovery_candidates_score_idx' in v_source) = 0
       or position('discovery_candidates_lane_idx' in v_source) = 0 then
      raise exception 'DISCOVERY_TEMP_INDEX_MARKER_INCOMPLETE';
    end if;
    return;
  end if;

  if position(v_marker in v_source) = 0
     or length(v_source) - length(replace(v_source, v_marker, '')) <> length(v_marker) then
    raise exception 'DISCOVERY_FEED_SELECTION_SOURCE_DRIFT';
  end if;

  v_source := replace(v_source, v_marker, v_rewrite);
  execute v_source;
end;
$migration$;

-- Repair the recurring VIP reminder job: notifications uses text/sub_text,
-- not the removed comment column. Keep the existing schedule and expiry policy.
create or replace function private.expire_blink_items_and_remind_vip()
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  update public.blink_inventory
  set status = 'EXPIRED', updated_at = now()
  where status = 'ACTIVE' and expires_at is not null and expires_at <= now();

  update public.blink_boosts
  set status = 'ENDED'
  where status = 'ACTIVE' and ends_at <= now();

  insert into public.notifications(user_id, type, text, sub_text, is_read)
  select v.user_id,
         'system'::public.notification_type_enum,
         '👑 Your Blink VIP expires in less than 24 hours. Open Blink Store to review or renew.',
         'Blink VIP expiry reminder',
         false
  from public.blink_vip_passes v
  where v.expires_at > now()
    and v.expires_at <= now() + interval '24 hours'
    and not v.expiry_reminder_sent;

  update public.blink_vip_passes
  set expiry_reminder_sent = true
  where expires_at > now()
    and expires_at <= now() + interval '24 hours'
    and not expiry_reminder_sent;

  update public.profiles p
  set blink_vip_until = (
    select max(v.expires_at)
    from public.blink_vip_passes v
    where v.user_id = p.id and v.expires_at > now()
  )
  where p.blink_vip_until is not null
    and p.blink_vip_until <= now();
end;
$function$;

commit;
