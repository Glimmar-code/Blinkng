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
create index if not exists blink_paystack_renewals_user_id_fk_idx
  on private.blink_paystack_renewals (user_id);
create index if not exists blink_boosts_content_id_fk_idx
  on public.blink_boosts (content_id);
create index if not exists blink_contest_entry_appeals_contest_id_fk_idx
  on public.blink_contest_entry_appeals (contest_id);
create index if not exists blink_referrals_referrer_user_id_fk_idx
  on public.blink_referrals (referrer_user_id);
create index if not exists blink_vip_claims_user_id_fk_idx
  on public.blink_vip_claims (user_id);
create index if not exists connect_listings_category_slug_fk_idx
  on public.connect_listings (category_slug);
create index if not exists mentor_requests_mentor_profile_id_fk_idx
  on public.mentor_requests (mentor_profile_id);
create index if not exists reading_mate_requests_reading_profile_id_fk_idx
  on public.reading_mate_requests (reading_profile_id);


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


-- Move message Rank Point awards out of the message transaction. The durable
-- outbox is drained by the existing minute channel worker, so sending a message
-- no longer waits for a contended profiles.points row.
create table if not exists private.blink_message_rank_reward_outbox (
  message_id uuid primary key references public.messages(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending'
    check (status in ('pending','processing','failed','processed')),
  attempts integer not null default 0 check (attempts >= 0),
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  processed_at timestamptz
);

create index if not exists blink_message_rank_reward_outbox_due_idx
  on private.blink_message_rank_reward_outbox(status, updated_at, created_at);

create index if not exists blink_message_rank_reward_outbox_sender_idx
  on private.blink_message_rank_reward_outbox(sender_id, created_at desc);

alter table private.blink_message_rank_reward_outbox enable row level security;
revoke all on table private.blink_message_rank_reward_outbox
  from public, anon, authenticated;

create or replace function public.award_message_points()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
  insert into private.blink_message_rank_reward_outbox(message_id, sender_id)
  values (new.id, new.sender_id)
  on conflict (message_id) do nothing;
  return new;
end;
$function$;

-- Trigger-only function: the existing trg_points_message trigger remains intact.
revoke all on function public.award_message_points() from public, anon, authenticated;

create or replace function public.process_message_rank_reward_outbox(p_limit integer default 25)
returns integer
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_job record;
  v_processed integer := 0;
  v_limit integer := greatest(1, least(coalesce(p_limit, 25), 100));
begin
  perform pg_catalog.set_config('lock_timeout', '100ms', true);

  for v_job in
    select o.message_id, o.sender_id, o.attempts
    from private.blink_message_rank_reward_outbox o
    where o.status = 'pending'
       or (
         o.status = 'failed'
         and o.attempts < 8
         and o.updated_at <= now() - make_interval(
           secs => least(1800, (30 * power(2, greatest(o.attempts - 1, 0)))::integer)
         )
       )
       or (
         o.status = 'processing'
         and o.attempts < 8
         and o.updated_at <= now() - interval '2 minutes'
       )
    order by o.created_at, o.message_id
    limit v_limit
    for update skip locked
  loop
    update private.blink_message_rank_reward_outbox
    set status = 'processing',
        attempts = v_job.attempts + 1,
        last_error = null,
        updated_at = now()
    where message_id = v_job.message_id;

    begin
      perform public.award_points(v_job.sender_id, 'message_user', v_job.message_id);

      update private.blink_message_rank_reward_outbox
      set status = 'processed',
          last_error = null,
          processed_at = now(),
          updated_at = now()
      where message_id = v_job.message_id;

      v_processed := v_processed + 1;
    exception when others then
      update private.blink_message_rank_reward_outbox
      set status = 'failed',
          last_error = left(sqlerrm, 800),
          updated_at = now()
      where message_id = v_job.message_id;
    end;
  end loop;

  return v_processed;
end;
$function$;

revoke all on function public.process_message_rank_reward_outbox(integer)
  from public, anon, authenticated;
grant execute on function public.process_message_rank_reward_outbox(integer)
  to service_role;


-- Wake the existing minute worker when message rewards are pending, even if
-- there are no channel notifications or contest emails to dispatch.
create or replace function private.blink_channel_dispatch_due()
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  secret text;
begin
  perform private.blink_channel_emit_due(200);

  if not exists(select 1 from public.blink_claim_channel_push_notifications(1))
     and not exists(
       select 1 from public.blink_essential_email_outbox
       where kind='contest_launch' and (
         (status='pending' and available_at<=now())
         or (status='sending' and updated_at<now()-interval '10 minutes')
       )
     )
     and not exists(
       select 1 from private.blink_message_rank_reward_outbox
       where status='pending'
          or (
            status='failed' and attempts<8
            and updated_at <= now() - make_interval(
              secs => least(1800, (30 * power(2, greatest(attempts - 1, 0)))::integer)
            )
          )
          or (
            status='processing' and attempts<8
            and updated_at <= now() - interval '2 minutes'
          )
     ) then
    return false;
  end if;

  select worker_token into secret
  from private.blink_channel_worker_credentials
  where singleton;

  if secret is null then
    raise exception 'CHANNEL_WORKER_SECRET_MISSING';
  end if;

  perform net.http_post(
    url := 'https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/blink-channel-push-worker',
    headers := jsonb_build_object(
      'Content-Type','application/json',
      'x-blink-channel-worker',secret
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 5000
  );

  return true;
end;
$function$;

commit;
