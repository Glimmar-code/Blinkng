import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const migration = fs.readFileSync(
  new URL('../migrations/20261107120000_fix_admin_backend_schema_access_and_vip_notifications.sql', import.meta.url),
  'utf8'
);
const normalized = migration.toLowerCase();

const wrappers = [
  'admin_dashboard_stats()',
  'admin_execute_feature(integer,uuid,text,bigint,integer,jsonb)',
  'admin_execute_feature_v2(integer,text,text,bigint,integer,jsonb)',
  'admin_get_universities()',
  'admin_global_search_v2(text)',
  'admin_grant_coins(uuid,bigint,text)',
  'admin_grant_role(uuid,integer)',
  'admin_history_v2(integer,integer)',
  'admin_list_features()',
  'admin_list_features_v2()',
  'admin_post_action(uuid,text,numeric)',
  'admin_revert_action_v2(uuid,text)',
  'admin_revoke_role(uuid)',
  'admin_search_posts_v2(text,integer)',
  'admin_search_universities_v2(text,integer)',
  'admin_search_users(text,integer)',
  'admin_search_users_v2(text,integer)',
  'admin_send_announcement(text,uuid,text,text)',
  'admin_set_verification(uuid,text,integer)'
];

test('all private-backed admin wrappers are elevated narrowly', () => {
  for (const signature of wrappers) {
    assert.ok(
      normalized.includes(`alter function public.${signature} security definer;`),
      `missing SECURITY DEFINER repair for ${signature}`
    );
  }
  assert.ok(normalized.includes("set search_path = ''"));
  assert.ok(normalized.includes('revoke all on function %s from public, anon'));
  assert.ok(normalized.includes('grant execute on function %s to authenticated, service_role'));
});

test('private schema remains closed and implementation-level authorization is retained', () => {
  assert.ok(normalized.includes('never grant clients access to private'));
  assert.ok(!normalized.includes('grant usage on schema private'));
  assert.ok(normalized.includes('private.require_blink_admin()'));
  assert.ok(normalized.includes('private.require_blink_owner()'));
});

test('daily streak helper is scoped to the authenticated user', () => {
  assert.ok(normalized.includes('create or replace function public.touch_daily_streak()'));
  assert.ok(normalized.includes('security definer'));
  assert.ok(normalized.includes('auth.uid()'));
  assert.ok(normalized.includes('revoke all on function public.touch_daily_streak() from public, anon'));
});

test('VIP expiry reminder uses deployed notification columns', () => {
  assert.ok(normalized.includes('notifications(user_id, type, text, sub_text, is_read)'));
  assert.ok(!normalized.includes('notifications(user_id,type,comment)'));
});
