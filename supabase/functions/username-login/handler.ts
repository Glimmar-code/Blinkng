import { resolveOwnerLoginAlias, isExpectedOwnerSession } from "./owner-identity.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const jsonHeaders = {
  ...corsHeaders,
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

function firstKeyFromJsonEnv(name: string): string {
  const raw = Deno.env.get(name);
  if (!raw) return "";
  try {
    const parsed = JSON.parse(raw) as Record<string, string>;
    return parsed.default || Object.values(parsed).find(Boolean) || "";
  } catch {
    return "";
  }
}

function gatewayHeaders(key: string): Record<string, string> {
  const headers: Record<string, string> = {
    apikey: key,
    Accept: "application/json",
  };
  if (key && !key.startsWith("sb_")) {
    headers.Authorization = `Bearer ${key}`;
  }
  return headers;
}

function response(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: jsonHeaders });
}

/**
 * Optional owner sign-in aliases. This is configuration, not authorization:
 * GoTrue still validates the real account's password and issues its session.
 *
 * Store the JSON mapping only in a server-side Supabase Edge Function secret,
 * never in the repo, mobile app, or web bundle. No alias is active by default.
 */

export async function handleUsernameLogin(req: Request): Promise<Response> {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return response({ error: "Method not allowed." }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") || "";
  const legacyAnon = Deno.env.get("SUPABASE_ANON_KEY") || "";
  const legacyService = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  const modernPublishable = firstKeyFromJsonEnv("SUPABASE_PUBLISHABLE_KEYS");
  const modernSecret = firstKeyFromJsonEnv("SUPABASE_SECRET_KEYS");
  const publishableKey = modernPublishable || legacyAnon;
  const secretKey = modernSecret || legacyService;

  if (!supabaseUrl || !publishableKey || !secretKey) {
    console.error("username-login: required Supabase environment keys are unavailable");
    return response({ error: "Authentication service is unavailable." }, 503);
  }

  const callerKey = req.headers.get("apikey") || "";
  const allowedCallerKeys = [legacyAnon, modernPublishable].filter(Boolean);
  if (!callerKey || !allowedCallerKeys.includes(callerKey)) {
    return response({ error: "Unauthorized client." }, 401);
  }

  let payload: { username?: unknown; password?: unknown };
  try {
    payload = await req.json();
  } catch {
    return response({ error: "Invalid request." }, 400);
  }

  const username = String(payload.username ?? "")
    .trim()
    .replace(/^@+/, "")
    .toLowerCase();
  const password = String(payload.password ?? "");

  const mappedEmail = resolveOwnerLoginAlias(
    username,
    Deno.env.get("BLINK_OWNER_LOGIN_IDENTIFIER_MAP"),
    Deno.env.get("BLINK_OWNER_AUTH_USER_ID"),
  );
  if (!username || !password || username.length > 254 ||
      (!mappedEmail && (username.includes("@") || username.length > 30))) {
    return response({ error: "Invalid email/username or password." }, 401);
  }

  try {
    // A configured numeric alias must never shadow a real user's username.
    // Fail closed if another profile now owns that identifier.
    if (mappedEmail && !username.includes("@")) {
      const lookupUrl = `${supabaseUrl}/rest/v1/profiles?username=eq.${encodeURIComponent(username)}&select=id&limit=1`;
      const lookup = await fetch(lookupUrl, {
        method: "GET",
        headers: gatewayHeaders(secretKey),
      });
      if (!lookup.ok) return response({ error: "Authentication service is unavailable." }, 503);
      const matches = await lookup.json() as Array<{ id?: string }>;
      const registeredId = matches?.[0]?.id || "";
      const expectedId = Deno.env.get("BLINK_OWNER_AUTH_USER_ID") || "";
      if (registeredId && registeredId.toLowerCase() !== expectedId.toLowerCase()) {
        return response({ error: "Invalid email/username or password." }, 401);
      }
    }

    let email = mappedEmail;
    if (!email) {
      const profileUrl =
        `${supabaseUrl}/rest/v1/profiles?username=eq.${encodeURIComponent(username)}` +
        `&select=email&limit=1`;

      const profileResult = await fetch(profileUrl, {
        method: "GET",
        headers: gatewayHeaders(secretKey),
      });

      if (!profileResult.ok) {
        console.error(`username-login: profile lookup failed (${profileResult.status})`);
        return response({ error: "Invalid email/username or password." }, 401);
      }

      const profiles = await profileResult.json() as Array<{ email?: string }>;
      email = profiles?.[0]?.email?.trim().toLowerCase() || "";
      if (!email) {
        return response({ error: "Invalid email/username or password." }, 401);
      }
    }

    const tokenResult = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: {
        ...gatewayHeaders(publishableKey),
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password }),
    });

    if (!tokenResult.ok) {
      // Preserve the GoTrue rate-limit response rather than masking it as a bad password.
      if (tokenResult.status === 429) {
        return response({ error: "Too many login attempts. Try again later." }, 429);
      }
      return response({ error: "Invalid email/username or password." }, 401);
    }

    const session = await tokenResult.json();
    if (mappedEmail && !isExpectedOwnerSession(
      session, mappedEmail, Deno.env.get("BLINK_OWNER_AUTH_USER_ID") || "",
    )) {
      console.error("username-login: configured owner identity mismatch");
      return response({ error: "Invalid email/username or password." }, 401);
    }
    return response(session, 200);
  } catch {
    // Do not log credentials or request bodies during authentication failures.
    console.error("username-login unexpected authentication error");
    return response({ error: "Authentication service is temporarily unavailable." }, 503);
  }
}
