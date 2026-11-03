-- Professional Connect directory catalog.
-- The Android client reads this table to render the 20 vertical Connect entries.
-- Each entry routes to one of the existing, fully-backed Connect workflows.

create table if not exists public.connect_category_catalog (
    id uuid primary key default gen_random_uuid(),
    slug text not null unique check (slug ~ '^[a-z0-9_]+$'),
    title text not null,
    description text not null default '',
    route_kind text not null check (route_kind in ('roommate','mentor','reading','agents','housing','challenges')),
    icon_key text not null default 'people',
    display_order smallint not null unique check (display_order between 1 and 100),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.connect_category_catalog enable row level security;

-- Explicit grants keep this compatible with Supabase Data API exposure changes.
revoke all on table public.connect_category_catalog from anon;
revoke all on table public.connect_category_catalog from authenticated;
grant select on table public.connect_category_catalog to authenticated;
grant all on table public.connect_category_catalog to service_role;

drop policy if exists connect_category_catalog_read on public.connect_category_catalog;
create policy connect_category_catalog_read
on public.connect_category_catalog
for select
to authenticated
using (is_active = true);

insert into public.connect_category_catalog
    (slug, title, description, route_kind, icon_key, display_order, is_active)
values
    ('roommates', 'Roommates', 'Find compatible students who are actively looking to share accommodation.', 'roommate', 'home', 1, true),
    ('mentors', 'Mentors', 'Connect with senior students and skilled peers who can guide you.', 'mentor', 'school', 2, true),
    ('reading_mates', 'Reading Mates', 'Meet focused study partners for courses, revision and exam preparation.', 'reading', 'book', 3, true),
    ('housing_agents', 'Verified Housing Agents', 'Browse reviewed housing agents and start a direct conversation safely.', 'agents', 'verified', 4, true),
    ('accommodation_requests', 'Accommodation Requests', 'See students who need accommodation and available housing support.', 'housing', 'apartment', 5, true),
    ('study_partners', 'Study Partners', 'Find accountability-focused students who want a consistent study routine.', 'reading', 'book', 6, true),
    ('project_teammates', 'Project Teammates', 'Discover students to collaborate with on coursework and practical projects.', 'reading', 'people', 7, true),
    ('skill_exchange', 'Skill Exchange', 'Teach what you know and connect with people who can teach you something new.', 'mentor', 'school', 8, true),
    ('career_guidance', 'Career Guidance', 'Meet experienced peers for CV, portfolio, interview and career direction support.', 'mentor', 'school', 9, true),
    ('internship_network', 'Internship Network', 'Connect with students preparing for internships, SIWES and early-career opportunities.', 'mentor', 'people', 10, true),
    ('research_partners', 'Research Partners', 'Find collaborators for surveys, data collection, papers and academic research.', 'reading', 'book', 11, true),
    ('founders_builders', 'Founders & Builders', 'Meet students building startups, products, communities and campus ventures.', 'mentor', 'people', 12, true),
    ('freelance_collaborators', 'Freelance Collaborators', 'Network with creatives, developers and service providers for legitimate projects.', 'mentor', 'people', 13, true),
    ('campus_communities', 'Campus Communities', 'Discover people with shared academic, creative and professional interests.', 'reading', 'people', 14, true),
    ('event_partners', 'Event Partners', 'Find dependable partners for academic, media, club and campus events.', 'reading', 'people', 15, true),
    ('accountability_partners', 'Accountability Partners', 'Connect with peers who can help you stay consistent with goals and deadlines.', 'reading', 'book', 16, true),
    ('alumni_network', 'Alumni & Senior Network', 'Use the mentor network to reach experienced students and graduates for guidance.', 'mentor', 'school', 17, true),
    ('course_tutors', 'Course Tutors', 'Find students offering subject-specific explanations, revision help and tutoring.', 'mentor', 'school', 18, true),
    ('relocation_support', 'Housing & Relocation Support', 'Get help navigating accommodation needs, locations and verified housing options.', 'housing', 'apartment', 19, true),
    ('game_challenges', 'Game Challenges', 'Connect through quick friendly challenge games and pending invitations.', 'challenges', 'games', 20, true)
on conflict (slug) do update set
    title = excluded.title,
    description = excluded.description,
    route_kind = excluded.route_kind,
    icon_key = excluded.icon_key,
    display_order = excluded.display_order,
    is_active = excluded.is_active,
    updated_at = now();
