import { handleUsernameLogin } from "./handler.ts";

const OWNER = "11111111-2222-3333-4444-555555555555";
const MEMBER = "aaaaaaaa-2222-3333-4444-555555555555";
const config = {
  SUPABASE_URL: "http://127.0.0.1:19999",
  SUPABASE_ANON_KEY: "test-publishable-key",
  SUPABASE_SERVICE_ROLE_KEY: "test-server-key",
  BLINK_OWNER_AUTH_USER_ID: OWNER,
  BLINK_OWNER_LOGIN_IDENTIFIER_MAP: JSON.stringify({
    "1234": "owner@example.test",
    "second@example.test": "owner@example.test",
  }),
};

const expectStatus = (actual: Response, wanted: number, name: string) => {
  if (actual.status !== wanted) {
    throw new Error(`${name}: expected HTTP ${wanted}, got ${actual.status}`);
  }
};

Deno.test("owner aliases exercise the real edge handler with fake GoTrue, never live secrets", async () => {
  const originals = new Map<string, string | undefined>();
  for (const [key, value] of Object.entries(config)) {
    originals.set(key, Deno.env.get(key));
    Deno.env.set(key, value);
  }
  const realFetch = globalThis.fetch;
  let numericAliasTakenByOtherUser = false;
  let returnIncorrectUid = false;
  let tokenRequestCount = 0;
  const json = (value: unknown, status = 200) =>
    new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json" } });

  globalThis.fetch = async (input, init) => {
    const url = new URL(typeof input === "string" ? input : input.toString());
    if (url.pathname === "/rest/v1/profiles") {
      const name = url.searchParams.get("username") || "";
      if (name === "eq.1234") {
        return json(numericAliasTakenByOtherUser ? [{ id: MEMBER }] : []);
      }
      if (name === "eq.standard") return json([{ id: MEMBER, email: "member@example.test" }]);
      return json([]);
    }
    if (url.pathname === "/auth/v1/token") {
      tokenRequestCount++;
      const body = JSON.parse(String(init?.body || "{}"));
      if (body.password === "wrong") return json({ error: "Invalid login credentials" }, 400);
      if (body.password === "throttled") return json({ error: "Too many requests" }, 429);
      const isOwner = body.email === "owner@example.test";
      return json({
        access_token: "test-only-access",
        refresh_token: "test-only-refresh",
        user: { id: returnIncorrectUid ? MEMBER : (isOwner ? OWNER : MEMBER), email: body.email },
      });
    }
    throw new Error(`Unexpected mocked endpoint ${url.pathname}`);
  };

  const request = (username: string, password = "fixture-password") =>
    new Request("http://edge.test/functions/v1/username-login", {
      method: "POST",
      headers: { apikey: config.SUPABASE_ANON_KEY, "content-type": "application/json" },
      body: JSON.stringify({ username, password }),
    });

  try {
    let res = await handleUsernameLogin(request("1234"));
    expectStatus(res, 200, "numeric alias");
    let body = await res.json();
    if (body.user.id !== OWNER || body.user.email !== "owner@example.test") {
      throw new Error("numeric alias did not resolve to existing owner identity");
    }

    res = await handleUsernameLogin(request("SECOND@EXAMPLE.TEST"));
    expectStatus(res, 200, "email alias");
    body = await res.json();
    if (body.user.id !== OWNER) throw new Error("email alias created a different identity");

    res = await handleUsernameLogin(request("standard"));
    expectStatus(res, 200, "unrelated username login");
    body = await res.json();
    if (body.user.id !== MEMBER) throw new Error("ordinary accounts must keep their identity");

    res = await handleUsernameLogin(request("1234", "wrong"));
    expectStatus(res, 401, "wrong password");
    res = await handleUsernameLogin(request("1234", "throttled"));
    expectStatus(res, 429, "GoTrue rate limiting");

    returnIncorrectUid = true;
    res = await handleUsernameLogin(request("1234"));
    expectStatus(res, 401, "incorrect owner user ID");
    returnIncorrectUid = false;

    numericAliasTakenByOtherUser = true;
    const requestsBeforeCollision = tokenRequestCount;
    res = await handleUsernameLogin(request("1234"));
    expectStatus(res, 401, "registered username collision");
    if (tokenRequestCount !== requestsBeforeCollision) throw new Error("collision must reject before password exchange");
    numericAliasTakenByOtherUser = false;

    Deno.env.delete("BLINK_OWNER_AUTH_USER_ID");
    res = await handleUsernameLogin(request("1234"));
    expectStatus(res, 401, "owner UUID unset");
    Deno.env.set("BLINK_OWNER_AUTH_USER_ID", OWNER);

    res = await handleUsernameLogin(new Request("http://edge.test/functions/v1/username-login", {
      method: "POST", headers: { apikey: "incorrect" }, body: JSON.stringify({ username: "1234", password: "x" }),
    }));
    expectStatus(res, 401, "wrong client key");

    res = await handleUsernameLogin(new Request("http://edge.test/functions/v1/username-login", { method: "GET" }));
    expectStatus(res, 405, "wrong HTTP method");
  } finally {
    globalThis.fetch = realFetch;
    for (const [key, value] of originals) {
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
  }
});
