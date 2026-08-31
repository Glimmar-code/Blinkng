-- Blink backend synchronization hardening.
-- Applied and verified against project ref jhwgifrlxwspoedxjaly on 2026-08-31.

alter table if exists public.poll_votes
  add constraint poll_votes_poll_id_user_id_key unique (poll_id, user_id);

alter table if exists public.story_reactions
  add constraint story_reactions_story_id_user_id_key unique (story_id, user_id);

update public.market_items mi
set seller_id = p.id
from public.profiles p
where mi.seller_id is null and p.username = mi.seller_username;

create or replace function public.recalc_feed_like_count()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  update public.feed_posts set like_count=(select count(*) from public.post_likes where post_id=coalesce(new.post_id,old.post_id))
  where id=coalesce(new.post_id,old.post_id);
  return coalesce(new,old);
end $$;

drop trigger if exists trg_recalc_feed_like_count on public.post_likes;
create trigger trg_recalc_feed_like_count after insert or delete on public.post_likes
for each row execute function public.recalc_feed_like_count();

create or replace function public.recalc_comment_like_count()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  update public.comments set likes_count=(select count(*) from public.comment_likes where comment_id=coalesce(new.comment_id,old.comment_id))
  where id=coalesce(new.comment_id,old.comment_id);
  return coalesce(new,old);
end $$;

drop trigger if exists trg_recalc_comment_like_count on public.comment_likes;
create trigger trg_recalc_comment_like_count after insert or delete on public.comment_likes
for each row execute function public.recalc_comment_like_count();

create or replace function public.recalc_feed_comment_count()
returns trigger language plpgsql security definer set search_path=public as $$
declare pid uuid;
begin
  pid:=coalesce(new.post_id,old.post_id);
  update public.feed_posts set comment_count=(select count(*) from public.comments where post_id=pid and parent_comment_id is null) where id=pid;
  return coalesce(new,old);
end $$;

drop trigger if exists trg_recalc_feed_comment_count on public.comments;
create trigger trg_recalc_feed_comment_count after insert or delete on public.comments
for each row execute function public.recalc_feed_comment_count();

create or replace function public.recalc_story_like_count()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  update public.stories set likes_count=(select count(*) from public.story_likes where story_id=coalesce(new.story_id,old.story_id)) where id=coalesce(new.story_id,old.story_id);
  return coalesce(new,old);
end $$;

drop trigger if exists trg_recalc_story_like_count on public.story_likes;
create trigger trg_recalc_story_like_count after insert or delete on public.story_likes
for each row execute function public.recalc_story_like_count();

create or replace function public.recalc_story_view_count()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  update public.stories set views_count=(select count(*) from public.story_views where story_id=coalesce(new.story_id,old.story_id)) where id=coalesce(new.story_id,old.story_id);
  return coalesce(new,old);
end $$;

drop trigger if exists trg_recalc_story_view_count on public.story_views;
create trigger trg_recalc_story_view_count after insert or delete on public.story_views
for each row execute function public.recalc_story_view_count();

create or replace function public.recalc_poll_option_vote_count()
returns trigger language plpgsql security definer set search_path=public as $$
begin
  update public.poll_options set vote_count=(select count(*) from public.poll_votes where option_id=coalesce(new.option_id,old.option_id)) where id=coalesce(new.option_id,old.option_id);
  return coalesce(new,old);
end $$;

drop trigger if exists trg_recalc_poll_option_vote_count on public.poll_votes;
create trigger trg_recalc_poll_option_vote_count after insert or delete on public.poll_votes
for each row execute function public.recalc_poll_option_vote_count();

-- Tighten poll ownership.
drop policy if exists polls_insert_own on public.polls;
create policy polls_insert_own on public.polls for insert to authenticated
with check (exists(select 1 from public.feed_posts fp where fp.id=post_id and fp.user_id=auth.uid()));
drop policy if exists polls_delete_own on public.polls;
create policy polls_delete_own on public.polls for delete to authenticated
using (exists(select 1 from public.feed_posts fp where fp.id=polls.post_id and fp.user_id=auth.uid()));
drop policy if exists poll_options_insert_own on public.poll_options;
create policy poll_options_insert_own on public.poll_options for insert to authenticated
with check (exists(select 1 from public.polls p join public.feed_posts fp on fp.id=p.post_id where p.id=poll_id and fp.user_id=auth.uid()));
drop policy if exists poll_options_delete_own on public.poll_options;
create policy poll_options_delete_own on public.poll_options for delete to authenticated
using (exists(select 1 from public.polls p join public.feed_posts fp on fp.id=p.post_id where p.id=poll_options.poll_id and fp.user_id=auth.uid()));

-- Stable marketplace ownership.
drop policy if exists market_items_insert_own on public.market_items;
create policy market_items_insert_own on public.market_items for insert to authenticated with check (auth.uid()=seller_id);
drop policy if exists market_items_update_own on public.market_items;
create policy market_items_update_own on public.market_items for update to authenticated using (auth.uid()=seller_id) with check (auth.uid()=seller_id);
drop policy if exists market_items_delete_own on public.market_items;
create policy market_items_delete_own on public.market_items for delete to authenticated using (auth.uid()=seller_id);

-- Correct study-circle member visibility predicate.
drop policy if exists circle_members_select on public.study_circle_members;
create policy circle_members_select on public.study_circle_members for select to authenticated using (
  exists(select 1 from public.study_circles c where c.id=study_circle_members.circle_id
    and (not c.is_private or c.owner_id=auth.uid() or exists(
      select 1 from public.study_circle_members m2 where m2.circle_id=study_circle_members.circle_id and m2.user_id=auth.uid()
    )))
);

-- Storage write policies match the Android users/<uuid>/... path. Public read remains unchanged.
do $$ begin
  drop policy if exists "profile_media_insert_own" on storage.objects;
  drop policy if exists "profile_media_update_own" on storage.objects;
  drop policy if exists "profile_media_delete_own" on storage.objects;
  create policy "profile_media_insert_own" on storage.objects for insert to authenticated
    with check (bucket_id='profile-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text);
  create policy "profile_media_update_own" on storage.objects for update to authenticated
    using (bucket_id='profile-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text)
    with check (bucket_id='profile-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text);
  create policy "profile_media_delete_own" on storage.objects for delete to authenticated
    using (bucket_id='profile-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text);

  drop policy if exists "post_media_insert_own" on storage.objects;
  drop policy if exists "post_media_update_own" on storage.objects;
  drop policy if exists "post_media_delete_own" on storage.objects;
  create policy "post_media_insert_own" on storage.objects for insert to authenticated
    with check (bucket_id='post-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text);
  create policy "post_media_update_own" on storage.objects for update to authenticated
    using (bucket_id='post-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text)
    with check (bucket_id='post-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text);
  create policy "post_media_delete_own" on storage.objects for delete to authenticated
    using (bucket_id='post-media' and (storage.foldername(name))[1]='users' and (storage.foldername(name))[2]=auth.uid()::text);
end $$;

-- Event rows are generated server-side so Activity survives device restarts.
create or replace function public.activity_from_post_like() returns trigger language plpgsql security definer set search_path=public as $$
declare owner_id uuid; begin
  select user_id into owner_id from public.feed_posts where id=new.post_id;
  if owner_id is not null and owner_id<>new.user_id then
    insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
    values(owner_id,new.user_id,'LIKE','post',new.post_id,'liked your post',false);
  end if; return new; end $$;
drop trigger if exists trg_activity_post_like on public.post_likes;
create trigger trg_activity_post_like after insert on public.post_likes for each row execute function public.activity_from_post_like();

create or replace function public.activity_from_post_comment() returns trigger language plpgsql security definer set search_path=public as $$
declare owner_id uuid; begin
  select user_id into owner_id from public.feed_posts where id=new.post_id;
  if owner_id is not null and owner_id<>new.author_id then
    insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
    values(owner_id,new.author_id,'COMMENT','post',new.post_id,'commented on your post',false);
  end if; return new; end $$;
drop trigger if exists trg_activity_post_comment on public.comments;
create trigger trg_activity_post_comment after insert on public.comments for each row execute function public.activity_from_post_comment();

create or replace function public.activity_from_bookmark() returns trigger language plpgsql security definer set search_path=public as $$
declare owner_id uuid; begin
  select user_id into owner_id from public.feed_posts where id=new.post_id;
  if owner_id is not null and owner_id<>new.user_id then
    insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
    values(owner_id,new.user_id,'SAVE','post',new.post_id,'saved your post',false);
  end if; return new; end $$;
drop trigger if exists trg_activity_post_bookmark on public.post_bookmarks;
create trigger trg_activity_post_bookmark after insert on public.post_bookmarks for each row execute function public.activity_from_bookmark();

create or replace function public.activity_from_connection() returns trigger language plpgsql security definer set search_path=public as $$
begin
  if new.status='pending' then
    insert into public.activities(recipient_id,actor_id,activity_type,entity_type,entity_id,message,is_read)
    values(new.receiver_id,new.sender_id,'CONNECTION','connection',new.id,'sent you a connection request',false);
  end if; return new; end $$;
drop trigger if exists trg_activity_connection_request on public.connection_requests;
create trigger trg_activity_connection_request after insert on public.connection_requests for each row execute function public.activity_from_connection();

-- Realtime coverage for user-facing collaborative state.
do $$ begin
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='post_bookmarks') then alter publication supabase_realtime add table public.post_bookmarks; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='poll_votes') then alter publication supabase_realtime add table public.poll_votes; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='story_replies') then alter publication supabase_realtime add table public.story_replies; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='story_views') then alter publication supabase_realtime add table public.story_views; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='roommate_profiles') then alter publication supabase_realtime add table public.roommate_profiles; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='roommate_applications') then alter publication supabase_realtime add table public.roommate_applications; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='study_circles') then alter publication supabase_realtime add table public.study_circles; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='study_circle_members') then alter publication supabase_realtime add table public.study_circle_members; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='skill_endorsements') then alter publication supabase_realtime add table public.skill_endorsements; end if;
end $$;
