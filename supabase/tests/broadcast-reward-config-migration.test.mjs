import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const migration = fs.readFileSync(
  new URL("../migrations/20261124103000_broadcast_reward_config_guard.sql", import.meta.url),
  "utf8",
).toLowerCase();

test("reward configuration guard validates campaign tiers and coin-only promos", () => {
  assert.match(migration, /create or replace function public\.broadcast_reward_config_guard/);
  assert.match(migration, /jsonb_array_elements\(new\.tiers\)/);
  assert.match(migration, /unsupported_campaign_reward/);
  assert.match(migration, /new\.reward_type <> 'coins'/);
  assert.match(migration, /coalesce\(new\.min_purchase, 0\) > 0/);
  assert.match(migration, /unsupported_promo_reward/);
  assert.match(migration, /before insert or update of tiers on public\.campaign_rules/);
  assert.match(migration, /before insert or update of reward_type, min_purchase, reward_amount, per_user_limit on public\.promo_rules/);
});
