import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json", "Connection": "keep-alive" },
});

let firebaseTokenCache: { token: string; projectId: string; expiresAt: number } | null = null;

function jwtSubject(authHeader: string): string | null {
  try {
    const token = authHeader.replace(/^Bearer\s+/i, "");
    const payload = token.split(".")[1];
    if (!payload) return null;
    const normalized = payload.replaceAll("-", "+").replaceAll("_", "/");
    const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
    return JSON.parse(atob(padded)).sub ?? null;
  } catch {
    return null;
  }
}

function base64Url(input: string): string {
  return btoa(input).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

async function firebaseAccessToken(serviceAccountJson: string) {
  const nowMs = Date.now();
  if (firebaseTokenCache && firebaseTokenCache.expiresAt > nowMs + 60_000) {
    return { token: firebaseTokenCache.token, projectId: firebaseTokenCache.projectId };
  }
  try {
    const sa = JSON.parse(serviceAccountJson);
    if (!sa.client_email || !sa.private_key || !sa.project_id) {
      return { error: "Invalid Firebase service account" };
    }
    const now = Math.floor(nowMs / 1000);
    const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const claim = base64Url(JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }));
    const pem = String(sa.private_key)
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replace(/\s/g, "");
    const keyBytes = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    const key = await crypto.subtle.importKey(
      "pkcs8",
      keyBytes,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const input = new TextEncoder().encode(`${header}.${claim}`);
    const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, input));
    const signed = `${header}.${claim}.${base64Url(String.fromCharCode(...signature))}`;
    const response = await fetch("https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion: signed,
      }),
    });
    if (!response.ok) return { error: `Firebase OAuth failed (${response.status})` };
    const body = await response.json();
    const accessToken = String(body.access_token ?? "");
    if (!accessToken) return { error: "Firebase OAuth returned no access token" };
    const expiresInSeconds = Number(body.expires_in ?? 3600);
    firebaseTokenCache = {
      token: accessToken,
      projectId: String(sa.project_id),
      expiresAt: nowMs + Math.max(300, expiresInSeconds - 120) * 1000,
    };
    return { token: accessToken, projectId: String(sa.project_id) };
  } catch (error) {
    return { error: error instanceof Error ? error.message : "Firebase auth failed" };
  }
}

function isUnregisteredFcmToken(status: number, responseText: string): boolean {
  const text = responseText.toUpperCase();
  return status === 404 || text.includes("UNREGISTERED") || text.includes("REGISTRATION-TOKEN-NOT-REGISTERED");
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);
  const auth = req.headers.get("Authorization") ?? "";
  const senderId = jwtSubject(auth);
  if (!senderId) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const firebaseJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!supabaseUrl || !serviceRole) return json({ error: "Supabase server configuration missing" }, 503);
  if (!firebaseJson) return json({ error: "Firebase server credential is not configured" }, 503);

  let callId = "";
  let event = "invite";
  try {
    const payload = await req.json();
    callId = String(payload?.call_id ?? "").trim();
    event = String(payload?.event ?? "invite").trim().toLowerCase();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }
  if (!callId) return json({ error: "call_id is required" }, 400);
  if (!["invite", "cancelled", "declined", "ended", "missed", "failed"].includes(event)) {
    return json({ error: "Unsupported call event" }, 400);
  }

  const admin = createClient(supabaseUrl, serviceRole, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: call, error: callError } = await admin
    .from("calls")
    .select("id,conversation_id,caller_id,callee_id,call_type,status,timeout_at")
    .eq("id", callId)
    .maybeSingle();
  if (callError) return json({ error: "Unable to load call" }, 500);
  if (!call) return json({ error: "Call not found" }, 404);
  if (senderId !== call.caller_id && senderId !== call.callee_id) return json({ error: "Forbidden" }, 403);

  let targetUserId = senderId === call.caller_id ? String(call.callee_id) : String(call.caller_id);
  if (event === "invite") {
    if (senderId !== call.caller_id || call.status !== "ringing") return json({ error: "Call is not ringable" }, 409);
    if (Date.parse(String(call.timeout_at)) <= Date.now()) return json({ ok: true, skipped: "expired" });
    targetUserId = String(call.callee_id);
  }

  const expectedStatus: Record<string, string[]> = {
    cancelled: ["cancelled", "ended"],
    declined: ["declined"],
    ended: ["ended"],
    missed: ["missed"],
    failed: ["failed"],
  };
  if (event !== "invite" && !(expectedStatus[event] ?? []).includes(String(call.status))) {
    return json({ ok: true, skipped: `status_${call.status}` });
  }

  const { data: prior } = await admin
    .from("call_push_dispatches")
    .select("status,attempts,updated_at")
    .eq("call_id", callId)
    .eq("event", event)
    .eq("target_user_id", targetUserId)
    .maybeSingle();
  if (prior?.status === "sent") return json({ ok: true, skipped: "already_sent" });
  if (prior?.status === "sending") {
    const ageMs = Date.now() - Date.parse(String(prior.updated_at ?? ""));
    if (Number.isFinite(ageMs) && ageMs < 90_000) return json({ ok: true, skipped: "already_sending" });
  }
  const attempts = Number(prior?.attempts ?? 0) + 1;
  await admin.from("call_push_dispatches").upsert({
    call_id: callId,
    event,
    target_user_id: targetUserId,
    status: "sending",
    attempts,
    last_error: null,
    updated_at: new Date().toISOString(),
  }, { onConflict: "call_id,event,target_user_id" });

  const [{ data: sender }, { data: tokenRows }, { data: legacyRecipient }] = await Promise.all([
    admin.from("profiles").select("username,full_name,avatar_url").eq("id", senderId).maybeSingle(),
    admin.from("fcm_tokens").select("token").eq("user_id", targetUserId).eq("is_active", true).order("updated_at", { ascending: false }).limit(20),
    admin.from("profiles").select("fcm_token").eq("id", targetUserId).maybeSingle(),
  ]);

  const tokens = new Set<string>();
  for (const row of tokenRows ?? []) {
    const token = String(row?.token ?? "").trim();
    if (token) tokens.add(token);
  }
  const legacyToken = String(legacyRecipient?.fcm_token ?? "").trim();
  if (legacyToken) tokens.add(legacyToken);
  if (tokens.size === 0) {
    await admin.from("call_push_dispatches")
      .update({ status: "failed", last_error: "recipient_has_no_push_token", updated_at: new Date().toISOString() })
      .eq("call_id", callId).eq("event", event).eq("target_user_id", targetUserId);
    return json({ ok: true, skipped: "recipient_has_no_push_token" });
  }

  const firebase = await firebaseAccessToken(firebaseJson);
  if (!firebase.token || !firebase.projectId) {
    await admin.from("call_push_dispatches")
      .update({ status: "failed", last_error: String(firebase.error ?? "firebase_auth_failed"), updated_at: new Date().toISOString() })
      .eq("call_id", callId).eq("event", event).eq("target_user_id", targetUserId);
    return json({ error: firebase.error ?? "Firebase authentication failed" }, 502);
  }

  const senderUsername = String(sender?.username ?? "");
  const senderName = String(sender?.full_name ?? (senderUsername || "Blink user"));
  const senderAvatar = String(sender?.avatar_url ?? "");
  const callType = String(call.call_type);
  const incoming = event === "invite";
  const data: Record<string, string> = {
    type: incoming ? "incoming_call" : "call_update",
    call_event: event,
    call_id: String(call.id),
    call_type: callType,
    conversation_id: String(call.conversation_id),
    caller_id: String(call.caller_id),
    callee_id: String(call.callee_id),
    sender_username: senderUsername,
    sender_name: senderName,
    sender_avatar: senderAvatar,
    title: incoming ? `${senderName} is calling` : "Call updated",
    body: incoming ? `Incoming ${callType} call` : `Call ${event}`,
  };

  const sendResults = await Promise.all([...tokens].map(async (token) => {
    try {
      const response = await fetch(`https://fcm.googleapis.com/v1/projects/${firebase.projectId}/messages:send`, {
        method: "POST",
        headers: { Authorization: `Bearer ${firebase.token}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          message: {
            token,
            data,
            android: {
              priority: "HIGH",
              ttl: incoming ? "60s" : "300s",
              collapse_key: `blink_call_${callId}`,
            },
          },
        }),
      });
      const text = await response.text();
      if (!response.ok && isUnregisteredFcmToken(response.status, text)) {
        await admin.from("fcm_tokens").update({ is_active: false, updated_at: new Date().toISOString() }).eq("token", token);
        await admin.from("profiles").update({ fcm_token: null }).eq("id", targetUserId).eq("fcm_token", token);
      }
      return { ok: response.ok, status: response.status, detail: response.ok ? "" : text.slice(0, 300) };
    } catch (error) {
      return { ok: false, status: 0, detail: error instanceof Error ? error.message : "FCM request failed" };
    }
  }));

  const delivered = sendResults.filter((result) => result.ok).length;
  const failureText = sendResults
    .filter((result) => !result.ok)
    .map((result) => `${result.status}:${result.detail}`)
    .join(" | ")
    .slice(0, 1000);
  await admin.from("call_push_dispatches")
    .update({
      status: delivered > 0 ? "sent" : "failed",
      last_error: failureText || null,
      updated_at: new Date().toISOString(),
    })
    .eq("call_id", callId).eq("event", event).eq("target_user_id", targetUserId);

  return json({ ok: delivered > 0, delivered, failed: sendResults.length - delivered, devices: sendResults.length, attempts });
});
