import assert from 'node:assert/strict';
import fs from 'node:fs';
import { PGlite } from '@electric-sql/pglite';

/**
 * Standalone transaction contract regression: use a disposable Postgres
 * instance, never the live BLINK project.
 */
export async function marketplaceOrderTests() {
  const db = new PGlite();
  const seller = '00000000-0000-4000-8000-000000010001';
  const buyer = '00000000-0000-4000-8000-000000010002';
  const anotherBuyer = '00000000-0000-4000-8000-000000010003';
  const itemId = '00000000-0000-4000-8000-000000010004';
  const sql = s => db.query(s);
  const scalar = async s => Object.values((await sql(s)).rows[0])[0];
  const actAs = async id => db.exec(`reset role; select set_config('request.jwt.claim.sub','${id}',false); set role authenticated;`);
  const migration = fs.readFileSync(
    new URL('../migrations/20261007005000_market_professional_hardening.sql', import.meta.url), 'utf8');
  const functionBody = name => {
    const start = migration.lastIndexOf(`CREATE OR REPLACE FUNCTION public.${name}(`);
    assert.ok(start >= 0, `Missing ${name} in marketplace migration`);
    const end = migration.indexOf('$function$;', start);
    assert.ok(end > start, `Unterminated ${name} in marketplace migration`);
    return migration.slice(start, end + '$function$;'.length);
  };
  try {
    await db.exec(`
      create role authenticated;
      create role anon;
      create schema auth;
      create function auth.uid() returns uuid language sql stable
      as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
      grant usage on schema public,auth to authenticated;
      grant execute on function auth.uid() to authenticated;
      create table public.profiles(id uuid primary key);
      create table public.blocks(blocker_id uuid not null, blocked_id uuid not null);
      create table public.market_items(
        id uuid primary key, seller_id uuid references public.profiles(id),
        price bigint not null, quantity integer not null, status text not null default 'active',
        is_sold boolean not null default false, currency text not null default 'NGN',
        updated_at timestamptz not null default now()
      );
      create table public.marketplace_orders(
        id uuid primary key default gen_random_uuid(), item_id uuid not null,
        buyer_id uuid not null, seller_id uuid not null,
        quantity integer not null, unit_price numeric not null, total_price numeric not null,
        currency text not null default 'NGN', status text not null default 'pending',
        created_at timestamptz not null default now(),
        updated_at timestamptz not null default now(),
        constraint marketplace_orders_status_check check(status in
          ('pending','paid','shipped','delivered','cancelled','refunded'))
      );
      insert into public.profiles(id) values ('${seller}'),('${buyer}'),('${anotherBuyer}');
      insert into public.market_items(id,seller_id,price,quantity)
      values ('${itemId}','${seller}',200,3);
    `);
    const statusPatchStart = migration.indexOf('ALTER TABLE public.marketplace_orders\\n  DROP CONSTRAINT');
    const statusPatchEnd = migration.indexOf('-- Prevent a missing seller contact', statusPatchStart);
    assert.ok(statusPatchStart >= 0 && statusPatchEnd > statusPatchStart, 'Missing legacy status compatibility patch');
    await db.exec(migration.slice(statusPatchStart, statusPatchEnd));
    await db.exec(functionBody('create_marketplace_order'));
    await db.exec(functionBody('update_marketplace_order_status'));
    await db.exec(`
      revoke all on function public.create_marketplace_order(uuid,integer) from public,anon;
      grant execute on function public.create_marketplace_order(uuid,integer) to authenticated;
      revoke all on function public.update_marketplace_order_status(uuid,text) from public,anon;
      grant execute on function public.update_marketplace_order_status(uuid,text) to authenticated;
    `);

    await actAs(seller);
    await assert.rejects(
      sql(`select public.create_marketplace_order('${itemId}',1)`), /INVALID_SELLER/);
    await actAs(buyer);
    const first = await scalar(`select public.create_marketplace_order('${itemId}',2)`);
    assert.equal(await scalar(`select public.create_marketplace_order('${itemId}',2)`), first,
      'A repeated request must return the original pending order ID');
    assert.equal(await scalar('select count(*)::int from public.marketplace_orders'), 1);
    await actAs(anotherBuyer);
    const second = await scalar(`select public.create_marketplace_order('${itemId}',2)`);

    await assert.rejects(sql(`select public.update_marketplace_order_status('${first}','accepted')`),
      /NOT_AUTHORIZED/);
    await actAs(seller);
    assert.equal(await scalar(`select public.update_marketplace_order_status('${first}','accepted')`), true);
    await assert.rejects(
      sql(`select public.update_marketplace_order_status('${second}','accepted')`),
      /INSUFFICIENT_QUANTITY/);
    assert.equal(await scalar(`select quantity from public.market_items where id='${itemId}'`), 3);
    assert.equal(await scalar(`select public.update_marketplace_order_status('${first}','completed')`), true);
    assert.equal(await scalar(`select quantity from public.market_items where id='${itemId}'`), 1);
    await assert.rejects(
      sql(`select public.update_marketplace_order_status('${first}','completed')`),
      /INVALID_SELLER_TRANSITION/);
    await assert.rejects(
      sql(`select public.update_marketplace_order_status('${second}','accepted')`),
      /INSUFFICIENT_QUANTITY/);
    await actAs(anotherBuyer);
    await assert.rejects(sql(`select public.create_marketplace_order('${itemId}',20)`),
      /INSUFFICIENT_QUANTITY/);
    assert.equal(await scalar(`select status from public.marketplace_orders where id='${first}'`),'completed');
    console.log('PASS market orders: legacy status compatibility, idempotent request, stock reservation, role isolation and no oversell');
  } finally {
    await db.close();
  }
}
