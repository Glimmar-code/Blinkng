import assert from 'node:assert/strict';
import fs from 'node:fs';

/**
 * Behavioral regression for the BLINK For You truncation:
 * all remaining posts have the same creator after two impressions.
 * The real migration updates the installed function in place, so this
 * controlled PL/pgSQL fixture exercises its guarded code replacement.
 */
export async function discoveryExhaustionTests(db) {
  const migration = fs.readFileSync(
    new URL('../migrations/20261011103000_blink_discovery_v3_creator_backfill.sql', import.meta.url),
    'utf8'
  );
  await db.exec(`
    create or replace function private_ranking.get_discovery_feed(
      p_limit integer default 30, p_offset integer default 0,
      p_as_of timestamptz default now(), p_gravity numeric default 1.5,
      p_w1 numeric default 2, p_w2 numeric default 1,
      p_w3 numeric default 250, p_w4 numeric default 4
    ) returns table(post_id uuid) language plpgsql set search_path to '' as $func$
    declare
      v_position integer;
      v_pick record;
      v_last_creator_1 uuid;
      v_last_creator_2 uuid;
    begin
      create temp table if not exists discovery_candidates (
        post_id uuid primary key, creator_id uuid not null,
        feed_score numeric not null, created_at timestamptz not null
      ) on commit preserve rows;
      create temp table if not exists discovery_selected (
        position integer, post_id uuid primary key
      ) on commit preserve rows;
      truncate pg_temp.discovery_candidates, pg_temp.discovery_selected;
      insert into pg_temp.discovery_candidates(post_id,creator_id,feed_score,created_at)
      select
        ('00000000-0000-4000-8000-' || lpad(i::text,12,'0'))::uuid,
        '00000000-0000-4000-8000-000000009999'::uuid,
        10-i, now()
      from generate_series(1,5) i;
      for v_position in 1..p_limit loop
        select c.* into v_pick from pg_temp.discovery_candidates c
        where not exists (
          select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id
        )
          and not (v_last_creator_1 is not null
            and v_last_creator_2=v_last_creator_1
            and c.creator_id=v_last_creator_1)
        order by c.feed_score desc,c.created_at desc,c.post_id desc limit 1;
        if not found then exit; end if;
        insert into pg_temp.discovery_selected(position,post_id)
        values(v_position,v_pick.post_id);
        v_last_creator_2 := v_last_creator_1;
        v_last_creator_1 := v_pick.creator_id;
      end loop;
      return query select s.post_id from pg_temp.discovery_selected s order by s.position;
    end;
    $func$;
  `);
  const count = async () => (await db.query(
    'select count(*)::integer as n from private_ranking.get_discovery_feed(30)'
  )).rows[0].n;

  assert.equal(await count(), 2, 'old diversity rule should truncate at two');
  await db.exec(migration);
  assert.equal(await count(), 5, 'fallback must reveal all eligible posts');
  await db.exec(migration);
  assert.equal(await count(), 5, 'migration must be idempotent');
  console.log('PASS discovery: repeated creator no longer exhausts For You');
}
