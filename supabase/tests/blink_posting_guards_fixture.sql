-- Isolated PostgreSQL fixture: fake data only, never production credentials.
create role authenticated nologin;
create role anon nologin;
create schema auth;
create function auth.uid() returns uuid language sql stable as $f$
  select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid
$f$;
grant usage on schema auth to authenticated;
grant execute on function auth.uid() to authenticated;

create table public.feed_posts (
    id uuid primary key,
    audience text default 'Everyone'
);
create table public.polls (
    id uuid primary key,
    expires_at timestamptz
);
create table public.poll_options (
    id uuid primary key,
    poll_id uuid not null references public.polls(id)
);
create table public.poll_votes (
    id uuid primary key,
    poll_id uuid not null references public.polls(id),
    option_id uuid not null references public.poll_options(id),
    user_id uuid not null
);

grant usage on schema public to authenticated;
grant select on public.polls, public.poll_options to authenticated;
grant select,insert on public.poll_votes to authenticated;
grant select,insert on public.feed_posts to authenticated;

insert into public.polls (id,expires_at) values
('10000000-0000-0000-0000-000000000001', now() + interval '1 day'),
('10000000-0000-0000-0000-000000000002', now() - interval '1 day'),
('10000000-0000-0000-0000-000000000003', null);
insert into public.poll_options (id,poll_id) values
('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001'),
('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002'),
('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003');
