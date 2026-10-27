import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const migration = readFileSync(
  new URL("../migrations/20261027210000_algorithm_integrity_security_reels.sql", import.meta.url),
  "utf8",
);
const featuredMigration = readFileSync(
  new URL("../migrations/20261027225000_featured_reels_viewer_preferences.sql", import.meta.url),
  "utf8"
);
const ssvWorker = readFileSync(
  new URL("../functions/admob-reward-ssv/index.ts", import.meta.url),
  "utf8",
);

const exposureTracker = readFileSync(
  new URL("../../app/src/main/java/com/example/ui/components/ContentExposureTracker.kt", import.meta.url), "utf8"
);
const reelScreen = readFileSync(
  new URL("../../app/src/main/java/com/example/ui/screens/VideoReelsScreen.kt", import.meta.url), "utf8"
);

test("rewarded coins require signed SSV and only service role may settle", () => {
  assert.match(migration, /v_claim\.ssv_verified_at is null then raise exception ''REWARDED_AD_VERIFICATION_PENDING''/);
  assert.match(migration, /grant execute on function public\.settle_blink_rewarded_ad_verified\(uuid\) to service_role;/i);
  assert.match(migration, /revoke all on function public\.settle_blink_rewarded_ad_verified\(uuid\) from public,anon,authenticated;/i);
  assert.match(migration, /REWARDED_AD_DAILY_LIMIT/);
  assert.match(migration, /least\(v_limit,10\)/);
  const signed = ssvWorker.indexOf("verifyAdMobSignedQuery(rawQuery");
  const persisted = ssvWorker.indexOf("rpc/record_blink_rewarded_ad_ssv");
  const settled = ssvWorker.indexOf("rpc/settle_blink_rewarded_ad_verified");
  assert.ok(signed >= 0 && signed < persisted && persisted < settled,
    "Verification must precede recording, which must precede settlement");
  assert.match(ssvWorker, /settlement_failed/);
});

test("ranked and chronological Feeds respect user privacy and blocks", () => {
  assert.match(migration, /ALGORITHM_DISCOVERY_SOURCE_DRIFT/);
  assert.match(migration, /REEL_COMPLETION_SCORE_SOURCE_DRIFT/);
  assert.match(migration, /v_reel_factor text/);
  assert.match(migration, /reel_watch_samples >= 12/);
  assert.match(migration, /privacy\.private_account is true/);
  assert.match(migration, /v_owner_bonus, '0::numeric'/);
  assert.match(migration, /'private\.is_blink_owner_id\(fp\.user_id\)', 'false'/);
});

test("Reels events use real watch-time derived flags, with duplicate protection", () => {
  assert.match(migration, /create or replace function public\.record_reel_engagement/);
  assert.match(migration, /v_completed := v_watch_ms >= round\(v_duration_ms \* 0\.90\)/);
  assert.match(migration, /v_rewatched := v_watch_ms >= round\(v_duration_ms \* 1\.05\)/);
  assert.doesNotMatch(migration, /v_completed := coalesce\(p_completed,false\) or/);
  assert.match(migration, /on conflict \(session_id\) do nothing/);
  assert.match(migration, /created_at > now\(\)-interval '20 seconds'/);
});

test("Post views reject rapid replays without inflating counters", () => {
  assert.match(migration, /CONTENT_VIEW_VISIBILITY_SOURCE_DRIFT/);
  assert.match(migration, /raise exception 'CONTENT_NOT_VISIBLE'/);
  assert.match(migration, /allowed\.is_active=true/);
  assert.match(migration, /privacy\.private_account=true/);
  assert.match(migration, /ALGORITHM_VIEW_REPLAY_COOLDOWN/);
  assert.match(migration, /recent\.last_viewed_at > now\(\) - interval '60 seconds'/);
  assert.match(migration, /'accepted',false,'duplicate',false,'cap_reached',false/);
});

test("Reel recommendation events use an accepted event type and preserve follower access", () => {
  assert.match(migration, /v_event_type := case when v_skipped then 'skip' else 'dwell'/);
  assert.doesNotMatch(migration, /then 'skip' else 'view'/);
  assert.match(migration, /audience_follow\.follower_id=v_viewer/);
  assert.match(migration, /existing\.created_at > now\(\)-interval '24 hours'/);
  assert.match(migration, />= 24 then/);
});

test("Expired creator badges never confer a verification ranking or view multiplier", () => {
  assert.match(migration, /EXPIRED_VERIFICATION_VIEW_WEIGHT_SOURCE_DRIFT/);
  assert.match(migration, /EXPIRED_VERIFICATION_RANK_SOURCE_DRIFT/);
  assert.match(migration, /p\.verification_expires_at > now\(\)/);
  assert.match(migration, /pr\.verification_expires_at > p_as_of/);
  assert.match(migration, /CONTENT_VIEW_ATOMIC_COOLDOWN_SOURCE_DRIFT/);
});

test("Reel exposure is playback-qualified; paused and buffering previews do not count", () => {
  assert.match(exposureTracker, /playbackActive: Boolean = true/);
  assert.match(exposureTracker, /!playbackActive \|\| wasRecorded/);
  assert.match(reelScreen, /!isPausedByUser && !isBuffering/);
  assert.match(reelScreen, /!previewFinished && !isBuffering/);
});

test("Own content never contributes to view or Featured Reel popularity", () => {
  assert.match(migration, /public\.feed_posts own/);
  assert.match(migration, /own\.user_id=v_viewer_id/);
  assert.match(featuredMigration, /own\.user_id=e\.viewer_id/);
  assert.match(featuredMigration, /FEATURED_REELS_SELF_VIEW_FILTER_SOURCE_DRIFT/);
});
