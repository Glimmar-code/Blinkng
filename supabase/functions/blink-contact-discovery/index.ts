import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

/**
 * BLINK opted-in contact discovery. User JWT is checked by Supabase Auth on EVERY call.
 * Raw phone numbers live only in memory for the duration of a request; private tables hold
 * keyed HMACs, not numbers, names, device IDs or phone logs.
 */
const allowedOrigins = new Set(["https://blink.com.ng", "https://www.blink.com.ng"]);
const json = (value: unknown, code = 200, origin: string | null = null) => new Response(
  JSON.stringify(value),
  { status: code, headers: {
    "Content-Type": "application/json",
    "Cache-Control": "no-store",
    "Access-Control-Allow-Origin": origin && allowedOrigins.has(origin) ? origin : "https://blink.com.ng",
    "Access-Control-Allow-Headers": "authorization, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
  } },
);

const matchPolicy = "reciprocal_opt_in_v1";
const isUuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
function canonical(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const value = raw.trim().replace(/[\s()\-]/g, "");
  const number = value.startsWith("0") && value.length === 11
    ? "+234" + value.slice(1)
    : value.startsWith("234") && value.length === 13 ? "+" + value : value;
  return /^\+[1-9]\d{7,14}$/.test(number) ? number : null;
}
async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
}
async function phoneHash(key: CryptoKey, phone: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(phone)));
  return [...digest].map(v => v.toString(16).padStart(2, "0")).join("");
}

Deno.serve(async request => {
  const origin = request.headers.get("Origin");
  if (request.method === "OPTIONS") return json({}, 200, origin);
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405, origin);
  const url = Deno.env.get("SUPABASE_URL");
  const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const pepper = Deno.env.get("BLINK_CONTACT_HMAC_SECRET");
  if (!url || !secret || !pepper || pepper.length < 32) {
    return json({ error: "Contact discovery is not configured" }, 503, origin);
  }
  const authorization = request.headers.get("Authorization") || "";
  const jwt = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!jwt) return json({ error: "Sign in required" }, 401, origin);

  const admin = createClient(url, secret, { auth: { persistSession: false, autoRefreshToken: false } });
  const { data: account, error: userError } = await admin.auth.getUser(jwt);
  const user = account?.user;
  if (userError || !user || !isUuid(user.id)) return json({ error: "Invalid session" }, 401, origin);

  let data: Record<string, unknown>;
  try {
    const raw = await request.text();
    if (raw.length > 48_000) return json({ error: "Contacts batch too large" }, 413, origin);
    data = JSON.parse(raw) as Record<string, unknown>;
    if (!data || typeof data !== "object" || Array.isArray(data)) throw Error("body");
  } catch {
    return json({ error: "Invalid request" }, 400, origin);
  }
  const uid = user.id;
  const action = String(data.action ?? "");
  // Profile phone is self-declared; it is NOT Supabase Auth phone ownership proof.
  // Reciprocal, consented contact imports are mandatory before revealing matches.
  const profileNumber = async () => {
    const { data: profile, error } = await admin.from("profiles")
      .select("phone").eq("id", uid).maybeSingle();
    if (error) throw error;
    return canonical(profile?.phone ?? "");
  };
  const key = await hmacKey(pepper);
  const dispatchPushFor = async (notificationId: string) => {
    if (!isUuid(notificationId)) return;
    try {
      await fetch(url + "/functions/v1/send-activity-notification", {
        method: "POST",
        headers: {
          "Content-Type": "application/json", "apikey": secret,
          "Authorization": "Bearer " + secret,
        },
        body: JSON.stringify({ notification_id: notificationId }),
      });
    } catch {
      // Trusted push worker will re-check privacy authorization and deduplicate.
    }
  };
  const dispatchNewContactAlerts = async () => {
    // Only after reciprocal opt-in matching and joined-alert authorization checks.
    const { data: ids, error } = await admin.rpc("blink_contact_my_recent_push_ids", { p_joiner: uid });
    if (error || !Array.isArray(ids)) return;
    for (const row of ids.slice(0, 100)) {
      const notificationId = String(row?.notification_id ?? "");
      await dispatchPushFor(notificationId);
    }
  };
  const freshSettings = async () => {
    const { data: prefs, error } = await admin.from("blink_contact_settings")
      .select("sync_enabled,discoverable,join_alerts,last_synced_at").eq("user_id", uid).maybeSingle();
    if (error) throw error;
    return prefs || { sync_enabled: false, discoverable: false, join_alerts: false, last_synced_at: null };
  };

  try {
    if (action === "get") {
      const settings = await freshSettings();
      // Do not misreport a broken/undeployed matching RPC as "zero friends".
      let matches: unknown[] = [];
      if (settings.sync_enabled) {
        const { data: found, error: matchingError } = await admin.rpc(
          "blink_contact_matches_for", { p_user: uid },
        );
        if (matchingError || !Array.isArray(found)) {
          throw Error("Contact matches backend unavailable");
        }
        matches = found;
      }
      return json({ settings, matches, match_policy: matchPolicy }, 200, origin);
    }
    if (action === "settings") {
      if (typeof data.sync_enabled !== "boolean" ||
          typeof data.discoverable !== "boolean" ||
          typeof data.join_alerts !== "boolean") {
        return json({ error: "Explicit consent choices are required" }, 400, origin);
      }
      const ownPhone = await profileNumber();
      if (data.discoverable && !data.sync_enabled) {
        return json({ error: "Enable contact sync before discoverability" }, 400, origin);
      }
      if (data.discoverable && !ownPhone) {
        return json({ error: "Add a phone number to your profile first" }, 409, origin);
      }
      const settings = {
        user_id: uid,
        sync_enabled: data.sync_enabled,
        discoverable: data.discoverable,
        join_alerts: data.join_alerts && data.sync_enabled,
        updated_at: new Date().toISOString(),
      };
      const { error: changeError } = await admin.from("blink_contact_settings")
        .upsert(settings, { onConflict: "user_id" });
      if (changeError) throw changeError;
      if (!data.sync_enabled) {
        const { error } = await admin.rpc("blink_contact_clear_imports", { p_owner: uid });
        if (error) throw error;
      }
      // Setting discoverable=false immediately removes this account from phone matches.
      const { error: directoryError } = await admin.rpc("blink_contact_upsert_opted_in", {
        p_user: uid,
        p_hmac: settings.discoverable && ownPhone ? await phoneHash(key, ownPhone) : "0".repeat(64),
      });
      if (directoryError) throw directoryError;
      if (data.discoverable) await dispatchNewContactAlerts();
      return json({ settings: await freshSettings(), match_policy: matchPolicy }, 200, origin);
    }
    if (action === "sync") {
      const settings = await freshSettings();
      if (!settings.sync_enabled) return json({ error: "Enable contact syncing first" }, 403, origin);
      const numbers = data.numbers;
      if (!Array.isArray(numbers) || numbers.length > 500 || numbers.some(n => typeof n !== "string" || n.length > 32)) {
        return json({ error: "Provide up to 500 contact numbers" }, 400, origin);
      }
      const last = Date.parse(settings.last_synced_at || "");
      if (Number.isFinite(last) && Date.now() - last < 60_000) {
        return json({ error: "Contacts were synced recently. Retry after one minute." }, 429, origin);
      }
      const clean = [...new Set(numbers.map(canonical).filter((v): v is string => !!v))];
      const hashes = await Promise.all(clean.map(phone => phoneHash(key, phone)));
      const { error } = await admin.rpc("blink_contact_replace_imports", { p_owner: uid, p_hashes: hashes });
      if (error) throw error;
      // The other participant may have imported their numbers before ours.
      // Reevaluate the mutual match only AFTER this fresh import.
      if (settings.discoverable) {
        const ownPhone = await profileNumber();
        if (!ownPhone) return json({ error: "Your profile phone is missing" }, 409, origin);
        const directory = await admin.rpc("blink_contact_upsert_opted_in", {
          p_user: uid, p_hmac: await phoneHash(key, ownPhone),
        });
        if (directory.error) throw directory.error;
        const alerts = await admin.rpc("blink_contact_notify_my_join", { p_joiner: uid });
        if (alerts.error) throw alerts.error;
        await dispatchNewContactAlerts();
      }
      // If an established user imports a recent joiner last, notify them now.
      // The SQL routine returns only that user's authorized recent notifications.
      const recent = await admin.rpc("blink_contact_notify_recent_matches", { p_owner: uid });
      if (recent.error) throw recent.error;
      if (Array.isArray(recent.data)) {
        for (const item of recent.data.slice(0, 100)) {
          await dispatchPushFor(String(item?.notification_id ?? ""));
        }
      }
      const result = await admin.rpc("blink_contact_matches_for", { p_user: uid });
      if (result.error) throw result.error;
      return json({ matched: result.data || [], synced: hashes.length, match_policy: matchPolicy }, 200, origin);
    }
    if (action === "delete") {
      const settings = { user_id: uid, sync_enabled: false, discoverable: false,
        join_alerts: false, updated_at: new Date().toISOString(), last_synced_at: null };
      const { error: preferenceError } = await admin.from("blink_contact_settings")
        .upsert(settings, { onConflict: "user_id" });
      if (preferenceError) throw preferenceError;
      const cleanup = await Promise.all([
        admin.rpc("blink_contact_clear_imports", { p_owner: uid }),
        admin.rpc("blink_contact_upsert_opted_in", { p_user: uid, p_hmac: "0".repeat(64) }),
      ]);
      if (cleanup.some(x => x.error)) throw Error("Contact data could not be deleted");
      return json({ deleted: true }, 200, origin);
    }
    return json({ error: "Unknown action" }, 400, origin);
  } catch (error) {
    // Never log contact numbers or query bodies.
    console.error("BLINK contact discovery request failed", action);
    return json({ error: "Contact discovery could not complete safely" }, 500, origin);
  }
});
