-- Rollback reward integration without deleting claims, reward inventory, or ledger entries.
DROP TRIGGER IF EXISTS broadcast_coin_purchase_campaign_progress ON public.blink_coin_purchase_orders;
-- Restore the previous paid-spin implementation from the recovered production function.
CREATE OR REPLACE FUNCTION public.spin_connect_match(p_university text DEFAULT NULL::text, p_faculty text DEFAULT NULL::text, p_department text DEFAULT NULL::text, p_academic_level text DEFAULT NULL::text, p_relationship_status text DEFAULT NULL::text, p_type_prompt text DEFAULT NULL::text, p_online_only boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_user_id uuid := (select auth.uid());
  v_balance numeric;
  v_prompt text := left(nullif(btrim(p_type_prompt), ''), 200);
  v_match record;
begin
  if v_user_id is null then
    raise exception 'Please sign in again.';
  end if;

  insert into public.user_balances (
    user_id,
    lifetime_engagement_score,
    spendable_coin_balance,
    updated_at
  ) values (
    v_user_id,
    0,
    0,
    now()
  )
  on conflict (user_id) do nothing;

  select ub.spendable_coin_balance
    into v_balance
  from public.user_balances ub
  where ub.user_id = v_user_id
  for update;

  if floor(coalesce(v_balance, 0)) < 10 then
    raise exception 'You need at least 10 Blink Coins to spin.';
  end if;

  with filtered as (
    select
      m.*,
      p.bio,
      p.professional_headline,
      p.core_skills,
      p.hobbies,
      p.languages,
      coalesce(pp.public_posts, '') as public_posts
    from public.get_connect_matches(120) m
    join public.profiles p on p.id = m.id
    left join lateral (
      select string_agg(
        concat_ws(
          ' ',
          recent.text,
          recent.caption,
          array_to_string(recent.hashtags, ' '),
          array_to_string(recent.tags, ' ')
        ),
        ' '
      ) as public_posts
      from (
        select fp.text, fp.caption, fp.hashtags, fp.tags
        from public.feed_posts fp
        where fp.user_id = m.id
          and fp.is_active is true
          and coalesce(fp.is_flagged, false) is false
          and (fp.audience is null or fp.audience ilike 'Everyone')
        order by fp.created_at desc
        limit 20
      ) recent
    ) pp on true
    where
      (nullif(btrim(p_university), '') is null or lower(m.university) = lower(btrim(p_university)))
      and (nullif(btrim(p_faculty), '') is null or lower(m.faculty) = lower(btrim(p_faculty)))
      and (nullif(btrim(p_department), '') is null or lower(m.department) = lower(btrim(p_department)))
      and (nullif(btrim(p_academic_level), '') is null or lower(m.academic_level) = lower(btrim(p_academic_level)))
      and (nullif(btrim(p_relationship_status), '') is null or lower(m.relationship_status) = lower(btrim(p_relationship_status)))
      and (not coalesce(p_online_only, false) or m.online_now is true)
  ),
  enriched as (
    select
      f.*,
      lower(
        concat_ws(
          ' ',
          f.bio,
          f.professional_headline,
          array_to_string(f.core_skills, ' '),
          array_to_string(f.hobbies, ' '),
          array_to_string(f.languages, ' '),
          f.university,
          f.faculty,
          f.department,
          f.public_posts
        )
      ) as search_blob
    from filtered f
  ),
  scored as (
    select
      e.*,
      case
        when v_prompt is null then 0
        else least(
          40,
          coalesce((
            select count(*)::int * 4
            from (
              select distinct token
              from regexp_split_to_table(
                regexp_replace(lower(v_prompt), '[^a-z0-9+#]+', ' ', 'g'),
                '\s+'
              ) token
              where length(token) >= 3
                and token not in (
                  'someone', 'somebody', 'person', 'people', 'that', 'with',
                  'who', 'likes', 'like', 'into', 'and', 'the', 'for', 'from',
                  'want', 'looking', 'type', 'very', 'also'
                )
            ) prompt_tokens
            where e.search_blob like '%' || prompt_tokens.token || '%'
          ), 0)
          + case
              when lower(v_prompt) ~ '(coding|code|programming|software|developer|tech)'
               and e.search_blob ~ '(coding|code|programming|software|developer|tech)'
              then 10 else 0
            end
          + case
              when lower(v_prompt) ~ '(social|talking|talk|chat|communication|community)'
               and e.search_blob ~ '(social|talking|talk|chat|communication|community)'
              then 10 else 0
            end
          + case
              when lower(v_prompt) ~ '(cooking|cook|food|baking|bake|chef|recipe)'
               and e.search_blob ~ '(cooking|cook|food|baking|bake|chef|recipe)'
              then 10 else 0
            end
          + case
              when lower(v_prompt) ~ '(football|sport|sports|gaming|game|music|reading|books|art)'
               and e.search_blob ~ '(football|sport|sports|gaming|game|music|reading|books|art)'
              then 8 else 0
            end
        )::int
      end as semantic_score
    from enriched e
  ),
  ranked as (
    select
      s.*,
      least(100, s.compatibility_score + round(s.semantic_score * 0.60)::int) as final_score,
      exists (
        select 1
        from public.connect_match_spins cms
        where cms.user_id = v_user_id
          and cms.matched_user_id = s.id
          and cms.created_at > now() - interval '14 days'
      ) as recently_matched
    from scored s
  )
  select *
    into v_match
  from ranked r
  order by
    r.recently_matched asc,
    (r.final_score + (random() * 8)) desc
  limit 1;

  if not found then
    raise exception 'No eligible match fits those filters yet. Try widening your preferences.';
  end if;

  update public.user_balances
  set spendable_coin_balance = spendable_coin_balance - 10,
      updated_at = now()
  where user_id = v_user_id;

  insert into public.connect_match_spins (
    user_id,
    matched_user_id,
    filters,
    type_prompt,
    coins_spent,
    compatibility_score
  ) values (
    v_user_id,
    v_match.id,
    jsonb_build_object(
      'university', nullif(btrim(p_university), ''),
      'faculty', nullif(btrim(p_faculty), ''),
      'department', nullif(btrim(p_department), ''),
      'academic_level', nullif(btrim(p_academic_level), ''),
      'relationship_status', nullif(btrim(p_relationship_status), ''),
      'online_only', coalesce(p_online_only, false)
    ),
    v_prompt,
    10,
    v_match.final_score
  );

  return jsonb_build_object(
    'candidate', jsonb_build_object(
      'id', v_match.id,
      'username', v_match.username,
      'full_name', v_match.full_name,
      'avatar_url', v_match.avatar_url,
      'university', v_match.university,
      'faculty', v_match.faculty,
      'department', v_match.department,
      'academic_level', v_match.academic_level,
      'relationship_status', v_match.relationship_status,
      'online_now', v_match.online_now,
      'last_seen_at', v_match.last_seen_at,
      'compatibility_score', v_match.final_score,
      'common_skills', coalesce(v_match.common_skills, '{}'::text[]),
      'common_hobbies', coalesce(v_match.common_hobbies, '{}'::text[])
    ),
    'coins_spent', 10,
    'remaining_coins', floor(v_balance - 10)::bigint,
    'match_reasons', to_jsonb(array_remove(array[
      case
        when cardinality(coalesce(v_match.common_skills, '{}'::text[])) > 0
        then 'Shared skills'
      end,
      case
        when cardinality(coalesce(v_match.common_hobbies, '{}'::text[])) > 0
        then 'Shared interests'
      end,
      case
        when v_match.semantic_score > 0
        then 'Matches your type preference'
      end,
      case
        when v_match.online_now is true
        then 'Online now'
      end,
      case
        when nullif(btrim(p_university), '') is not null
        then 'University match'
      end
    ], null))
  );
end;
$function$

-- Preserve the relaxed spin cost check so any already-consumed free spins remain valid.
REVOKE EXECUTE ON FUNCTION public.broadcast_apply_reward(uuid,uuid,uuid,public.broadcast_reward_type,numeric,text) FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.broadcast_backfill_campaign_progress() FROM PUBLIC,anon,authenticated;
REVOKE EXECUTE ON FUNCTION public.redeem_broadcast_fee_voucher(uuid,numeric,text) FROM PUBLIC,anon,authenticated;
