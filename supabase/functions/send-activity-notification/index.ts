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
    const signature = new Uint8Array(
      await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, input),
    );
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

type DispatchClaim = { proceed: boolean; tracked: boolean; attempts: number; reason?: string };

async function claimDispatch(
  admin: ReturnType<typeof createClient>,
  notificationId: string,
): Promise<DispatchClaim> {
  const { data: existing, error: readError } = await admin
    .from("notification_push_dispatches")
    .select("status,attempts,updated_at")
    .eq("notification_id", notificationId)
    .maybeSingle();

  if (readError) return { proceed: true, tracked: false, attempts: 1 };
  if (existing?.status === "sent") {
    return { proceed: false, tracked: true, attempts: Number(existing.attempts ?? 1), reason: "already_sent" };
  }
  if (existing?.status === "sending") {
    const ageMs = Date.now() - Date.parse(String(existing.updated_at ?? ""));
    if (Number.isFinite(ageMs) && ageMs < 120_000) {
      return { proceed: false, tracked: true, attempts: Number(existing.attempts ?? 1), reason: "already_sending" };
    }
  }

  const attempts = Number(existing?.attempts ?? 0) + 1;
  const { error } = await admin.from("notification_push_dispatches").upsert({
    notification_id: notificationId,
    status: "sending",
    attempts,
    last_error: null,
    updated_at: new Date().toISOString(),
  }, { onConflict: "notification_id" });

  return error
    ? { proceed: true, tracked: false, attempts }
    : { proceed: true, tracked: true, attempts };
}

async function finishDispatch(
  admin: ReturnType<typeof createClient>,
  notificationId: string,
  tracked: boolean,
  ok: boolean,
  errorText = "",
) {
  if (!tracked) return;
  await admin.from("notification_push_dispatches")
    .update({
      status: ok ? "sent" : "failed",
      last_error: errorText ? errorText.slice(0, 800) : null,
      updated_at: new Date().toISOString(),
    })
    .eq("notification_id", notificationId);
}

function wireType(notification: Record<string, unknown>): string {
  const type = String(notification.type ?? "social").toLowerCase();
  const targetType = String(notification.target_type ?? "").toLowerCase();
  const metadata = (notification.metadata ?? {}) as Record<string, unknown>;
  if (targetType === "story" && type === "like") return "story_like";
  if (type === "comment" && metadata.is_reply === true) return "reply";
  return type;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  const auth = req.headers.get("Authorization") ?? "";
  const actorId = jwtSubject(auth);
  if (!actorId) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const firebaseJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!supabaseUrl || !serviceRole) return json({ error: "Supabase server configuration missing" }, 503);
  if (!firebaseJson) return json({ error: "Firebase server credential is not configured" }, 503);

  let notificationId = "";
  try {
    const payload = await req.json();
    notificationId = String(payload?.notification_id ?? "").trim();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }
  if (!notificationId) return json({ error: "notification_id is required" }, 400);

  const admin = createClient(supabaseUrl, serviceRole, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: notification, error: notificationError } = await admin
    .from("notifications")
    .select("id,user_id,actor_id,type,post_id,target_type,target_id,text,sub_text,metadata,created_at")
    .eq("id", notificationId)
    .maybeSingle();
  if (notificationError) return json({ error: "Unable to load notification" }, 500);
  if (!notification) return json({ error: "Notification not found" }, 404);

  // Automatic user-generated pushes must be attributable to the JWT that caused the event.
  if (!notification.actor_id || String(notification.actor_id) !== actorId) {
    return json({ error: "Forbidden" }, 403);
  }

  const recipientId = String(notification.user_id ?? "").trim();
  if (!recipientId || recipientId === actorId) return json({ ok: true, skipped: "self_or_missing_recipient" });

  const type = wireType(notification as Record<string, unknown>);
  // DMs have a dedicated dispatcher with message delivery acknowledgement and are never sent here.
  if (type === "system" && String(notification.text ?? "").toLowerCase().includes(" sent you a message")) {
    return json({ ok: true, skipped: "message_pipeline" });
  }

  const { data: allowed, error: allowedError } = await admin.rpc("notification_push_allowed", {
    p_user_id: recipientId,
    p_type: type,
  });
  if (!allowedError && allowed === false) {
    return json({ ok: true, skipped: "recipient_preferences" });
  }

  const claim = await claimDispatch(admin, notificationId);
  if (!claim.proceed) {
    return json({ ok: true, skipped: claim.reason ?? "duplicate", attempts: claim.attempts });
  }

  const [{ data: actor }, { data: tokenRows }, { data: legacyRecipient }] = await Promise.all([
    admin.from("profiles").select("username,full_name,avatar_url").eq("id", actorId).maybeSingle(),
    admin.from("fcm_tokens").select("token").eq("user_id", recipientId).eq("is_active", true)
      .order("updated_at", { ascending: false }).limit(20),
    admin.from("profiles").select("fcm_token").eq("id", recipientId).maybeSingle(),
  ]);

  const tokens = new Set<string>();
  for (const row of tokenRows ?? []) {
    const token = String(row?.token ?? "").trim();
    if (token) tokens.add(token);
  }
  const legacyToken = String(legacyRecipient?.fcm_token ?? "").trim();
  if (legacyToken) tokens.add(legacyToken);

  if (tokens.size === 0) {
    await finishDispatch(admin, notificationId, claim.tracked, false, "recipient_has_no_push_token");
    return json({ ok: true, skipped: "recipient_has_no_push_token" });
  }

  const firebase = await firebaseAccessToken(firebaseJson);
  if (!firebase.token || !firebase.projectId) {
    await finishDispatch(admin, notificationId, claim.tracked, false, String(firebase.error ?? "firebase_auth_failed"));
    return json({ error: firebase.error ?? "Firebase authentication failed" }, 502);
  }

  const senderUsername = String(actor?.username ?? "");
  const senderName = String(actor?.full_name ?? (senderUsername || "Blink user"));
  const senderAvatar = String(actor?.avatar_url ?? "");
  const action = String(notification.text ?? "New activity").trim();
  const body = String(notification.sub_text ?? "").trim() || action;
  const title = action.startsWith("@") || action.toLowerCase().startsWith(senderName.toLowerCase())
    ? action
    : `${senderName} ${action}`.trim();

  const data: Record<string, string> = {
    type,
    title: title.slice(0, 180),
    body: body.slice(0, 1200),
    notification_id: notificationId,
    recipient_id: recipientId,
    sender_id: actorId,
    sender_username: senderUsername,
    sender_name: senderName,
    sender_avatar: senderAvatar,
    target_type: String(notification.target_type ?? ""),
    target_id: String(notification.target_id ?? ""),
  };
  const postId = String(notification.post_id ?? "").trim();
  if (postId) data.post_id = postId;
  if (String(notification.target_type ?? "") === "story") {
    data.story_id = String(notification.target_id ?? "");
  }

  const collapseTarget = postId || String(notification.target_id ?? recipientId);
  const sendResults = await Promise.all([...tokens].map(async (token) => {
    try {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${firebase.projectId}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${firebase.token}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token,
              data,
              android: {
                priority: type === "mention" || type === "comment" || type === "reply" ? "HIGH" : "NORMAL",
                ttl: "86400s",
                collapse_key: `blink_${type}_${collapseTarget}`.slice(0, 64),
              },
            },
          }),
        },
      );
      const text = await response.text();
      if (!response.ok && isUnregisteredFcmToken(response.status, text)) {
        await admin.from("fcm_tokens")
          .update({ is_active: false, updated_at: new Date().toISOString() })
          .eq("token", token);
        await admin.from("profiles")
          .update({ fcm_token: null })
          .eq("id", recipientId)
          .eq("fcm_token", token);
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
    .join(" | ");

  await finishDispatch(admin, notificationId, claim.tracked, delivered > 0, failureText);

  return json({
    ok: delivered > 0,
    delivered,
    failed: sendResults.length - delivered,
    devices: sendResults.length,
    attempts: claim.attempts,
  });
});
