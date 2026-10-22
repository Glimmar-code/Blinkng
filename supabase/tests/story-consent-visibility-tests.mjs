import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

/**
 * Phase 4 security contract on a disposable PostgreSQL process.
 * No Supabase production project is touched.
 */
export async function storyConsentVisibilityTests() {
  const db = new PGlite();
  const ids = {
    author: '00000000-0000-4000-8000-000000000601',
    viewer: '00000000-0000-4000-8000-000000000602',
    blocked: '00000000-0000-4000-8000-000000000603',
    muted: '00000000-0000-4000-8000-000000000604',
    original: '00000000-0000-4000-8000-000000000605',
    mutedStory: '00000000-0000-4000-8000-000000000606',
    expired: '00000000-0000-4000-8000-000000000607',
  };
  const q = s => db.query(s);
  const asUser = async uid => {
    await db.exec(`reset role;
       select set_config('request.jwt.claim.sub','${uid}',false);
       set role authenticated;`);
  };
  const migration = fs.readFileSync(new URL(
    '../migrations/20261022045000_story_repost_consent_and_visibility.sql',
    import.meta.url), 'utf8');

  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create schema auth;
      create function auth.uid() returns uuid language sql stable
        as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema public,auth to authenticated;
      grant execute on function auth.uid() to authenticated;
      create table public.stories (
        id uuid primary key default gen_random_uuid(),
        user_id uuid not null,
        active boolean default true,
        views_count integer default 0,
        created_at timestamptz default now(),
        media_url text,
        media_type text default 'image',
        image_url text,
        video_url text,
        caption text,
        text text,
        background text,
        audio_title text,
        likes_count integer default 0
      );
      create table public.blocks (blocker_id uuid not null, blocked_id uuid not null,
        primary key(blocker_id,blocked_id));
      create table public.muted_users (user_id uuid not null, muted_id uuid not null);
      create table public.story_views (
        id uuid primary key default gen_random_uuid(),
        story_id uuid not null, user_id uuid not null
      );
      create table public.story_likes (
        id uuid primary key default gen_random_uuid(),
        story_id uuid not null, user_id uuid not null
      );
      create table public.story_reactions (
        id uuid primary key default gen_random_uuid(),
        story_id uuid not null, user_id uuid not null
      );
      create table public.story_replies (
        id uuid primary key default gen_random_uuid(),
        story_id uuid not null, user_id uuid not null
      );
      grant select,insert,update,delete on all tables in schema public to authenticated;
      alter table public.stories enable row level security;
      create policy select_stories on public.stories for select
        to authenticated using (true);
      create policy insert_stories on public.stories for insert
        to authenticated with check (auth.uid()=user_id);
      create policy update_stories on public.stories for update
        to authenticated using (auth.uid()=user_id)
        with check (auth.uid()=user_id);
      create policy delete_stories on public.stories for delete
        to authenticated using (auth.uid()=user_id);
      alter table public.blocks enable row level security;
      create policy blocks_select_own on public.blocks for select to authenticated
        using (blocker_id=auth.uid());
      alter table public.muted_users enable row level security;
      create policy muted_select_own on public.muted_users for select to authenticated
        using (user_id=auth.uid());
      alter table public.story_views enable row level security;
      create policy story_views_insert_own on public.story_views for insert
        to authenticated with check (auth.uid()=user_id);
      alter table public.story_likes enable row level security;
      create policy story_likes_insert_own on public.story_likes for insert
        to authenticated with check (auth.uid()=user_id);
      alter table public.story_reactions enable row level security;
      create policy story_reactions_insert_own on public.story_reactions for insert
        to authenticated with check (auth.uid()=user_id);
      alter table public.story_replies enable row level security;
      create policy story_replies_insert_own on public.story_replies for insert
        to authenticated with check (auth.uid()=user_id);
      insert into public.stories(
        id,user_id,active,created_at,media_url,image_url,caption
      ) values
        ('${ids.original}','${ids.author}',true,now()-interval '1 hour',
          'https://example.invalid/one.jpg','https://example.invalid/one.jpg','original'),
        ('${ids.mutedStory}','${ids.muted}',true,now()-interval '1 hour',
          'https://example.invalid/two.jpg','https://example.invalid/two.jpg','muted'),
        ('${ids.expired}','${ids.author}',true,now()-interval '26 hours',
          'https://example.invalid/old.jpg','https://example.invalid/old.jpg','expired');
      insert into public.blocks(blocker_id,blocked_id) values
        ('${ids.author}','${ids.blocked}');
      insert into public.muted_users(user_id,muted_id) values
        ('${ids.viewer}','${ids.muted}');
    `);

    await db.exec(migration);
    await db.exec(migration);
    console.log('PASS consent migration SQL and idempotent replay');

    await asUser(ids.viewer);
    const visibleViewer = (await q('select id::text from public.stories order by id'))
      .rows.map(x => x.id);
    assert.deepEqual(visibleViewer,[ids.original],
      'muted and expired stories must not appear');
    await assert.rejects(q(`select public.repost_blink_story('${ids.original}')`),
      /STORY_REPOST_NOT_ALLOWED/,
      'Default opt-out must be enforced by the server');
    await assert.rejects(q(`
      insert into public.stories(user_id,reposted_from_id)
      values ('${ids.viewer}','${ids.original}')`),
      /row-level security/,
      'Authenticated users must not fabricate direct reposts');
    await assert.rejects(q(`
      insert into public.story_views(story_id,user_id)
      values ('${ids.expired}','${ids.viewer}')`),
      /row-level security/,
      'Expired stories must reject even directly posted view records');
    await assert.rejects(q(`
      insert into public.story_reactions(story_id,user_id)
      values ('${ids.mutedStory}','${ids.viewer}')`),
      /row-level security/,
      'Muted stories must reject direct reactions');
    console.log('PASS default denied repost, mute and expiry protections');

    await asUser(ids.blocked);
    assert.deepEqual((await q(`select id::text from public.stories where id='${ids.original}'`)).rows, [],
      'author-blocked viewer cannot read the blocked author story even when blocks SELECT RLS hides the block row');
    await assert.rejects(q(`
      insert into public.story_likes(story_id,user_id)
      values ('${ids.original}','${ids.blocked}')`),
      /row-level security/);
    console.log('PASS bilateral blocked relationship despite blocks RLS');

    await asUser(ids.author);
    assert.equal((await q(`select public.set_my_story_reposts_allowed('${ids.original}',true) as ok`))
      .rows[0].ok, true);
    await assert.rejects(q(`select public.repost_blink_story('${ids.original}')`),
      /STORY_REPOST_NOT_ALLOWED/,
      'Original author cannot repost their own status');
    await asUser(ids.viewer);
    const created = (await q(`select public.repost_blink_story('${ids.original}')::text as id`))
      .rows[0].id;
    const repeated = (await q(`select public.repost_blink_story('${ids.original}')::text as id`))
      .rows[0].id;
    assert.equal(created,repeated,'Repeating taps must reuse the same repost');
    const inherited = (await q(`select created_at,
      reposted_from_id::text as original,
      allow_reposts from public.stories where id='${created}'`)).rows[0];
    assert.equal(inherited.original,ids.original);
    assert.equal(inherited.allow_reposts,false);
    const originalTime = (await q(`select created_at from public.stories where id='${ids.original}'`))
      .rows[0].created_at;
    assert.equal(new Date(inherited.created_at).valueOf(),new Date(originalTime).valueOf(),
      'Reposting must not extend original expiry');
    console.log('PASS creator consent, one repost per viewer, inherited expiry');

    await asUser(ids.blocked);
    assert.deepEqual((await q(`select id::text from public.stories
      where id='${created}'`)).rows,[],
      'Blocking the original creator also hides approved copies');

    await asUser(ids.author);
    assert.equal((await q(`select public.set_my_story_reposts_allowed('${ids.original}',false) as ok`))
      .rows[0].ok,true);
    await db.exec('reset role;');
    assert.equal((await q(`select count(*)::int as n from public.stories
      where reposted_from_id='${ids.original}'`)).rows[0].n,0,
      'Revoking consent must remove derivative reposts');
    console.log('PASS revoke consent and derivative cleanup');
  } finally {
    await db.close();
  }
}
