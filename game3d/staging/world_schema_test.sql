-- Execute only against disposable staging DB after world_schema.sql.
-- A CI run creates stub auth.users and the PostgreSQL roles in a local container.
begin;
insert into auth.users(id) values
('11111111-1111-4111-8111-111111111111'),
('22222222-2222-4222-8222-222222222222');
insert into private.blink_life_verified_campus(user_id,university_code)
values ('11111111-1111-4111-8111-111111111111','verified_demo_school');

do $$
declare
  uid uuid := '11111111-1111-4111-8111-111111111111';
  eid uuid := 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  outcome jsonb;
  profile jsonb;
  rankings jsonb;
begin
  if has_function_privilege('anon','public.blink_life_stage_event(uuid,uuid,text)','execute')
    or has_function_privilege('authenticated','public.blink_life_stage_event(uuid,uuid,text)','execute')
    or has_table_privilege('authenticated','private.blink_life_world_profiles','select') then
    raise exception 'Unprivileged roles unexpectedly reached server-only world state';
  end if;
  if not has_function_privilege('service_role','public.blink_life_stage_event(uuid,uuid,text)','execute') then
    raise exception 'Private world RPC unavailable to trusted Edge service';
  end if;
  outcome := public.blink_life_stage_event(eid,uid,'depot_shift');
  if (outcome->'profile'->>'game_cash')::int <> 580 then
    raise exception 'Fictional depot shift payout was not exactly 80';
  end if;
  outcome := public.blink_life_stage_event(eid,uid,'depot_shift');
  if outcome->>'duplicate' <> 'true' then
    raise exception 'Repeat event created duplicated fictional payout';
  end if;
  profile := public.blink_life_stage_profile(uid);
  if (profile->>'game_cash')::int <> 580 then
    raise exception 'Duplicate request changed the wallet';
  end if;
  outcome := public.blink_life_stage_event('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',uid,'buy_house');
  if outcome->'profile'->>'house_owned' <> 'true' or
     (outcome->'profile'->>'game_cash')::int <> 180 then
    raise exception 'House ownership purchase did not apply atomically';
  end if;
  outcome := public.blink_life_stage_event('cccccccc-cccc-4ccc-8ccc-cccccccccccc',uid,'duel_win');
  if (outcome->'profile'->>'xp')::int <> 18 then
    raise exception 'Non-redeemable quiz XP missing';
  end if;
  rankings := public.blink_life_stage_campus_rankings();
  if jsonb_array_length(rankings) <> 1 or rankings->0->>'university' <> 'verified_demo_school'
     or (rankings->0->>'points')::int <> 1 then
    raise exception 'School ranks do not require verified school membership';
  end if;
  outcome := public.blink_life_stage_event('dddddddd-dddd-4ddd-8ddd-dddddddddddd',
    '22222222-2222-4222-8222-222222222222','duel_win');
  rankings := public.blink_life_stage_campus_rankings();
  if jsonb_array_length(rankings) <> 1 then
    raise exception 'Unverified school membership generated public points';
  end if;
end $$;
rollback;
