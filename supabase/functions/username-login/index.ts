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

Deno.serve(async (req: Request) => {
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

  if (!username || !password || username.includes("@") || username.length > 30) {
    return response({ error: "Invalid email/username or password." }, 401);
  }

  try {
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
    const email = profiles?.[0]?.email?.trim().toLowerCase() || "";
    if (!email) {
      return response({ error: "Invalid email/username or password." }, 401);
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
      return response({ error: "Invalid email/username or password." }, 401);
    }

    const session = await tokenResult.json();
    return response(session, 200);
  } catch (error) {
    console.error("username-login unexpected error", error);
    return response({ error: "Authentication service is temporarily unavailable." }, 503);
  }
});
