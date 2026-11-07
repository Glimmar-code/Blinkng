import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const migration = fs.readFileSync(
  new URL('../migrations/20261107120000_fix_admin_backend_schema_access_and_vip_notifications.sql', import.meta.url),
  'utf8'
);

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
    assert.match(
      migration,
      new RegExp('alter\\s+function\\s+public\\.' + signature.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&') + '\\s+security\\s+definer', 'i'),
      `missing SECURITY DEFINER repair for ${signature}`
    );
  }
  assert.match(migration, /set search_path = ''/i);
  assert.match(migration, /revoke all on function %s from public, anon/i);
  assert.match(migration, /grant execute on function %s to authenticated, service_role/i);
});

test('private schema remains closed; wrappers use internal authorization checks', () => {
  assert.match(migration, /never grant clients access to private/i);
  assert.doesNotMatch(migration, /grant\\s+usage\\s+on\\s+schema\\s+private/i);
  assert.match(migration, /require_blink_admin\\(\\)/i);
  assert.match(migration, /require_blink_owner\\(\\)/i);
});

test('daily streak helper is a narrowly authenticated security definer', () => {
  assert.match(migration, /create or replace function public\\.touch_daily_streak\\(\\)[\\s\\S]*?security definer[\\s\\S]*?auth\\.uid\\(\\)/i);
  assert.match(migration, /revoke all on function public\\.touch_daily_streak\\(\\) from public, anon/i);
});

test('VIP expiry reminder uses deployed notification columns', () => {
  assert.match(migration, /notifications\\(user_id, type, text, sub_text, is_read\\)/i);
  assert.doesNotMatch(migration, /notifications\\s*\\(\\s*user_id\\s*,\\s*type\\s*,\\s*comment\\s*\\)/i);
});
