import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const migration = fs.readFileSync(
  new URL("../migrations/20261124100000_broadcast_admin_update_patch_fields.sql", import.meta.url),
  "utf8",
).toLowerCase();

test("admin Broadcast update remains authorized and version-checked", () => {
  assert.match(migration, /security definer/);
  assert.match(migration, /set search_path to 'public'/);
  assert.match(migration, /private\.require_blink_admin\(\)/);
  assert.match(migration, /oldrow\.version <> p_version/);
  assert.match(migration, /version = version \+ 1/);
});

test("admin Broadcast update covers editable blocking, audience, channel and version fields", () => {
  for (const field of [
    "timezone =", "dismissible =", "is_blocking =", "block_kind =",
    "min_app_version =", "store_url =", "audience =", "channels =",
  ]) assert.ok(migration.includes(field), `missing patch field: ${field}`);
});
