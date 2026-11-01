-- BLINK Contest topic directory, campus/nationwide scope and permanent public history.
-- destructive-change-reviewed: the safety scanner flags the DELETE inside the user-invoked unfollow RPC; this migration itself does not delete existing rows.
-- rollback-plan: revoke the new RPC grants and restore the replaced admin/list RPC definitions from the prior Contest migration; retain additive topic/history tables and snapshots so permanent history is never discarded.
-- Apply to Testlab only until backend and client verification pass.
begin;

create table if not exists public.blink_contest_topics (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name text not null unique check (length(trim(name)) between 3 and 100),
  category text not null check (length(category) between 2 and 60),
  description text not null default '',
  is_active boolean not null default true,
  sort_order integer not null default 100,
  created_at timestamptz not null default now()
);
alter table public.blink_contest_topics enable row level security;
revoke all on public.blink_contest_topics from public,anon,authenticated;
grant select,insert,update on public.blink_contest_topics to service_role;

insert into public.blink_contest_topics(slug,name,category,description,sort_order) values
('most-beautiful-female','Most Beautiful Female on Campus','Beauty & Fashion','Celebrate confidence, style and personality.',1),
('most-handsome-male','Most Handsome Male on Campus','Beauty & Fashion','Celebrate style, confidence and personality.',2),
('face-of-blink','Face of BLINK','Beauty & Fashion','A flagship BLINK personality and style competition.',3),
('campus-fashion-icon','Campus Fashion Icon','Beauty & Fashion','Showcase original campus fashion.',4),
('best-traditional-outfit','Best Traditional Outfit','Beauty & Fashion','Celebrate cultural fashion and creativity.',5),
('best-corporate-outfit','Best Corporate Outfit','Beauty & Fashion','Professional style and presentation.',6),
('best-casual-outfit','Best Casual Outfit','Beauty & Fashion','Everyday style and personal expression.',7),
('best-hairstyle','Best Hairstyle','Beauty & Fashion','Creative hair styling and presentation.',8),
('best-smile','Best Smile','Beauty & Fashion','A confidence and personality showcase.',9),
('best-photogenic-student','Best Photogenic Student','Beauty & Fashion','Portrait and camera presence competition.',10),
('campus-got-talent','Campus Got Talent','Entertainment','A multi-talent showcase for students.',11),
('best-singer','Best Singer','Entertainment','Vocal performance competition.',12),
('best-rapper','Best Rapper','Entertainment','Rap performance and originality.',13),
('best-dancer','Best Dancer','Entertainment','Dance performance and creativity.',14),
('best-spoken-word-artist','Best Spoken Word Artist','Entertainment','Poetry, spoken word and storytelling.',15),
('best-comedian','Best Comedian','Entertainment','Comedy, skits and original humour.',16),
('best-actor','Best Actor','Entertainment','Acting and screen performance.',17),
('best-actress','Best Actress','Entertainment','Acting and screen performance.',18),
('best-content-creator','Best Content Creator','Entertainment','Original and engaging digital content.',19),
('best-voice','Best Voice','Entertainment','Voice performance, narration and delivery.',20),
('best-tiktok-creator','Best TikTok Creator','Digital Creativity','Short-form video creativity.',21),
('best-instagram-creator','Best Instagram Creator','Digital Creativity','Visual storytelling and creator craft.',22),
('best-photographer','Best Photographer','Digital Creativity','Photography and visual storytelling.',23),
('best-videographer','Best Videographer','Digital Creativity','Video production and storytelling.',24),
('best-graphic-designer','Best Graphic Designer','Digital Creativity','Graphic design and visual communication.',25),
('best-meme-creator','Best Meme Creator','Digital Creativity','Original humour and meme creativity.',26),
('best-digital-artist','Best Digital Artist','Digital Creativity','Digital illustration and art.',27),
('best-campus-blogger','Best Campus Blogger','Digital Creativity','Campus reporting and blogging.',28),
('best-social-media-influencer','Best Social Media Influencer','Digital Creativity','Positive influence and audience engagement.',29),
('best-campus-podcast-host','Best Campus Podcast Host','Digital Creativity','Podcast hosting and conversations.',30),
('campus-brain','Campus Brain of the Year','Academics','Academic excellence and intellectual curiosity.',31),
('best-mathematics-student','Best Mathematics Student','Academics','Mathematical reasoning and problem solving.',32),
('best-science-student','Best Science Student','Academics','Scientific knowledge and curiosity.',33),
('best-debater','Best Debater','Academics','Reasoned argument and respectful debate.',34),
('best-public-speaker','Best Public Speaker','Academics','Public speaking and communication.',35),
('campus-quiz-champion','Campus Quiz Champion','Academics','Knowledge and quiz competition.',36),
('best-tech-innovator','Best Tech Innovator','Academics','Practical technology innovation.',37),
('best-research-idea','Best Research Idea','Academics','Research thinking and useful ideas.',38),
('best-problem-solver','Best Problem Solver','Academics','Creative solutions to real problems.',39),
('best-young-entrepreneur','Best Young Entrepreneur','Academics','Student enterprise and initiative.',40),
('most-popular-student','Most Popular Student','Personality','A community popularity recognition.',41),
('most-influential-student','Most Influential Student','Personality','Positive influence and contribution.',42),
('most-friendly-student','Most Friendly Student','Personality','Friendliness and community spirit.',43),
('most-charismatic-student','Most Charismatic Student','Personality','Presence and positive energy.',44),
('most-entertaining-student','Most Entertaining Student','Personality','Creativity and entertainment.',45),
('most-supportive-student','Most Supportive Student','Personality','Encouragement and support for others.',46),
('campus-personality-of-year','Campus Personality of the Year','Personality','An all-round campus personality award.',47),
('most-creative-student','Most Creative Student','Personality','Original thinking and expression.',48),
('most-inspiring-student','Most Inspiring Student','Personality','A story or contribution that inspires.',49),
('most-approachable-student','Most Approachable Student','Personality','Warmth and approachability.',50),
('cutest-campus-couple','Cutest Campus Couple','Relationships','A consensual couple showcase.',51),
('best-campus-friendship','Best Campus Friendship','Relationships','Celebrate friendship and support.',52),
('best-female-besties','Best Female Besties','Relationships','A friendship duo showcase.',53),
('best-male-besties','Best Male Besties','Relationships','A friendship duo showcase.',54),
('best-campus-squad','Best Squad on Campus','Relationships','Team friendship and campus spirit.',55),
('most-supportive-couple','Most Supportive Couple','Relationships','Celebrate mutual support.',56),
('best-friendship-story','Best Friendship Story','Relationships','Share a positive friendship story.',57),
('campus-best-duo','Campus Best Duo','Relationships','A two-person team competition.',58),
('best-roommates','Best Roommates','Relationships','Celebrate teamwork in shared living.',59),
('most-stylish-couple','Most Stylish Couple','Relationships','A couple fashion showcase.',60),
('best-footballer','Best Footballer','Sports & Fitness','Football skill and sportsmanship.',61),
('best-female-footballer','Best Female Footballer','Sports & Fitness','Women in football.',62),
('best-basketball-player','Best Basketball Player','Sports & Fitness','Basketball skill and sportsmanship.',63),
('best-volleyball-player','Best Volleyball Player','Sports & Fitness','Volleyball skill and teamwork.',64),
('best-athlete','Best Athlete','Sports & Fitness','Athletic performance and dedication.',65),
('best-fitness-dancer','Best Fitness Dance','Sports & Fitness','Fitness dance and movement.',66),
('best-gym-transformation','Best Gym Transformation','Sports & Fitness','A personal fitness progress showcase.',67),
('best-campus-sports-team','Campus Sports Team','Sports & Fitness','Teamwork and sports achievement.',68),
('best-table-tennis-player','Best Table Tennis Player','Sports & Fitness','Table tennis skill and sportsmanship.',69),
('campus-sports-personality','Campus Sports Personality','Sports & Fitness','Sports leadership and contribution.',70),
('best-student-business','Best Student Business','Entrepreneurship','Student-owned business showcase.',71),
('best-fashion-entrepreneur','Best Fashion Entrepreneur','Entrepreneurship','Fashion business and enterprise.',72),
('best-tech-startup','Best Tech Startup','Entrepreneurship','Student technology venture.',73),
('best-student-brand','Best Student Brand','Entrepreneurship','Brand building and originality.',74),
('best-food-entrepreneur','Best Food Entrepreneur','Entrepreneurship','Student food business showcase.',75),
('best-student-inventor','Best Student Inventor','Entrepreneurship','Useful invention and innovation.',76),
('best-business-pitch','Best Business Pitch','Entrepreneurship','Business idea presentation.',77),
('best-app-developer','Best App Developer','Entrepreneurship','Software and app development.',78),
('best-campus-vendor','Best Campus Vendor','Entrepreneurship','Student-focused service and commerce.',79),
('young-entrepreneur-of-year','Young Entrepreneur of the Year','Entrepreneurship','Outstanding student enterprise.',80),
('best-hostel-room-setup','Best Hostel Room Setup','Campus Lifestyle','Room organization and creativity.',81),
('best-room-decoration','Best Room Decoration','Campus Lifestyle','Interior creativity and design.',82),
('best-campus-photography','Best Campus Photography','Campus Lifestyle','Capture campus life.',83),
('best-campus-adventure','Best Campus Adventure','Campus Lifestyle','Campus experiences and exploration.',84),
('best-campus-lifestyle-creator','Best Campus Lifestyle Creator','Campus Lifestyle','Positive campus lifestyle content.',85),
('best-student-cook','Best Student Cook','Campus Lifestyle','Student cooking and food creativity.',86),
('best-food-presentation','Best Food Presentation','Campus Lifestyle','Food styling and presentation.',87),
('best-campus-couple-photoshoot','Best Campus Couple Photoshoot','Campus Lifestyle','Creative couple photography.',88),
('best-graduation-look','Best Campus Graduation Look','Campus Lifestyle','Graduation fashion showcase.',89),
('best-campus-memory','Best Campus Memory','Campus Lifestyle','A memorable campus story.',90),
('blink-star-of-month','BLINK Star of the Month','BLINK Exclusives','Monthly community spotlight.',91),
('blink-creator-of-month','BLINK Creator of the Month','BLINK Exclusives','Monthly creator recognition.',92),
('blink-rising-star','BLINK Rising Star','BLINK Exclusives','Spotlight emerging talent.',93),
('blink-campus-ambassador','BLINK Campus Ambassador','BLINK Exclusives','Campus representation and service.',94),
('blink-influencer-challenge','BLINK Influencer Challenge','BLINK Exclusives','A themed creator challenge.',95),
('blink-dance-challenge','BLINK Dance Challenge','BLINK Exclusives','A recurring dance challenge.',96),
('blink-photography-challenge','BLINK Photography Challenge','BLINK Exclusives','A themed photography challenge.',97),
('blink-comedy-challenge','BLINK Comedy Challenge','BLINK Exclusives','A recurring comedy challenge.',98),
('blink-creative-challenge','BLINK Creative Challenge','BLINK Exclusives','A cross-discipline creativity challenge.',99),
('blink-personality-of-month','BLINK Personality of the Month','BLINK Exclusives','Monthly community personality award.',100)
on conflict(slug) do update set name=excluded.name,category=excluded.category,
 description=excluded.description,sort_order=excluded.sort_order;

alter table public.blink_contests
  add column if not exists topic_id uuid references public.blink_contest_topics(id) on delete restrict,
  add column if not exists competition_scope text not null default 'campus'
    check (competition_scope in ('campus','nationwide')),
  add column if not exists season text check (season is null or length(season)<=80);
create index if not exists blink_contests_topic_scope_idx
  on public.blink_contests(topic_id,competition_scope,status,created_at desc);

-- Backfill existing contests into a matching topic, retaining all original contest records.
update public.blink_contests c set topic_id=t.id
from public.blink_contest_topics t
where c.topic_id is null and (
  lower(regexp_replace(c.category,'[^a-z0-9]+','-','g'))=t.slug
  or lower(regexp_replace(c.title,'[^a-z0-9]+','-','g'))=t.slug
);

create table if not exists public.blink_contest_topic_follows (
  topic_id uuid not null references public.blink_contest_topics(id) on delete restrict,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(topic_id,user_id)
);
alter table public.blink_contest_topic_follows enable row level security;
revoke all on public.blink_contest_topic_follows from public,anon,authenticated;
grant select,insert,delete on public.blink_contest_topic_follows to service_role;

-- A snapshot survives account/profile deletion and protects completed public history.
create table if not exists public.blink_contest_history_participants (
  id uuid primary key default gen_random_uuid(),
  contest_id uuid not null references public.blink_contests(id) on delete restrict,
  entry_id uuid,
  user_id uuid,
  username text not null,
  avatar_url text,
  votes integer not null default 0 check (votes>=0),
  final_place integer check (final_place between 1 and 100000),
  prize_ngn integer not null default 0 check (prize_ngn>=0),
  captured_at timestamptz not null default now(),
  unique(contest_id,entry_id)
);
create index if not exists blink_contest_history_topic_idx
  on public.blink_contest_history_participants(contest_id,final_place,votes desc);
alter table public.blink_contest_history_participants enable row level security;
revoke all on public.blink_contest_history_participants from public,anon,authenticated;
grant select,insert on public.blink_contest_history_participants to service_role;

create or replace function private.blink_snapshot_contest_history()
returns trigger language plpgsql security definer set search_path='' as $$
begin
  if new.status='completed' and old.status is distinct from 'completed' then
    insert into public.blink_contest_history_participants(
      contest_id,entry_id,user_id,username,avatar_url,votes,final_place,prize_ngn)
    select new.id,e.id,e.user_id,coalesce(p.username,'Former BLINK user'),
      p.avatar_url,count(v.id) filter(where v.review_status='valid')::integer,
      w.place,coalesce(w.amount_ngn,0)
    from public.blink_contest_entries e
    left join public.profiles p on p.id=e.user_id
    left join public.blink_contest_votes v on v.entry_id=e.id and v.review_status='valid'
    left join public.blink_contest_prizes w on w.contest_id=e.contest_id and w.entry_id=e.id
    where e.contest_id=new.id and e.status in ('approved','disqualified')
    group by e.id,e.user_id,p.username,p.avatar_url,w.place,w.amount_ngn
    on conflict(contest_id,entry_id) do nothing;
  end if;
  return new;
end; $$;
revoke all on function private.blink_snapshot_contest_history() from public,anon,authenticated;
drop trigger if exists blink_contest_snapshot_history on public.blink_contests;
create trigger blink_contest_snapshot_history
after update of status on public.blink_contests
for each row execute function private.blink_snapshot_contest_history();

-- Preserve history for contests completed before this migration is installed.
insert into public.blink_contest_history_participants(
  contest_id,entry_id,user_id,username,avatar_url,votes,final_place,prize_ngn)
select c.id,e.id,e.user_id,coalesce(p.username,'Former BLINK user'),p.avatar_url,
  count(v.id) filter(where v.review_status='valid')::integer,w.place,coalesce(w.amount_ngn,0)
from public.blink_contests c
join public.blink_contest_entries e on e.contest_id=c.id
left join public.profiles p on p.id=e.user_id
left join public.blink_contest_votes v on v.entry_id=e.id and v.review_status='valid'
left join public.blink_contest_prizes w on w.contest_id=c.id and w.entry_id=e.id
where c.status='completed' and e.status in ('approved','disqualified')
group by c.id,e.id,e.user_id,p.username,p.avatar_url,w.place,w.amount_ngn
on conflict(contest_id,entry_id) do nothing;

create or replace function public.list_blink_contest_topics()
returns jsonb language sql stable security definer set search_path='' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',t.id,'slug',t.slug,'name',t.name,'category',t.category,
    'description',t.description,'sort_order',t.sort_order,
    'followers',(select count(*) from public.blink_contest_topic_follows f where f.topic_id=t.id),
    'contests_total',(select count(*) from public.blink_contests c
      where c.topic_id=t.id and c.status in ('published','paused','completed','cancelled')),
    'completed_contests',(select count(*) from public.blink_contests c
      where c.topic_id=t.id and c.status='completed'),
    'ongoing_contests',(select count(*) from public.blink_contests c
      where c.topic_id=t.id and c.status in ('published','paused')
        and now()>=c.registration_opens_at and now()<c.voting_closes_at),
    'upcoming_contests',(select count(*) from public.blink_contests c
      where c.topic_id=t.id and c.status='published' and now()<c.registration_opens_at),
    'participants',(select count(distinct coalesce(h.user_id,e.user_id))
      from public.blink_contests c
      left join public.blink_contest_history_participants h on h.contest_id=c.id
      left join public.blink_contest_entries e on e.contest_id=c.id and e.status='approved'
      where c.topic_id=t.id and (h.id is not null or e.id is not null)),
    'votes',(select coalesce(sum(x.votes),0) from (
      select h.votes from public.blink_contests c
        join public.blink_contest_history_participants h on h.contest_id=c.id
        where c.topic_id=t.id and c.status='completed'
      union all
      select count(v.id)::integer from public.blink_contests c
        join public.blink_contest_entries e on e.contest_id=c.id and e.status='approved'
        join public.blink_contest_votes v on v.entry_id=e.id and v.review_status='valid'
        where c.topic_id=t.id and c.status<>'completed'
      group by c.id) x),
    'prizes_awarded',(select coalesce(sum(h.prize_ngn),0) from public.blink_contests c
      join public.blink_contest_history_participants h on h.contest_id=c.id
      where c.topic_id=t.id)
  ) order by t.sort_order,t.name),'[]'::jsonb)
  from public.blink_contest_topics t where t.is_active;
$$;

create or replace function public.get_blink_contest_topic(p_topic_id uuid)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare t public.blink_contest_topics%rowtype; v_contests jsonb; v_people jsonb;
begin
  select * into t from public.blink_contest_topics where id=p_topic_id and is_active;
  if not found then raise exception 'CONTEST_TOPIC_NOT_FOUND'; end if;
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'title',c.title,'description',c.description,'status',c.status,
    'competition_scope',c.competition_scope,'university',c.university,'season',c.season,
    'banner_url',c.banner_url,'registration_opens_at',c.registration_opens_at,
    'registration_closes_at',c.registration_closes_at,'voting_opens_at',c.voting_opens_at,
    'voting_closes_at',c.voting_closes_at,'results_at',c.results_at,
    'prize_first',c.prize_first,'prize_second',c.prize_second,'prize_third',c.prize_third,
    'participants',(select count(*) from public.blink_contest_entries e
      where e.contest_id=c.id and e.status='approved'),
    'votes',(select count(*) from public.blink_contest_votes v
      join public.blink_contest_entries e on e.id=v.entry_id
      where v.contest_id=c.id and e.status='approved' and v.review_status='valid')
  ) order by case when c.status='published' and now()<c.registration_opens_at then 0
    when c.status='published' and now()<c.voting_closes_at then 1
    when c.status='completed' then 2 else 3 end,c.registration_opens_at),'[]'::jsonb)
  into v_contests from public.blink_contests c
  where c.topic_id=t.id and c.status in ('published','paused','completed','cancelled');

  select coalesce(jsonb_agg(jsonb_build_object(
    'user_id',z.user_id,'username',z.username,'avatar_url',z.avatar_url,
    'contest_id',z.contest_id,'contest_title',z.contest_title,
    'competition_scope',z.competition_scope,'university',z.university,
    'votes',z.votes,'place',z.final_place,'prize_ngn',z.prize_ngn,
    'historical',z.historical
  ) order by z.historical desc,z.votes desc,z.username),'[]'::jsonb)
  into v_people from (
    select h.user_id,h.username,h.avatar_url,h.contest_id,c.title contest_title,
      c.competition_scope,c.university,h.votes,h.final_place,h.prize_ngn,true historical
    from public.blink_contest_history_participants h
    join public.blink_contests c on c.id=h.contest_id where c.topic_id=t.id
    union all
    select e.user_id,coalesce(p.username,'BLINK user'),p.avatar_url,e.contest_id,c.title,
      c.competition_scope,c.university,
      (select count(*)::integer from public.blink_contest_votes v
       where v.entry_id=e.id and v.review_status='valid'),null::integer,0,false
    from public.blink_contest_entries e
    join public.blink_contests c on c.id=e.contest_id
    left join public.profiles p on p.id=e.user_id
    where c.topic_id=t.id and c.status<>'completed' and e.status='approved'
  ) z;

  return jsonb_build_object('topic',jsonb_build_object(
    'id',t.id,'slug',t.slug,'name',t.name,'category',t.category,'description',t.description,
    'following',exists(select 1 from public.blink_contest_topic_follows f where f.topic_id=t.id and f.user_id=auth.uid())),
    'contests',v_contests,'participants',v_people,
    'total_contests',(select count(*) from public.blink_contests where topic_id=t.id and status in ('published','paused','completed','cancelled')),
    'completed_contests',(select count(*) from public.blink_contests where topic_id=t.id and status='completed'),
    'total_participants',(select count(distinct person_id) from (
      select h.user_id person_id from public.blink_contest_history_participants h
        join public.blink_contests c on c.id=h.contest_id where c.topic_id=t.id
      union
      select e.user_id from public.blink_contest_entries e
        join public.blink_contests c on c.id=e.contest_id
        where c.topic_id=t.id and c.status<>'completed' and e.status='approved'
    ) unique_people),
    'total_votes',coalesce((select sum((x->>'votes')::integer) from jsonb_array_elements(v_people) x),0),
    'prizes_awarded',(select coalesce(sum(prize_ngn),0) from public.blink_contest_history_participants h
      join public.blink_contests c on c.id=h.contest_id where c.topic_id=t.id));
end; $$;

create or replace function public.admin_save_blink_contest_topic(p_id uuid,p_data jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v_id uuid; v_slug text; v_name text; v_category text; v_description text;
begin
  perform private.require_blink_admin();
  if p_data is null or jsonb_typeof(p_data)<>'object' or octet_length(p_data::text)>3000
    then raise exception 'INVALID_TOPIC'; end if;
  v_name:=trim(coalesce(p_data->>'name',''));
  v_category:=trim(coalesce(p_data->>'category',''));
  v_slug:=lower(trim(both '-' from regexp_replace(v_name,'[^a-zA-Z0-9]+','-','g')));
  v_description:=trim(coalesce(p_data->>'description',''));
  if length(v_name) not between 3 and 100 or length(v_category) not between 2 and 60
    or length(v_description)>500 or v_slug='' then raise exception 'INVALID_TOPIC'; end if;
  if p_id is null then
    insert into public.blink_contest_topics(slug,name,category,description,sort_order)
      values(v_slug,v_name,v_category,v_description,500)
      returning id into v_id;
  else
    update public.blink_contest_topics set name=v_name,category=v_category,
      description=v_description where id=p_id returning id into v_id;
    if v_id is null then raise exception 'CONTEST_TOPIC_NOT_FOUND'; end if;
  end if;
  return (select to_jsonb(t) from public.blink_contest_topics t where id=v_id);
end; $$;

create or replace function public.admin_set_blink_contest_topic_active(p_topic_id uuid,p_active boolean)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
  perform private.require_blink_admin();
  update public.blink_contest_topics set is_active=p_active where id=p_topic_id;
  if not found then raise exception 'CONTEST_TOPIC_NOT_FOUND'; end if;
  return jsonb_build_object('id',p_topic_id,'is_active',p_active);
end; $$;

create or replace function private.blink_notify_followed_contest_topic()
returns trigger language plpgsql security definer set search_path='' as $$
declare v_topic_name text;
begin
  if new.status='published' and old.status is distinct from 'published'
     and new.topic_id is not null then
    select name into v_topic_name from public.blink_contest_topics where id=new.topic_id;
    insert into public.notifications(user_id,actor_id,type,target_type,target_id,
      text,sub_text,is_read,metadata)
    select f.user_id,new.created_by,'system'::public.notification_type_enum,
      'contest',new.id,left('A new contest is scheduled in a topic you follow',180),
      left(coalesce(v_topic_name,'BLINK Contest')||' · '||new.title,240),false,
      jsonb_build_object('kind','blink_contest_topic_update','topic_id',new.topic_id,
        'contest_id',new.id,'contest_title',new.title)
    from public.blink_contest_topic_follows f
    where f.topic_id=new.topic_id and f.user_id<>new.created_by
      and not exists(select 1 from public.notifications n
        where n.user_id=f.user_id and n.target_id=new.id
          and n.metadata->>'kind'='blink_contest_topic_update');
  end if;
  return new;
end; $$;
revoke all on function private.blink_notify_followed_contest_topic() from public,anon,authenticated;
drop trigger if exists blink_contest_topic_follow_notification on public.blink_contests;
create trigger blink_contest_topic_follow_notification
after update of status on public.blink_contests
for each row execute function private.blink_notify_followed_contest_topic();

create or replace function public.get_blink_contest_participant_profile(p_user_id uuid)
returns jsonb language plpgsql stable security definer set search_path='' as $profile$
declare v_profile jsonb; v_history jsonb; v_wins integer; v_votes bigint; v_prizes bigint; v_total integer;
begin
  if auth.uid() is null then raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  if p_user_id is null then raise exception 'PARTICIPANT_NOT_FOUND'; end if;
  select jsonb_build_object('user_id',p.id,'username',p.username,'avatar_url',p.avatar_url,
    'university',p.university)
    into v_profile from public.profiles p where p.id=p_user_id;
  select coalesce(jsonb_agg(x.row_data order by x.event_at desc),'[]'::jsonb),
    count(*) filter(where x.place=1)::integer,
    coalesce(sum(x.votes),0),coalesce(sum(x.prize_ngn),0),count(*)::integer
  into v_history,v_wins,v_votes,v_prizes,v_total
  from (
    select jsonb_build_object('contest_id',h.contest_id,'contest_title',c.title,
      'topic_id',c.topic_id,'scope',c.competition_scope,'university',c.university,
      'votes',h.votes,'place',h.final_place,'prize_ngn',h.prize_ngn,
      'status','completed','season',c.season) row_data,
      h.captured_at event_at,h.final_place place,h.votes,h.prize_ngn
    from public.blink_contest_history_participants h
    join public.blink_contests c on c.id=h.contest_id
    where h.user_id=p_user_id
    union all
    select jsonb_build_object('contest_id',e.contest_id,'contest_title',c.title,
      'topic_id',c.topic_id,'scope',c.competition_scope,'university',c.university,
      'votes',(select count(*)::integer from public.blink_contest_votes v
        where v.entry_id=e.id and v.review_status='valid'),
      'place',null,'prize_ngn',0,'status',c.status,'season',c.season),
      e.submitted_at,null::integer,
      (select count(*)::integer from public.blink_contest_votes v
        where v.entry_id=e.id and v.review_status='valid'),0::bigint
    from public.blink_contest_entries e join public.blink_contests c on c.id=e.contest_id
    where e.user_id=p_user_id and c.status<>'completed' and e.status in ('approved','pending')
  ) x;
  if v_profile is null and v_total=0 then raise exception 'PARTICIPANT_NOT_FOUND'; end if;
  if v_profile is null then
    select jsonb_build_object('user_id',p_user_id,'username',h.username,'avatar_url',h.avatar_url)
      into v_profile from public.blink_contest_history_participants h
      where h.user_id=p_user_id order by h.captured_at desc limit 1;
  end if;
  return jsonb_build_object('participant',v_profile,'contests_entered',v_total,
    'wins',v_wins,'total_votes',v_votes,'prizes_won_ngn',v_prizes,'history',v_history);
end; $profile$;

create or replace function public.follow_blink_contest_topic(p_topic_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
  if auth.uid() is null then raise exception 'SIGN_IN_REQUIRED' using errcode='42501'; end if;
  if not exists(select 1 from public.blink_contest_topics where id=p_topic_id and is_active)
    then raise exception 'CONTEST_TOPIC_NOT_FOUND'; end if;
  insert into public.blink_contest_topic_follows(topic_id,user_id)
    values(p_topic_id,auth.uid()) on conflict do nothing;
  return jsonb_build_object('following',true);
end; $$;
create or replace function public.unfollow_blink_contest_topic(p_topic_id uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
begin
  delete from public.blink_contest_topic_follows where topic_id=p_topic_id and user_id=auth.uid();
  return jsonb_build_object('following',false);
end; $$;

-- Admin topic creation is append-only; topics are deactivated, never hard-deleted.
create or replace function public.admin_save_blink_contest(p_id uuid,p_data jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare u uuid:=private.require_blink_admin(); c public.blink_contests%rowtype;
  ro timestamptz; rc timestamptz; vo timestamptz; vc timestamptz; ra timestamptz;
  f int; s int; t int; v_title text; v_topic uuid; v_scope text;
begin
  if p_data is null or jsonb_typeof(p_data)<>'object' or octet_length(p_data::text)>10000
    then raise exception 'INVALID_CONTEST'; end if;
  v_title:=trim(coalesce(p_data->>'title',''));
  ro:=(p_data->>'registration_opens_at')::timestamptz;
  rc:=(p_data->>'registration_closes_at')::timestamptz;
  vo:=(p_data->>'voting_opens_at')::timestamptz;
  vc:=(p_data->>'voting_closes_at')::timestamptz;
  ra:=(p_data->>'results_at')::timestamptz;
  f:=coalesce((p_data->>'prize_first')::int,0);
  s:=coalesce((p_data->>'prize_second')::int,0);
  t:=coalesce((p_data->>'prize_third')::int,0);
  v_topic:=nullif(p_data->>'topic_id','')::uuid;
  v_scope:=coalesce(nullif(p_data->>'competition_scope',''),'campus');
  if v_topic is not null and not exists(select 1 from public.blink_contest_topics where id=v_topic and is_active)
    then raise exception 'INVALID_CONTEST_TOPIC'; end if;
  if v_topic is null then raise exception 'CONTEST_TOPIC_REQUIRED'; end if;
  if v_scope not in ('campus','nationwide') then raise exception 'INVALID_COMPETITION_SCOPE'; end if;
  if v_scope='campus' and nullif(trim(p_data->>'university'),'') is null
    then raise exception 'CAMPUS_COMPETITION_REQUIRES_UNIVERSITY'; end if;
  if length(v_title) not between 4 and 100 or f<0 or s<0 or t<0 or
    ro is null or rc is null or vo is null or vc is null or ra is null or
    not(ro<rc and rc<=vo and vo<vc and vc<=ra) then
      raise exception 'INVALID_CONTEST_CONFIGURATION'; end if;
  if p_id is not null then
    select * into c from public.blink_contests where id=p_id for update;
    if not found or c.status<>'draft' then raise exception 'ONLY_DRAFTS_EDITABLE'; end if;
    update public.blink_contests set title=v_title,description=coalesce(p_data->>'description',''),
      rules=coalesce(p_data->>'rules',''),category=coalesce(p_data->>'category','General'),
      topic_id=v_topic,competition_scope=v_scope,season=nullif(trim(p_data->>'season'),''),
      banner_url=nullif(p_data->>'banner_url',''),university=nullif(p_data->>'university',''),
      registration_opens_at=ro,registration_closes_at=rc,voting_opens_at=vo,
      voting_closes_at=vc,results_at=ra,prize_first=f,prize_second=s,prize_third=t,
      max_entries=nullif(p_data->>'max_entries','')::integer,
      auto_approve=coalesce((p_data->>'auto_approve')::boolean,false),updated_at=now()
      where id=p_id returning * into c;
  else
    insert into public.blink_contests(title,description,rules,category,topic_id,
      competition_scope,season,banner_url,university,registration_opens_at,
      registration_closes_at,voting_opens_at,voting_closes_at,results_at,
      prize_first,prize_second,prize_third,max_entries,auto_approve,created_by)
    values(v_title,coalesce(p_data->>'description',''),coalesce(p_data->>'rules',''),
      coalesce(p_data->>'category','General'),v_topic,v_scope,
      nullif(trim(p_data->>'season'),''),nullif(p_data->>'banner_url',''),
      nullif(p_data->>'university',''),ro,rc,vo,vc,ra,f,s,t,
      nullif(p_data->>'max_entries','')::integer,
      coalesce((p_data->>'auto_approve')::boolean,false),u)
    returning * into c;
  end if;
  return to_jsonb(c);
end; $$;

create or replace function public.list_blink_contests()
returns jsonb language sql stable security definer set search_path='' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'title',c.title,'description',c.description,'rules',c.rules,
    'category',c.category,'topic_id',c.topic_id,'competition_scope',c.competition_scope,
    'season',c.season,'banner_url',c.banner_url,'university',c.university,
    'prize_first',c.prize_first,'prize_second',c.prize_second,'prize_third',c.prize_third,
    'registration_opens_at',c.registration_opens_at,'registration_closes_at',c.registration_closes_at,
    'voting_opens_at',c.voting_opens_at,'voting_closes_at',c.voting_closes_at,'results_at',c.results_at,
    'status',case when c.status='completed' then 'completed' when c.status='cancelled' then 'cancelled'
      when c.status='paused' then 'paused' when now()<c.registration_opens_at then 'upcoming'
      when now()<c.registration_closes_at then 'registration' when now()<c.voting_opens_at then 'upcoming'
      when now()<c.voting_closes_at then 'voting' else 'review' end,
    'contestants',(select count(*) from public.blink_contest_entries e
      where e.contest_id=c.id and e.status in ('approved','pending'))
  ) order by c.voting_opens_at desc),'[]'::jsonb)
  from public.blink_contests c where c.status in ('published','paused','completed');
$$;

revoke all on function public.list_blink_contest_topics(),public.get_blink_contest_topic(uuid),public.get_blink_contest_participant_profile(uuid),
 public.follow_blink_contest_topic(uuid),public.unfollow_blink_contest_topic(uuid),
 public.admin_save_blink_contest_topic(uuid,jsonb),public.admin_set_blink_contest_topic_active(uuid,boolean) from public,anon,authenticated;
grant execute on function public.list_blink_contest_topics(),public.get_blink_contest_topic(uuid),public.get_blink_contest_participant_profile(uuid),
 public.follow_blink_contest_topic(uuid),public.unfollow_blink_contest_topic(uuid) to authenticated;
grant execute on function public.admin_save_blink_contest_topic(uuid,jsonb),
 public.admin_set_blink_contest_topic_active(uuid,boolean) to authenticated;
revoke all on function public.admin_save_blink_contest(uuid,jsonb) from public,anon,authenticated;
grant execute on function public.admin_save_blink_contest(uuid,jsonb) to authenticated;
revoke all on function public.list_blink_contests() from public,anon;
grant execute on function public.list_blink_contests() to authenticated;

notify pgrst,'reload schema';
commit;
