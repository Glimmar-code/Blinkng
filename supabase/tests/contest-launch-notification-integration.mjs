import assert from "node:assert/strict";
import fs from "node:fs";
import { PGlite } from "@electric-sql/pglite";

const migration = fs.readFileSync(
  new URL("../migrations/20261101143000_blink_contest_launch_notifications.sql", import.meta.url),
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
    create schema net;
    create type public.notification_type_enum as enum ('system');
    create table auth.users (
      id uuid primary key, email text, email_confirmed_at timestamptz,
      deleted_at timestamptz, banned_until timestamptz
    );
    create table public.profiles (id uuid primary key);
    create table public.user_settings (
      user_id uuid primary key, email_notifs_enabled boolean default true,
      push_notifs_enabled boolean default true
    );
    create table public.blink_contests (
      id uuid primary key, title text not null, status text not null
    );
    create table public.notifications (
      id uuid primary key default gen_random_uuid(),
      user_id uuid not null references public.profiles(id) on delete cascade,
      actor_id uuid references public.profiles(id) on delete cascade,
      type public.notification_type_enum not null,
      target_type text, target_id uuid, text text, sub_text text,
      is_read boolean not null default false, metadata jsonb not null default '{}'::jsonb,
      created_at timestamptz not null default now()
    );
    create table public.notification_push_dispatches (
      notification_id uuid primary key, status text not null,
      attempts integer not null default 0, updated_at timestamptz not null default now()
    );
    create table private.blink_channel_worker_credentials (
      singleton boolean primary key, worker_token text not null
    );
    insert into private.blink_channel_worker_credentials(singleton,worker_token)
      values (true, repeat('a',64));
    create function public.blink_claim_channel_push_notifications(p_limit integer default 30)
      returns table(notification_id uuid) language sql stable
      as $$ select null::uuid where false $$;
    create function private.blink_channel_emit_due(p_limit integer)
      returns integer language sql as $$ select 0 $$;
    create function net.http_post(url text, headers jsonb, body jsonb, timeout_milliseconds integer)
      returns bigint language sql as $$ select 1::bigint $$;
  `);

  const users = [
    ["00000000-0000-4000-8000-000000000001", "one@example.com", true, false, false, null],
    ["00000000-0000-4000-8000-000000000002", "two@example.com", true, false, false, false],
    ["00000000-0000-4000-8000-000000000003", "banned@example.com", true, false, true, null],
    ["00000000-0000-4000-8000-000000000004", "deleted@example.com", true, true, false, null],
    ["00000000-0000-4000-8000-000000000005", "unverified@example.com", false, false, false, null],
    ["00000000-0000-4000-8000-000000000006", "no-profile@example.com", true, false, false, null],
  ];
  for (const [id,email,verified,deleted,banned,emailEnabled] of users) {
    await db.query(
      `insert into auth.users(id,email,email_confirmed_at,deleted_at,banned_until)
       values ($1,$2,$3,$4,$5)`,
      [id,email,verified ? new Date().toISOString() : null,
       deleted ? new Date().toISOString() : null,
       banned ? new Date(Date.now()+86400000).toISOString() : null],
    );
    if (id !== users[5][0]) await db.query("insert into public.profiles(id) values ($1)", [id]);
    if (emailEnabled !== null) {
      await db.query(
        "insert into public.user_settings(user_id,email_notifs_enabled) values ($1,$2)",
        [id,emailEnabled],
      );
    }
  }

  await db.exec(migration);
  const contestId = "10000000-0000-4000-8000-000000000001";
  await db.query(
    "insert into public.blink_contests(id,title,status) values ($1,$2,'draft')",
    [contestId,"FUTA Talent Showcase"],
  );
  await db.query("update public.blink_contests set status='published' where id=$1",[contestId]);

  const notices = await db.query(
    "select user_id,text,sub_text,metadata from public.notifications where target_id=$1 order by user_id",
    [contestId],
  );
  assert.equal(notices.rows.length, 3, "all active profiles get an in-app launch alert");
  assert.ok(notices.rows.every(row => row.text.includes("FUTA Talent Showcase")));
  assert.ok(notices.rows.every(row => row.sub_text.includes("showcase your talent")));
  assert.ok(notices.rows.every(row => row.metadata.kind === "blink_contest_launch"));

  const emails = await db.query(
    "select user_id,kind,event_key from public.blink_essential_email_outbox where kind='contest_launch'",
  );
  assert.equal(emails.rows.length, 1, "only verified, opted-in active profile gets email");
  assert.equal(emails.rows[0].user_id, users[0][0]);
  assert.equal(emails.rows[0].event_key, `contest_launch:${contestId}`);

  // Repeated publication and pause/resume must not re-broadcast.
  await db.query("update public.blink_contests set status='published' where id=$1",[contestId]);
  await db.query("update public.blink_contests set status='paused' where id=$1",[contestId]);
  await db.query("update public.blink_contests set status='published' where id=$1",[contestId]);
  const afterResume = await db.query(
    "select count(*)::int as count from public.notifications where target_id=$1",
    [contestId],
  );
  assert.equal(afterResume.rows[0].count, 3);
  const afterResumeEmail = await db.query(
    "select count(*)::int as count from public.blink_essential_email_outbox where kind='contest_launch'",
  );
  assert.equal(afterResumeEmail.rows[0].count, 1);

  // The minute scheduler continues to run for email-only backlog after push rows settle.
  const notificationIds = notices.rows.length;
  await db.query(
    `insert into public.notification_push_dispatches(notification_id,status,attempts)
     select id,'sent',1 from public.notifications where target_id=$1`,
    [contestId],
  );
  const dispatch = await db.query("select private.blink_channel_dispatch_due() as invoked");
  assert.equal(dispatch.rows[0].invoked, true);

  // A stale sending claim also wakes the scheduler so the email worker can recover it.
  await db.exec("update public.blink_essential_email_outbox set status='sending',updated_at=now()-interval '11 minutes'");
  const staleDispatch = await db.query("select private.blink_channel_dispatch_due() as invoked");
  assert.equal(staleDispatch.rows[0].invoked, true);

  console.log("PASS Contest launch in-app/email fan-out, audience filters, idempotency and email-only scheduler recovery");
} finally {
  await db.close();
}
