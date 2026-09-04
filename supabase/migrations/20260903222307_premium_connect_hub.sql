begin;

-- Identity integrity: usernames and chosen display names are unique regardless of case.
-- The legacy onboarding placeholder remains exempt until those users choose a real name.
create unique index if not exists profiles_username_ci_unique
  on public.profiles (lower(btrim(username)))
  where nullif(btrim(username), '') is not null;

create unique index if not exists profiles_full_name_ci_unique
  on public.profiles (lower(btrim(full_name)))
  where btrim(full_name) <> ''
    and lower(btrim(full_name)) <> 'blink user'
    and nullif(btrim(username), '') is not null;

create or replace function public.normalize_profile_identity()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.username := lower(trim(leading '@' from coalesce(new.username, new.handle, '')));
  new.full_name := btrim(coalesce(new.full_name, new.name, ''));
  new.handle := new.username;
  new.name := new.full_name;

  if new.username <> '' and new.username !~ '^[a-z0-9][a-z0-9._-]{1,29}$' then
    raise exception 'USERNAME_INVALID';
  end if;

  if char_length(new.full_name) < 2 or char_length(new.full_name) > 60 then
    raise exception 'FULL_NAME_INVALID';
  end if;

  return new;
end;
$$;

drop trigger if exists normalize_profile_identity_trigger on public.profiles;
create trigger normalize_profile_identity_trigger
before insert or update of username, full_name on public.profiles
for each row execute function public.normalize_profile_identity();

-- Keep email and OAuth signup compatible while honoring a username supplied by the app.
create or replace function public.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_username text := coalesce(
    nullif(new.raw_user_meta_data->>'username', ''),
    nullif(new.raw_user_meta_data->>'user_name', ''),
    ''
  );
  v_full_name text := coalesce(
    nullif(new.raw_user_meta_data->>'full_name', ''),
    nullif(new.raw_user_meta_data->>'name', ''),
    'Blink User'
  );
begin
  insert into public.profiles (id, email, full_name, username)
  values (new.id, new.email, v_full_name, v_username)
  on conflict (id) do update set
    email = coalesce(public.profiles.email, excluded.email),
    username = case
      when nullif(btrim(public.profiles.username), '') is null then excluded.username
      else public.profiles.username
    end,
    full_name = case
      when public.profiles.full_name = 'Blink User' then excluded.full_name
      else public.profiles.full_name
    end;
  return new;
end;
$$;

create or replace function public.check_profile_identity(
  p_username text,
  p_full_name text,
  p_exclude_id uuid default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_username text := lower(trim(leading '@' from coalesce(p_username, '')));
  v_full_name text := lower(btrim(coalesce(p_full_name, '')));
  v_exclude uuid := case when auth.uid() = p_exclude_id then p_exclude_id else null end;
begin
  return jsonb_build_object(
    'username_available',
      v_username ~ '^[a-z0-9][a-z0-9._-]{1,29}$'
      and not exists (
        select 1 from public.profiles p
        where lower(btrim(p.username)) = v_username
          and (v_exclude is null or p.id <> v_exclude)
      ),
    'full_name_available',
      char_length(v_full_name) between 2 and 60
      and v_full_name <> 'blink user'
      and not exists (
        select 1 from public.profiles p
        where lower(btrim(p.full_name)) = v_full_name
          and lower(btrim(p.full_name)) <> 'blink user'
          and nullif(btrim(p.username), '') is not null
          and (v_exclude is null or p.id <> v_exclude)
      )
  );
end;
$$;

revoke all on function public.check_profile_identity(text, text, uuid) from public;
grant execute on function public.check_profile_identity(text, text, uuid) to anon, authenticated;

create or replace function public.set_my_presence(p_online boolean)
returns void
language sql
security invoker
set search_path = public
as $$
  update public.profiles
  set online_now = coalesce(p_online, false),
      is_online = coalesce(p_online, false),
      last_seen_at = now(),
      last_seen = now(),
      updated_at = now()
  where id = auth.uid();
$$;

revoke all on function public.set_my_presence(boolean) from public, anon;
grant execute on function public.set_my_presence(boolean) to authenticated;

create table if not exists public.connect_listings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  listing_type text not null check (listing_type in (
    'roommate', 'housing_need', 'housing_agent', 'mentor_available',
    'mentor_needed', 'study_mate', 'project_partner', 'skill_swap', 'campus_guide'
  )),
  title text not null check (char_length(btrim(title)) between 3 and 90),
  description text not null default '' check (char_length(description) <= 800),
  university text,
  department text,
  academic_level text,
  location text,
  budget_min numeric check (budget_min is null or budget_min >= 0),
  budget_max numeric check (budget_max is null or budget_max >= 0),
  subjects text[] not null default '{}',
  tags text[] not null default '{}',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (budget_min is null or budget_max is null or budget_max >= budget_min)
);

create unique index if not exists connect_listings_one_active_type_per_user
  on public.connect_listings (user_id, listing_type)
  where is_active;
create index if not exists connect_listings_discovery_idx
  on public.connect_listings (listing_type, is_active, updated_at desc);
create index if not exists connect_listings_user_idx
  on public.connect_listings (user_id, updated_at desc);

create table if not exists public.connect_applications (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.connect_listings(id) on delete cascade,
  applicant_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  message text not null default '' check (char_length(message) <= 500),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'withdrawn')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (listing_id, applicant_id)
);

create index if not exists connect_applications_applicant_idx
  on public.connect_applications (applicant_id, updated_at desc);
create index if not exists connect_applications_listing_idx
  on public.connect_applications (listing_id, status, updated_at desc);

create table if not exists public.game_challenges (
  id uuid primary key default gen_random_uuid(),
  challenger_id uuid not null references auth.users(id) on delete cascade,
  opponent_id uuid not null references auth.users(id) on delete cascade,
  game_type text not null check (game_type in ('brain_mix', 'math_sprint', 'logic', 'memory', 'word_power', 'general_knowledge')),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'in_progress', 'completed', 'cancelled')),
  challenger_score integer check (challenger_score is null or challenger_score >= 0),
  opponent_score integer check (opponent_score is null or opponent_score >= 0),
  winner_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  completed_at timestamptz,
  check (challenger_id <> opponent_id),
  check (winner_id is null or winner_id in (challenger_id, opponent_id))
);

-- Upgrade the earlier challenge schema in place without losing existing requests.
do $$
begin
  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'game_challenges' and column_name = 'challenged_id'
  ) and not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'game_challenges' and column_name = 'opponent_id'
  ) then
    alter table public.game_challenges rename column challenged_id to opponent_id;
  end if;

  if exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'game_challenges' and column_name = 'challenged_score'
  ) and not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'game_challenges' and column_name = 'opponent_score'
  ) then
    alter table public.game_challenges rename column challenged_score to opponent_score;
  end if;
end;
$$;

alter table public.game_challenges
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists accepted_at timestamptz,
  add column if not exists completed_at timestamptz;

alter table public.game_challenges drop constraint if exists game_challenges_game_type_check;
alter table public.game_challenges drop constraint if exists game_challenges_status_check;
alter table public.game_challenges drop constraint if exists game_challenges_winner_participant_check;
alter table public.game_challenges drop constraint if exists game_challenges_challenger_score_check;
alter table public.game_challenges drop constraint if exists game_challenges_challenged_score_check;
alter table public.game_challenges drop constraint if exists game_challenges_opponent_score_check;

update public.game_challenges
set game_type = case game_type
  when 'trivia' then 'general_knowledge'
  when 'math' then 'math_sprint'
  when 'speed' then 'brain_mix'
  else game_type
end;

alter table public.game_challenges
  add constraint game_challenges_game_type_check check (
    game_type in ('brain_mix', 'math_sprint', 'logic', 'memory', 'word_power', 'general_knowledge')
  ),
  add constraint game_challenges_status_check check (
    status in ('pending', 'accepted', 'declined', 'in_progress', 'completed', 'cancelled')
  ),
  add constraint game_challenges_challenger_score_check check (
    challenger_score is null or challenger_score between 0 and 500
  ),
  add constraint game_challenges_opponent_score_check check (
    opponent_score is null or opponent_score between 0 and 500
  ),
  add constraint game_challenges_winner_participant_check check (
    winner_id is null or winner_id in (challenger_id, opponent_id)
  );

create unique index if not exists game_challenges_one_open_pair_type
  on public.game_challenges (
    least(challenger_id, opponent_id),
    greatest(challenger_id, opponent_id),
    game_type
  )
  where status in ('pending', 'accepted', 'in_progress');
create index if not exists game_challenges_challenger_idx
  on public.game_challenges (challenger_id, updated_at desc);
create index if not exists game_challenges_opponent_idx
  on public.game_challenges (opponent_id, updated_at desc);

create or replace function public.set_connect_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create or replace function public.guard_connect_application_update()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  if new.listing_id <> old.listing_id or new.applicant_id <> old.applicant_id then
    raise exception 'APPLICATION_IDENTITY_IMMUTABLE';
  end if;

  if auth.uid() = old.applicant_id then
    if new.status <> old.status and new.status <> 'withdrawn' then
      raise exception 'APPLICANT_CAN_ONLY_WITHDRAW';
    end if;
  elsif exists (
    select 1 from public.connect_listings l
    where l.id = old.listing_id and l.user_id = auth.uid()
  ) then
    new.message := old.message;
    if new.status <> old.status and new.status not in ('accepted', 'declined') then
      raise exception 'OWNER_RESPONSE_INVALID';
    end if;
  else
    raise exception 'APPLICATION_UPDATE_FORBIDDEN';
  end if;

  return new;
end;
$$;

drop trigger if exists connect_listings_updated_at on public.connect_listings;
create trigger connect_listings_updated_at before update on public.connect_listings
for each row execute function public.set_connect_updated_at();
drop trigger if exists connect_applications_updated_at on public.connect_applications;
create trigger connect_applications_updated_at before update on public.connect_applications
for each row execute function public.set_connect_updated_at();
drop trigger if exists connect_applications_guard on public.connect_applications;
create trigger connect_applications_guard before update on public.connect_applications
for each row execute function public.guard_connect_application_update();
drop trigger if exists game_challenges_updated_at on public.game_challenges;
create trigger game_challenges_updated_at before update on public.game_challenges
for each row execute function public.set_connect_updated_at();

alter table public.connect_listings enable row level security;
alter table public.connect_applications enable row level security;
alter table public.game_challenges enable row level security;

drop policy if exists connect_listings_select on public.connect_listings;
create policy connect_listings_select on public.connect_listings for select to authenticated
using (is_active or user_id = (select auth.uid()));
drop policy if exists connect_listings_insert_own on public.connect_listings;
create policy connect_listings_insert_own on public.connect_listings for insert to authenticated
with check (user_id = (select auth.uid()));
drop policy if exists connect_listings_update_own on public.connect_listings;
create policy connect_listings_update_own on public.connect_listings for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
drop policy if exists connect_listings_delete_own on public.connect_listings;
create policy connect_listings_delete_own on public.connect_listings for delete to authenticated
using (user_id = (select auth.uid()));

drop policy if exists connect_applications_select_involved on public.connect_applications;
create policy connect_applications_select_involved on public.connect_applications for select to authenticated
using (
  applicant_id = (select auth.uid()) or exists (
    select 1 from public.connect_listings l
    where l.id = listing_id and l.user_id = (select auth.uid())
  )
);
drop policy if exists connect_applications_insert_own on public.connect_applications;
create policy connect_applications_insert_own on public.connect_applications for insert to authenticated
with check (
  applicant_id = (select auth.uid()) and exists (
    select 1 from public.connect_listings l
    where l.id = listing_id and l.user_id <> (select auth.uid()) and l.is_active
  )
);
drop policy if exists connect_applications_update_involved on public.connect_applications;
create policy connect_applications_update_involved on public.connect_applications for update to authenticated
using (
  applicant_id = (select auth.uid()) or exists (
    select 1 from public.connect_listings l
    where l.id = listing_id and l.user_id = (select auth.uid())
  )
)
with check (
  applicant_id = (select auth.uid()) or exists (
    select 1 from public.connect_listings l
    where l.id = listing_id and l.user_id = (select auth.uid())
  )
);
drop policy if exists connect_applications_delete_own on public.connect_applications;
create policy connect_applications_delete_own on public.connect_applications for delete to authenticated
using (applicant_id = (select auth.uid()));

drop policy if exists game_challenges_select_involved on public.game_challenges;
drop policy if exists game_challenges_delete_own_pending on public.game_challenges;
drop policy if exists game_challenges_insert_own on public.game_challenges;
drop policy if exists game_challenges_read_participants on public.game_challenges;
drop policy if exists game_challenges_update_participants on public.game_challenges;
create policy game_challenges_select_involved on public.game_challenges for select to authenticated
using ((select auth.uid()) in (challenger_id, opponent_id));

revoke all on table public.connect_listings from anon;
revoke all on table public.connect_applications from anon;
revoke all on table public.game_challenges from anon;
revoke all on table public.game_challenges from authenticated;
grant select, insert, update, delete on table public.connect_listings to authenticated;
grant select, insert, update, delete on table public.connect_applications to authenticated;
grant select on table public.game_challenges to authenticated;

create or replace function public.create_game_challenge(p_opponent_id uuid, p_game_type text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_id uuid;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_opponent_id is null or p_opponent_id = v_user then raise exception 'INVALID_OPPONENT'; end if;
  if p_game_type not in ('brain_mix', 'math_sprint', 'logic', 'memory', 'word_power', 'general_knowledge') then
    raise exception 'INVALID_GAME_TYPE';
  end if;
  if not exists (select 1 from public.profiles where id = p_opponent_id) then
    raise exception 'OPPONENT_NOT_FOUND';
  end if;
  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_user and b.blocked_id = p_opponent_id)
       or (b.blocker_id = p_opponent_id and b.blocked_id = v_user)
  ) then
    raise exception 'USER_BLOCKED';
  end if;

  select id into v_id from public.game_challenges
  where least(challenger_id, opponent_id) = least(v_user, p_opponent_id)
    and greatest(challenger_id, opponent_id) = greatest(v_user, p_opponent_id)
    and game_type = p_game_type
    and status in ('pending', 'accepted', 'in_progress')
  limit 1;

  if v_id is null then
    insert into public.game_challenges(challenger_id, opponent_id, game_type)
    values (v_user, p_opponent_id, p_game_type)
    returning id into v_id;
  else
    update public.game_challenges set updated_at = now() where id = v_id;
  end if;
  return v_id;
end;
$$;

create or replace function public.respond_game_challenge(p_challenge_id uuid, p_accept boolean)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  update public.game_challenges
  set status = case when p_accept then 'accepted' else 'declined' end,
      accepted_at = case when p_accept then now() else accepted_at end,
      updated_at = now()
  where id = p_challenge_id and opponent_id = auth.uid() and status = 'pending';
  return found;
end;
$$;

create or replace function public.submit_game_challenge_score(p_challenge_id uuid, p_score integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := auth.uid();
  v_challenge public.game_challenges%rowtype;
begin
  if v_user is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if p_score < 0 or p_score > 500 then raise exception 'INVALID_SCORE'; end if;

  select * into v_challenge from public.game_challenges
  where id = p_challenge_id and v_user in (challenger_id, opponent_id)
  for update;
  if not found then raise exception 'CHALLENGE_NOT_FOUND'; end if;
  if v_challenge.status not in ('accepted', 'in_progress') then raise exception 'CHALLENGE_NOT_ACTIVE'; end if;
  if (v_challenge.challenger_id = v_user and v_challenge.challenger_score is not null)
     or (v_challenge.opponent_id = v_user and v_challenge.opponent_score is not null) then
    raise exception 'SCORE_ALREADY_SUBMITTED';
  end if;

  update public.game_challenges
  set challenger_score = case when challenger_id = v_user then p_score else challenger_score end,
      opponent_score = case when opponent_id = v_user then p_score else opponent_score end,
      status = 'in_progress', updated_at = now()
  where id = p_challenge_id;

  update public.game_challenges
  set status = 'completed', completed_at = now(),
      winner_id = case
        when challenger_score > opponent_score then challenger_id
        when opponent_score > challenger_score then opponent_id
        else null
      end,
      updated_at = now()
  where id = p_challenge_id and challenger_score is not null and opponent_score is not null;
  select * into v_challenge from public.game_challenges where id = p_challenge_id;
  return jsonb_build_object(
    'score', p_score,
    'completed', v_challenge.status = 'completed',
    'winnerId', v_challenge.winner_id
  );
end;
$$;

revoke all on function public.create_game_challenge(uuid, text) from public, anon;
revoke all on function public.respond_game_challenge(uuid, boolean) from public, anon;
revoke all on function public.submit_game_challenge_score(uuid, integer) from public, anon;
grant execute on function public.create_game_challenge(uuid, text) to authenticated;
grant execute on function public.respond_game_challenge(uuid, boolean) to authenticated;
grant execute on function public.submit_game_challenge_score(uuid, integer) to authenticated;

-- Use one unambiguous, authenticated identity resolver for direct messages.
create or replace function public.send_message(p_receiver_username text, p_content text)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_sender uuid := auth.uid();
  v_receiver uuid;
  v_conversation uuid;
  v_message uuid;
  v_identifier text := lower(trim(leading '@' from coalesce(p_receiver_username, '')));
begin
  if v_sender is null then raise exception 'AUTHENTICATION_REQUIRED'; end if;
  if v_identifier = '' then raise exception 'RECIPIENT_REQUIRED'; end if;
  if nullif(btrim(p_content), '') is null then raise exception 'MESSAGE_REQUIRED'; end if;

  select p.id into v_receiver
  from public.profiles p
  where lower(btrim(p.username)) = v_identifier
     or lower(btrim(coalesce(p.handle, ''))) = v_identifier
     or (
       lower(btrim(p.full_name)) = v_identifier
       and lower(btrim(p.full_name)) <> 'blink user'
     )
  order by case when lower(btrim(p.username)) = v_identifier then 0 else 1 end
  limit 1;

  if v_receiver is null then raise exception 'RECIPIENT_NOT_FOUND'; end if;
  if v_receiver = v_sender then raise exception 'CANNOT_MESSAGE_SELF'; end if;
  if exists (
    select 1 from public.blocks b
    where (b.blocker_id = v_sender and b.blocked_id = v_receiver)
       or (b.blocker_id = v_receiver and b.blocked_id = v_sender)
  ) then raise exception 'USER_BLOCKED'; end if;

  select c.id into v_conversation
  from public.conversations c
  where c.is_group = false
    and exists (select 1 from public.conversation_participants cp where cp.conversation_id = c.id and cp.user_id = v_sender)
    and exists (select 1 from public.conversation_participants cp where cp.conversation_id = c.id and cp.user_id = v_receiver)
    and (select count(*) from public.conversation_participants cp where cp.conversation_id = c.id) = 2
  limit 1;

  if v_conversation is null then
    insert into public.conversations(created_by, is_group, last_message_at)
    values(v_sender, false, now()) returning id into v_conversation;
    insert into public.conversation_participants(conversation_id, user_id)
    values(v_conversation, v_sender), (v_conversation, v_receiver)
    on conflict do nothing;
  end if;

  insert into public.messages(conversation_id, sender_id, content, message_type)
  values(v_conversation, v_sender, btrim(p_content), 'text')
  returning id into v_message;
  update public.conversations set last_message_at = now(), updated_at = now() where id = v_conversation;
  return v_message;
end;
$$;

revoke all on function public.send_message(text, text) from public, anon;
grant execute on function public.send_message(text, text) to authenticated;

notify pgrst, 'reload schema';
commit;
