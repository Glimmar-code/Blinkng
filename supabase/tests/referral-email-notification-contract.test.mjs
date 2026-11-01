import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const migration = read("../migrations/20261101160000_blink_referral_email_notifications.sql");
const worker = read("../functions/send-essential-email/index.ts");
const referral = read("../migrations/20261024203000_blink_refer_and_grow.sql");

test("referral confirmation email is queued only from a persisted confirmed-referral notification", () => {
  assert.match(migration, /after insert on public\.notifications/i);
  assert.match(migration, /new\.metadata->>'kind'='blink_referral_confirmed'/i);
  assert.match(migration, /r\.status='confirmed'/i);
  assert.match(migration, /r\.referred_user_id=new\.actor_id/i);
  assert.match(migration, /r\.referrer_user_id=new\.user_id/i);
});

test("email queue is deduplicated and requires a verified opted-in referrer", () => {
  assert.match(migration, /u\.email_confirmed_at is not null/i);
  assert.match(migration, /coalesce\(s\.email_notifs_enabled,false\) is true/i);
  assert.match(migration, /on conflict\(user_id,kind,event_key\) do nothing/i);
  assert.match(migration, /referral_confirmed:/);
  assert.match(migration, /referral_milestone:/);
  assert.match(migration, /confirmed_referrals in \(1,5,10,25,50,100\)/i);
});

test("worker revalidates the referral relationship before sending", () => {
  assert.match(worker, /"referral_confirmed"/);
  assert.match(worker, /"referral_milestone"/);
  assert.match(worker, /from\("blink_referrals"\)/);
  assert.match(worker, /referral\.status !== "confirmed"/);
  assert.match(worker, /referral\.referrer_user_id !== row\.user_id/);
  assert.match(worker, /userSettings\?\.email_notifs_enabled !== true/);
  assert.match(worker, /referral_milestone_not_verified/);
});

test("referral emails do not create or award coins and do not change Auth confirmation", () => {
  assert.doesNotMatch(migration, /insert into public\.(blink_wallet|blink_coin_transactions)/i);
  assert.doesNotMatch(migration, /confirm.*sign.?up|enable.*signup.*confirmation/i);
  assert.match(referral, /status text not null default 'pending'/i);
});
