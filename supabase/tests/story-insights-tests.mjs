import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

export async function storyInsightsTests() {
  const db = new PGlite();
  const owner = '00000000-0000-4000-8000-000000000501';
  const viewer = '00000000-0000-4000-8000-000000000502';
  const blocked = '00000000-0000-4000-8000-000000000503';
  const outsider = '00000000-0000-4000-8000-000000000504';
  const recentStory = '00000000-0000-4000-8000-000000000505';
  const expiredStory = '00000000-0000-4000-8000-000000000506';
  const migration = fs.readFileSync(
    new URL('../migrations/20261022044000_story_owner_insights.sql', import.meta.url), 'utf8');

  const asRole = role => db.exec(`reset role; set role ${role};`);
  const actAs = async id => {
    await db.exec(`reset role; select set_config('request.jwt.claim.sub','${id}',false);
      set role authenticated;`);
  };
  const insights = id => db.query(
    `select public.get_my_story_insights('${id}') as data`
  );

  try {
    await db.exec(`
      create role anon;
      create role authenticated;
      create schema auth;
      create function auth.uid() returns uuid language sql stable as
        $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema public,auth to authenticated,anon;
      grant execute on function auth.uid() to authenticated;
      create table public.stories(id uuid primary key,user_id uuid not null,
        active boolean default true,created_at timestamptz default now());
      create table public.profiles(id uuid primary key,username text not null,
        full_name text,avatar_url text);
      create table public.blocks(blocker_id uuid,blocked_id uuid);
      create table public.story_views(story_id uuid,user_id uuid,created_at timestamptz default now());
      create table public.story_likes(story_id uuid,user_id uuid,created_at timestamptz default now());
      alter table public.stories enable row level security;
      create policy owner_stories on public.stories for select to authenticated
        using (user_id = auth.uid());
      grant select on public.stories to authenticated;
      insert into public.profiles(id,username,full_name) values
        ('${owner}','owner','Owner'),
        ('${viewer}','viewer','Viewer'),
        ('${blocked}','blocked','Blocked'),
        ('${outsider}','outsider','Outsider');
      insert into public.stories(id,user_id,active,created_at) values
        ('${recentStory}','${owner}',true,now()-interval '1 hour'),
        ('${expiredStory}','${owner}',true,now()-interval '27 hours');
      insert into public.story_views(story_id,user_id) values
        ('${recentStory}','${viewer}'),('${recentStory}','${blocked}');
      insert into public.story_likes(story_id,user_id) values
        ('${recentStory}','${viewer}'),('${recentStory}','${blocked}');
      insert into public.blocks(blocker_id,blocked_id) values
        ('${blocked}','${owner}');
    `);
    await db.exec(migration);
    await db.exec(migration);
    await actAs(owner);
    const ownerResult = (await insights(recentStory)).rows[0].data;
    assert.deepEqual(ownerResult.viewers.map(row=>row.username), ['viewer']);
    assert.deepEqual(ownerResult.likers.map(row=>row.username), ['viewer']);
    await assert.rejects(insights(expiredStory), /STORY_INSIGHTS_UNAVAILABLE/);

    await actAs(viewer);
    await assert.rejects(insights(recentStory), /STORY_INSIGHTS_UNAVAILABLE/);
    await actAs(outsider);
    await assert.rejects(insights(recentStory), /STORY_INSIGHTS_UNAVAILABLE/);

    await db.exec('reset role;');
    await db.exec('select set_config(\'request.jwt.claim.sub\',\'\',false);');
    await asRole('anon');
    await assert.rejects(insights(recentStory), /permission denied/);
    console.log('PASS story insights owner-only, blocked relationships, expiry and anon restriction');
  } finally {
    await db.close();
  }
}
