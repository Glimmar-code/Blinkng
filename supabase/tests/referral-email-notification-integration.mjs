import assert from "node:assert/strict";
import fs from "node:fs";
import { PGlite } from "@electric-sql/pglite";

const migration = fs.readFileSync(
  new URL("../migrations/20261101160000_blink_referral_email_notifications.sql", import.meta.url),
  "utf8",
);
const db = new PGlite();

try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create schema private;
    create table auth.users (
      id uuid primary key, email text, email_confirmed_at timestamptz,
      deleted_at timestamptz, banned_until timestamptz
    );
    create table public.profiles (id uuid primary key);
    create table public.user_settings (
      user_id uuid primary key, email_notifs_enabled boolean default false
    );
    create table public.blink_referrals (
      referred_user_id uuid primary key,
      referrer_user_id uuid not null,
      status text not null
    );
    create table public.blink_referral_achievements (
      user_id uuid primary key, confirmed_referrals bigint not null
    );
    create table public.notifications (
      id uuid primary key default gen_random_uuid(),
      user_id uuid not null, actor_id uuid, metadata jsonb
    );
    create table public.blink_essential_email_outbox (
      id uuid primary key default gen_random_uuid(),
      user_id uuid not null,
      kind text not null,
      event_key text not null,
      status text not null default 'pending',
      priority smallint not null default 10,
      attempts integer not null default 0,
      available_at timestamptz not null default now(),
      created_at timestamptz not null default now(),
      updated_at timestamptz not null default now(),
      sent_at timestamptz,
      last_error text,
      constraint blink_essential_email_outbox_kind_check
        check (kind in ('welcome','new_device','suspicious_activity','new_follower',
          'unread_messages','post_engagement','reel_milestone','leaderboard','contest_launch')),
      unique(user_id,kind,event_key)
    );
  `);
  await db.exec(migration);

  const referrer = "10000000-0000-4000-8000-000000000001";
  const referred = "20000000-0000-4000-8000-000000000002";
  const optedOut = "30000000-0000-4000-8000-000000000003";
  const referred2 = "40000000-0000-4000-8000-000000000004";

  await db.query("insert into auth.users(id,email,email_confirmed_at) values ($1,'referrer@example.com',now())", [referrer]);
  await db.query("insert into auth.users(id,email,email_confirmed_at) values ($1,'referred@example.com',now())", [referred]);
  await db.query("insert into auth.users(id,email,email_confirmed_at) values ($1,'optout@example.com',now())", [optedOut]);
  await db.query("insert into auth.users(id,email,email_confirmed_at) values ($1,'second-referred@example.com',now())", [referred2]);
  await db.query("insert into public.user_settings(user_id,email_notifs_enabled) values ($1,true),($2,false)", [referrer,optedOut]);
  await db.query("insert into public.blink_referrals(referred_user_id,referrer_user_id,status) values ($1,$2,'confirmed'),($3,$4,'confirmed')", [referred,referrer,referred2,optedOut]);
  await db.query("insert into public.blink_referral_achievements(user_id,confirmed_referrals) values ($1,1),($2,5)", [referrer,optedOut]);

  await db.query(`
    insert into public.notifications(user_id,actor_id,metadata)
    values ($1,$2,'{"kind":"blink_referral_confirmed"}'::jsonb)
  `, [referrer,referred]);

  const first = await db.query(
    "select kind,event_key from public.blink_essential_email_outbox where user_id=$1 order by kind",
    [referrer],
  );
  assert.deepEqual(first.rows.map((x) => x.kind).sort(), ["referral_confirmed","referral_milestone"]);
  assert.deepEqual(first.rows.map((x) => x.event_key).sort(), [
    "referral_confirmed:" + referred,
    "referral_milestone:1",
  ].sort());

  // Duplicate event insertion is harmless: the outbox's unique event keys dedupe it.
  await db.query(`
    insert into public.notifications(user_id,actor_id,metadata)
    values ($1,$2,'{"kind":"blink_referral_confirmed"}'::jsonb)
  `, [referrer,referred]);
  const count = await db.query(
    "select count(*)::int as count from public.blink_essential_email_outbox where user_id=$1",
    [referrer],
  );
  assert.equal(count.rows[0].count, 2);

  // An opted-out referrer gets neither the per-referral nor milestone email.
  await db.query(`
    insert into public.notifications(user_id,actor_id,metadata)
    values ($1,$2,'{"kind":"blink_referral_confirmed"}'::jsonb)
  `, [optedOut,referred2]);
  const optedOutCount = await db.query(
    "select count(*)::int as count from public.blink_essential_email_outbox where user_id=$1",
    [optedOut],
  );
  assert.equal(optedOutCount.rows[0].count, 0);

  console.log("Referral email integration passed: verified referral, milestone, dedupe, and opt-out.");
} finally {
  await db.close();
}
