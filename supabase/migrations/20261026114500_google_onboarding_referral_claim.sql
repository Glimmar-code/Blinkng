-- Google-created accounts may select their one lifetime referrer during onboarding.
-- This endpoint is identity-scoped, never accepts a referred_user_id from a client,
-- and never allows previously completed accounts to retroactively claim attribution.
create or replace function public.blink_set_onboarding_referrer(p_referrer_id uuid)
returns boolean
language plpgsql volatile security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_registered timestamptz;
  v_confirmed timestamptz;
  v_inserted boolean := false;
begin
  if v_user_id is null then
    raise exception 'AUTHENTICATION_REQUIRED' using errcode = '42501';
  end if;
  if p_referrer_id is null or p_referrer_id = v_user_id then
    return false;
  end if;
  select u.created_at, u.email_confirmed_at into v_registered, v_confirmed
    from auth.users u where u.id = v_user_id for update;
  if not found or v_registered < now() - interval '7 days' then
    return false;
  end if;
  -- Only Google OAuth accounts in initial setup are eligible for this late picker.
  if not exists (
    select 1 from auth.identities i where i.user_id=v_user_id and i.provider='google'
  ) or not exists (
    select 1 from public.profiles mine where mine.id=v_user_id
      and mine.onboarding_completed is false
  ) or not exists (
    select 1 from public.profiles p join auth.users original on original.id=p.id
     where p.id=p_referrer_id and p.onboarding_completed is true
       and original.created_at < v_registered
  ) then
    return false;
  end if;

  insert into public.blink_referrals(
    referred_user_id, referrer_user_id, status, confirmed_at
  ) values (
    v_user_id, p_referrer_id,
    case when v_confirmed is null then 'pending' else 'confirmed' end,
    v_confirmed
  ) on conflict (referred_user_id) do nothing;
  v_inserted := found;
  if not v_inserted then
    -- Existing attribution is immutable, including on multiple devices.
    return exists (
      select 1 from public.blink_referrals
      where referred_user_id = v_user_id and referrer_user_id = p_referrer_id
    );
  end if;

  if v_confirmed is not null then
    insert into public.blink_referral_achievements(
      user_id, confirmed_referrals, last_confirmed_at, updated_at
    )
    values (p_referrer_id, 1, v_confirmed, now())
    on conflict (user_id) do update set
      confirmed_referrals = public.blink_referral_achievements.confirmed_referrals + 1,
      last_confirmed_at = greatest(
        public.blink_referral_achievements.last_confirmed_at, excluded.last_confirmed_at
      ),
      updated_at = now();

    insert into public.notifications(
      user_id, actor_id, type, target_type, target_id, text, sub_text, is_read, metadata
    )
    select p_referrer_id, v_user_id, 'system', 'profile', v_user_id,
      'Your BLINK network is growing!',
      'A student joined with your referral. You have more opportunities to be discovered.',
      false, jsonb_build_object('kind','blink_referral_confirmed')
    where not exists (
      select 1 from public.notifications n
      where n.user_id=p_referrer_id and n.actor_id=v_user_id
        and n.metadata->>'kind'='blink_referral_confirmed'
    );
  end if;
  return true;
end;
$$;

revoke all on function public.blink_set_onboarding_referrer(uuid)
  from public, anon, authenticated;
grant execute on function public.blink_set_onboarding_referrer(uuid) to authenticated;
comment on function public.blink_set_onboarding_referrer(uuid) is
 'Bound to auth.uid(); Google-created, recently registered onboarding accounts only; immutable single referral; rewards at most once.';
