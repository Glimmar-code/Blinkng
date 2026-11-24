import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";

const migration = fs.readFileSync(
  new URL("../migrations/20261124101500_broadcast_prefs_clear_quiet_hours.sql", import.meta.url),
  "utf8",
).toLowerCase();

test("quiet-hours update can explicitly clear values while preserving user auth", () => {
  assert.match(migration, /security definer/);
  assert.match(migration, /set search_path to 'public'/);
  assert.match(migration, /auth\.uid\(\)/);
  assert.match(migration, /p_patch \? 'quiet_start'/);
  assert.match(migration, /p_patch \? 'quiet_end'/);
  assert.match(migration, /then nullif\(p_patch->>'quiet_start', ''\)::time/);
  assert.match(migration, /then nullif\(p_patch->>'quiet_end', ''\)::time/);
});
