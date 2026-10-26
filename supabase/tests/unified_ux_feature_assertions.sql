-- Guardrails: run only against disposable synthetic database.
-- Provision memberships as the synthetic backend, not as a regular client.
insert into public.conversations(id,created_by,is_group)
values ('30000000-0000-0000-0000-000000000101',
  '00000000-0000-0000-0000-000000000101',true);
insert into public.conversation_participants(conversation_id,user_id)
values ('30000000-0000-0000-0000-000000000101',
  '00000000-0000-0000-0000-000000000101'),
('30000000-0000-0000-0000-000000000101',
  '00000000-0000-0000-0000-000000000102');
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000102',false);
do $$
declare
  first_row uuid;
  second_row uuid;
  first_reel uuid;
  group_id uuid;
  group_message uuid;
  first_claim boolean;
  second_claim boolean;
  rejected boolean;
  total integer;
begin
  select reel_id into first_reel from public.blink_featured_reels_12h() limit 1;
  if first_reel is distinct from '10000000-0000-0000-0000-000000000101'::uuid then
    raise exception '12-hour ranked Reels did not prioritize protected weighted views';
  end if;
  if (select count(*) from public.blink_featured_reels_12h()) <> 2 then
    raise exception 'Incorrect ranked Reels cohort count';
  end if;

  select public.blink_set_onboarding_referrer('00000000-0000-0000-0000-000000000101')
    into first_claim;
  if not coalesce(first_claim,false) then
    raise exception 'Eligible new account referral was rejected';
  end if;
  select public.blink_set_onboarding_referrer('00000000-0000-0000-0000-000000000101')
    into second_claim;
  if not coalesce(second_claim,false) then
    raise exception 'Idempotent referral replay failed';
  end if;
  select public.blink_set_onboarding_referrer('00000000-0000-0000-0000-000000000104')
    into rejected;
  if coalesce(rejected,false) then
    raise exception 'Referral identity was mutable';
  end if;

  select message_id into first_row
    from public.blink_send_direct_text_idempotent(
      'creator','A single offline text','20000000-0000-0000-0000-000000000101');
  select message_id into second_row
    from public.blink_send_direct_text_idempotent(
      'creator','A single offline text','20000000-0000-0000-0000-000000000101');
  if first_row is null or first_row is distinct from second_row then
    raise exception 'Direct text retry duplicated a message';
  end if;

  begin
    perform * from public.blink_send_direct_text_idempotent(
      'creator','Different text','20000000-0000-0000-0000-000000000101');
    raise exception 'Different payload reused a client message id';
  exception when sqlstate '22023' then null;
  end;

  select message_id into group_message
    from public.blink_send_group_text_idempotent(
      '30000000-0000-0000-0000-000000000101','Hello group',
      '20000000-0000-0000-0000-000000000102');
  select message_id into second_row from public.blink_send_group_text_idempotent(
      '30000000-0000-0000-0000-000000000101','Hello group',
      '20000000-0000-0000-0000-000000000102');
  if group_message is distinct from second_row then
    raise exception 'Repeated group send was not idempotent';
  end if;
end;
$$;
reset role;

do $$
begin
  if (select count(*) from public.messages
      where content='A single offline text') <> 1 then
    raise exception 'Direct message was delivered multiple times';
  end if;
  if (select count(*) from public.messages
      where content='Hello group') <> 1 then
    raise exception 'Group message was delivered multiple times';
  end if;
  if (select confirmed_referrals from public.blink_referral_achievements
      where user_id='00000000-0000-0000-0000-000000000101') <> 1 then
    raise exception 'Referral was credited more than once';
  end if;
end;
$$;
-- A 31-day-old pending account cannot select a referrer retroactively.
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000103',false);
do $$
begin
  if public.blink_set_onboarding_referrer('00000000-0000-0000-0000-000000000101') then
    raise exception 'Late referral attribution was allowed';
  end if;
end;
$$;
reset role;

-- A non-admin member cannot set a group image even when the image belongs to
-- that account; even an owner cannot claim another account's storage object.
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000102',false);
do $$
begin
  begin
    perform public.blink_set_group_avatar(
      '30000000-0000-0000-0000-000000000101',
      'https://blink.test/storage/v1/object/public/profile-media/users/00000000-0000-0000-0000-000000000102/avatar/test-image.png'
    );
    raise exception 'Non-admin member changed the group picture';
  exception when sqlstate '42501' then null;
  end;
end;
$$;
reset role;
set role authenticated;
select set_config('request.jwt.claim.sub','00000000-0000-0000-0000-000000000101',false);
do $$
begin
  if public.blink_set_group_avatar(
    '30000000-0000-0000-0000-000000000101',
    'https://blink.test/storage/v1/object/public/profile-media/users/00000000-0000-0000-0000-000000000102/avatar/test-image.png'
  ) then raise exception 'Group owner used a foreign image'; end if;
  if not public.blink_set_group_avatar(
    '30000000-0000-0000-0000-000000000101',
    'https://blink.test/storage/v1/object/public/profile-media/users/00000000-0000-0000-0000-000000000101/avatar/test-image.png'
  ) then raise exception 'Authorized owner could not update group picture'; end if;
  if public.blink_set_group_avatar(
    '30000000-0000-0000-0000-000000000101',
    'https://outside.test/bad.png'
  ) then raise exception 'Group owner used an untrusted external picture'; end if;
end;
$$;
reset role;
select 'unified UX disposable PostgreSQL tests passed' as outcome;
