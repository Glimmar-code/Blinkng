import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
const normal = '00000000-0000-4000-8000-000000000001';
const blue = '00000000-0000-4000-8000-000000000002';
const gold = '00000000-0000-4000-8000-000000000003';
const seller = '00000000-0000-4000-8000-000000000004';
const listing = '00000000-0000-4000-8000-000000000010';
const event = n => `00000000-0000-4000-8000-${String(n).padStart(12,'0')}`;

try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create schema private;
    create function auth.uid() returns uuid language sql stable
      as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    create table public.profiles(
      id uuid primary key,
      is_verified boolean not null default false,
      verification_badge text,
      verification_tier text,
      verification_expires_at timestamptz
    );
    insert into public.profiles(id,is_verified,verification_badge,verification_tier)
    values
      ('00000000-0000-4000-8000-000000000001',false,'NONE','None'),
      ('00000000-0000-4000-8000-000000000002',true,'BLUE','Standard'),
      ('00000000-0000-4000-8000-000000000003',true,'GOLD','Gold'),
      ('00000000-0000-4000-8000-000000000004',true,'BLUE','Standard');
    create table public.market_items(
      id uuid primary key,
      seller_id uuid not null references public.profiles(id),
      status text default 'active',
      is_sold boolean default false,
      views_count bigint default 0,
      updated_at timestamptz default now()
    );
    insert into public.market_items(id,seller_id) values('00000000-0000-4000-8000-000000000010','00000000-0000-4000-8000-000000000004');
    create table public.marketplace_views(
      user_id uuid not null references public.profiles(id),
      item_id uuid not null references public.market_items(id),
      viewed_on date not null default current_date,
      created_at timestamptz not null default now(),
      primary key(user_id,item_id,viewed_on)
    );
  `);

  const migration = fs.readFileSync(
    new URL('../migrations/20261031120000_marketplace_repeat_weighted_view_events.sql',import.meta.url),
    'utf8'
  );
  await db.exec(migration);
  const setViewer = async id => db.query(
    `select set_config('request.jwt.claim.sub',$1,false)`,[id]
  );
  const count = async () => (await db.query(
    `select views_count from public.market_items where id=$1`,[listing]
  )).rows[0].views_count;
  const record = async id => {
    const result = await db.query(
      `select public.record_marketplace_view($1::uuid,$2::uuid) as count`,
      [listing,id]
    );
    return Number(result.rows[0].count);
  };
  const ageLastEvent = async seconds => db.query(
    `update private.marketplace_view_events
       set created_at=now()-($1::text||' seconds')::interval
     where viewer_id=auth.uid() and item_id=$2`,[seconds,listing]
  );

  await setViewer(normal);
  assert.equal(await record(event(1)),1,'normal exposure adds 1');
  assert.equal(await record(event(1)),1,'same event ID is idempotent');
  assert.equal(await record(event(2)),1,'rapid duplicate visit is rate-limited');
  await ageLastEvent(3);
  assert.equal(await record(event(3)),2,'a later genuine visit counts again');

  await setViewer(blue);
  assert.equal(await record(event(4)),5,'a separate viewer contributes their own weighted view');
  await ageLastEvent(3);
  assert.equal(await record(event(5)),8,'active Blue repeat adds 3');

  await setViewer(gold);
  await ageLastEvent(3);
  assert.equal(await record(event(6)),14,'active Gold adds 6');

  await db.query(`update public.profiles set verification_expires_at='2020-01-01' where id=$1`,[gold]);
  await ageLastEvent(3);
  assert.equal(await record(event(7)),15,'expired Gold falls back to 1');

  await setViewer(seller);
  assert.equal(await record(event(8)),15,'seller self-views do not inflate listing metrics');

  const dailyUnique = (await db.query(
    `select count(*)::integer as n from public.marketplace_views where item_id=$1`,[listing]
  )).rows[0].n;
  assert.equal(dailyUnique,3,'legacy daily unique visitor analytics remain deduplicated');

  const grants = (await db.query(`
    select has_function_privilege('authenticated','public.record_marketplace_view(uuid,uuid)','EXECUTE') as auth_ok,
           has_function_privilege('anon','public.record_marketplace_view(uuid,uuid)','EXECUTE') as anon_ok,
           has_table_privilege('authenticated','private.marketplace_view_events','SELECT') as private_read
  `)).rows[0];
  assert.equal(grants.auth_ok,true);
  assert.equal(grants.anon_ok,false);
  assert.equal(grants.private_read,false);

  console.log('PASS Marketplace views: repeat events, idempotency, rate limit, 1/3/6 weights, expiry, owner exclusion, private grants');
} finally {
  await db.close();
}
