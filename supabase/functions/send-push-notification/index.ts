import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json", "Connection": "keep-alive" },
});

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

async function firebaseAccessToken(serviceAccountJson: string): Promise<{ token?: string; projectId?: string; error?: string }> {
  try {
    const sa = JSON.parse(serviceAccountJson);
    if (!sa.client_email || !sa.private_key || !sa.project_id) return { error: "Invalid Firebase service account" };
    const now = Math.floor(Date.now() / 1000);
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
    return { token: body.access_token, projectId: sa.project_id };
  } catch (error) {
    return { error: error instanceof Error ? error.message : "Firebase auth failed" };
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  const auth = req.headers.get("Authorization") ?? "";
  const senderId = jwtSubject(auth);
  if (!senderId) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRole) return json({ error: "Supabase server configuration missing" }, 503);

  let messageId = "";
  try {
    const payload = await req.json();
    messageId = String(payload?.message_id ?? "").trim();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }
  if (!messageId) return json({ error: "message_id is required" }, 400);

  const admin = createClient(supabaseUrl, serviceRole, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: message, error: messageError } = await admin
    .from("messages")
    .select("id,conversation_id,sender_id,content")
    .eq("id", messageId)
    .maybeSingle();
  if (messageError) return json({ error: "Unable to load message" }, 500);
  if (!message) return json({ error: "Message not found" }, 404);
  if (message.sender_id !== senderId) return json({ error: "Forbidden" }, 403);

  const { data: recipientParticipant, error: participantError } = await admin
    .from("conversation_participants")
    .select("user_id")
    .eq("conversation_id", message.conversation_id)
    .neq("user_id", senderId)
    .limit(1)
    .maybeSingle();
  if (participantError) return json({ error: "Unable to resolve recipient" }, 500);
  if (!recipientParticipant?.user_id) return json({ ok: true, skipped: "no_recipient" });

  const [{ data: sender }, { data: recipient }] = await Promise.all([
    admin.from("profiles").select("username,full_name,avatar_url").eq("id", senderId).maybeSingle(),
    admin.from("profiles").select("fcm_token").eq("id", recipientParticipant.user_id).maybeSingle(),
  ]);

  const recipientToken = String(recipient?.fcm_token ?? "").trim();
  if (!recipientToken) return json({ ok: true, skipped: "recipient_has_no_push_token" });

  const firebaseJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!firebaseJson) return json({ error: "Firebase server credential is not configured" }, 503);
  const firebase = await firebaseAccessToken(firebaseJson);
  if (!firebase.token || !firebase.projectId) return json({ error: firebase.error ?? "Firebase authentication failed" }, 502);

  const senderUsername = String(sender?.username ?? "");
  const senderName = String(sender?.full_name ?? (senderUsername || "Blink user"));
  const senderAvatar = String(sender?.avatar_url ?? "");
  const content = String(message.content ?? "");

  const fcmResponse = await fetch(`https://fcm.googleapis.com/v1/projects/${firebase.projectId}/messages:send`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${firebase.token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message: {
        token: recipientToken,
        data: {
          type: "message",
          title: senderName,
          body: content,
          sender_username: senderUsername,
          sender_name: senderName,
          sender_avatar: senderAvatar,
          conversation_id: String(message.conversation_id),
          message_id: String(message.id),
        },
        android: { priority: "HIGH" },
      },
    }),
  });

  const fcmText = await fcmResponse.text();
  if (!fcmResponse.ok) return json({ error: "FCM send failed", status: fcmResponse.status, detail: fcmText.slice(0, 500) }, 502);
  return json({ ok: true });
});
