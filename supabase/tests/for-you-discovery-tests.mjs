import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const migration = fs.readFileSync(
  new URL('../migrations/20261011054000_expand_foryou_discovery_and_creator_fallback.sql', import.meta.url),
  'utf8'
);

/**
 * Disposable PostgreSQL regression for the production function's age predicate
 * and creator-diversity loop. No production data, secrets, or API calls.
 */
export async function forYouDiscoveryTests() {
  const db = new PGlite();
  try {
    await db.exec(`
      create schema private_ranking;
      create table public.feed_posts(
        id uuid primary key,
        user_id uuid not null,
        created_at timestamptz not null,
        is_active boolean not null default true
      );
      -- A minimal reproduction of the existing discovery signature and loop.
      -- The migration patches the same exact clauses in the live implementation.
      create function private_ranking.get_discovery_feed(
        p_limit integer default 40,
        p_offset integer default 0,
        p_as_of timestamptz default now(),
        p_gravity numeric default 1.5,
        p_w1 numeric default 2.0,
        p_w2 numeric default 1.0,
        p_w3 numeric default 250.0,
        p_w4 numeric default 4.0
      )
      returns table(
        item jsonb, feed_score numeric, ranking_components jsonb,
        feed_position integer, as_of timestamptz, next_offset integer
      )
      language plpgsql security definer set search_path to ''
      as $function$
      declare
        v_pick record;
        v_position integer;
        v_last_creator_1 uuid;
        v_last_creator_2 uuid;
      begin
        drop table if exists pg_temp.discovery_candidates;
        drop table if exists pg_temp.discovery_selected;
        create temporary table discovery_candidates(
          post_id uuid, creator_id uuid, feed_score numeric, created_at timestamptz
        );
        create temporary table discovery_selected(position integer,post_id uuid);
        insert into pg_temp.discovery_candidates(post_id,creator_id,feed_score,created_at)
        select fp.id,fp.user_id,
          (100 - extract(epoch from (p_as_of-fp.created_at))/86400)::numeric,
          fp.created_at
        from public.feed_posts fp
        left join lateral (
          select null::timestamptz as created_at
        ) rr on true
        where fp.is_active
          and greatest(fp.created_at, coalesce(rr.created_at, fp.created_at)) > p_as_of - interval '7 days'
          and fp.created_at <= p_as_of;
        for v_position in 1..(p_offset+p_limit) loop
          select c.* into v_pick
          from pg_temp.discovery_candidates c
          where not exists(
            select 1 from pg_temp.discovery_selected s where s.post_id=c.post_id
          )
          and not (
            v_last_creator_1 is not null
            and v_last_creator_2=v_last_creator_1
            and c.creator_id=v_last_creator_1
          )
          order by c.feed_score desc,c.created_at desc,c.post_id desc
          limit 1;
          if not found then exit; end if;
          insert into pg_temp.discovery_selected(position,post_id)
          values(v_position,v_pick.post_id);
          v_last_creator_2 := v_last_creator_1;
          v_last_creator_1 := v_pick.creator_id;
        end loop;
        return query
        select jsonb_build_object('id',fp.id),c.feed_score,
          '{}'::jsonb,s.position,p_as_of,
          (p_offset + count(*) over())::integer
        from pg_temp.discovery_selected s
        join pg_temp.discovery_candidates c on c.post_id=s.post_id
        join public.feed_posts fp on fp.id=s.post_id
        where s.position > p_offset and s.position <= p_offset+p_limit
        order by s.position;
      end;
      $function$;
      insert into public.feed_posts(id,user_id,created_at)
      select
        ('00000000-0000-4000-8000-' || lpad(i::text,12,'0'))::uuid,
        '00000000-0000-4000-8000-999999999999'::uuid,
        '2026-10-11 05:00:00+00'::timestamptz -
        case i when 1 then interval '1 day'
               when 2 then interval '2 days'
               when 3 then interval '3 days'
               when 4 then interval '15 days'
               when 5 then interval '20 days'
               else interval '40 days' end
      from generate_series(1,6) i;
    `);

    const page = (limit, offset) =>
      db.query(`select item->>'id' as id, feed_score, feed_position, next_offset
        from private_ranking.get_discovery_feed(
          ${limit},${offset},'2026-10-11 05:00:00+00',
          1.5,2.0,1.0,250.0,4.0
        )`);

    assert.equal((await page(3, 0)).rows.length, 2,
      'baseline reproduction must stop on third consecutive creator');

    await db.exec(migration);
    const first = (await page(3, 0)).rows;
    const second = (await page(3, 3)).rows;
    assert.equal(first.length, 3, 'first ranked page must be full');
    assert.equal(second.length, 3, 'second ranked page must include older posts');
    const ids = [...first, ...second].map(row => row.id);
    assert.equal(new Set(ids).size, 6, 'pages must not repeat posts');
    assert.equal(first[0].id, '00000000-0000-4000-8000-000000000001',
      'fresher posts remain ahead when engagement and author are equal');
    assert.equal(second[2].id, '00000000-0000-4000-8000-000000000006',
      'posts older than seven days remain eligible');
    assert.equal((await page(3,6)).rows.length,0, 'feed stops only when exhausted');

    // Idempotence and source-guard assertions make accidental duplicate fallback
    // insertion or changes to score calculation a migration failure.
    await db.exec(migration);
    const source = (await db.query(`
      select pg_get_functiondef(
        'private_ranking.get_discovery_feed(integer,integer,timestamptz,numeric,numeric,numeric,numeric,numeric)'::regprocedure
      ) as definition
    `)).rows[0].definition;
    assert.equal((source.match(/BLINK_FOR_YOU_CREATOR_FALLBACK_V1/g) || []).length,1);
    assert.equal((source.match(/BLINK_FOR_YOU_NO_AGE_CUTOFF_V1/g) || []).length,1);
    assert.equal((await page(3,3)).rows.length,3);
    console.log('PASS For You: old content visible, freshness retained, same-creator fallback, distinct pages, idempotence');
  } finally {
    await db.close();
  }
}
