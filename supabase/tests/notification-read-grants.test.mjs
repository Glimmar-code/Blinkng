import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const sql = readFileSync(new URL("../migrations/20261027195000_notification_read_state_atomic.sql", import.meta.url), "utf8").toLowerCase();

test("read-state grant is least-privilege and restricted to authenticated accounts", () => {
  assert.match(sql, /revoke update on table public\.notifications from anon, authenticated/);
  assert.match(sql, /grant update \(is_read\) on table public\.notifications to authenticated/);
  assert.doesNotMatch(sql, /grant update on table public\.notifications to authenticated/);
  assert.match(sql, /for update to authenticated/);
  assert.match(sql, /using \(user_id = \(select auth\.uid\(\)\)\)/);
  assert.match(sql, /with check \(user_id = \(select auth\.uid\(\)\)\)/);
});

test("read timestamp is maintained server-side without granting clients column access", () => {
  assert.match(sql, /create or replace function public\.set_notification_read_timestamp\(\)/);
  assert.match(sql, /before update of is_read on public\.notifications/);
  assert.match(sql, /new\.read_at := case when new\.is_read then now\(\) else null end/);
  assert.doesNotMatch(sql, /grant update \(read_at\)/);
});

test("mark all is one RLS-enforced transaction", () => {
  assert.match(sql, /create or replace function public\.mark_all_my_notifications_read_atomic\(\)/);
  assert.match(sql, /security invoker/);
  assert.match(sql, /if v_uid is null then raise exception 'auth_required'/);
  assert.match(sql, /update public\.activities[\s\S]*where recipient_id = v_uid/);
  assert.match(sql, /update public\.notifications[\s\S]*where user_id = v_uid/);
  assert.match(sql, /revoke all on function public\.mark_all_my_notifications_read_atomic\(\) from public, anon/);
  assert.match(sql, /grant execute on function public\.mark_all_my_notifications_read_atomic\(\) to authenticated/);
});
