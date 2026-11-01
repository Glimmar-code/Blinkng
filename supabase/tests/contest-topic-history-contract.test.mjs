import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const sql = await readFile(new URL("../migrations/20261101180000_blink_contest_topics_history.sql", import.meta.url), "utf8");

test("Contest directory seeds the complete 100-topic library", () => {
  const seedBlock = sql.split("insert into public.blink_contest_topics")[1]?.split("on conflict(slug)")[0] ?? "";
  const slugs = [...seedBlock.matchAll(/^\('([a-z0-9-]+)'/gm)];
  assert.equal(slugs.length, 100);
  assert.match(sql, /unique/);
});

test("topic and contest scope are stored server-side", () => {
  assert.match(sql, /add column if not exists topic_id uuid references public\.blink_contest_topics/);
  assert.match(sql, /competition_scope in \('campus','nationwide'\)/);
  assert.match(sql, /CAMPUS_COMPETITION_REQUIRES_UNIVERSITY/);
  assert.match(sql, /CONTEST_TOPIC_REQUIRED/);
});

test("topic detail exposes permanent participant, vote and prize history", () => {
  assert.match(sql, /create table if not exists public\.blink_contest_history_participants/);
  assert.match(sql, /create or replace function private\.blink_snapshot_contest_history/);
  assert.match(sql, /create or replace function public\.get_blink_contest_topic/);
  assert.match(sql, /'participants',v_people/);
  assert.match(sql, /'prizes_awarded'/);
  assert.match(sql, /on conflict\(contest_id,entry_id\) do nothing/);
});

test("topic follow and admin topic management are authenticated RPCs", () => {
  assert.match(sql, /create or replace function public\.follow_blink_contest_topic/);
  assert.match(sql, /create or replace function public\.unfollow_blink_contest_topic/);
  assert.match(sql, /create or replace function public\.admin_save_blink_contest_topic/);
  assert.match(sql, /create or replace function private\.blink_notify_followed_contest_topic/);
  assert.match(sql, /grant execute on function public\\.list_blink_contest_topics\\(\\)[\\s\\S]*public\\.get_blink_contest_topic/);
});

test("contest voting remains free and no paid-vote path is introduced", () => {
  assert.doesNotMatch(sql, /paid_vote|purchase_vote|vote_multiplier/i);
  assert.match(sql, /grant execute on function public\\.list_blink_contest_topics\\(\\)[\\s\\S]*public\\.get_blink_contest_topic/);
});
