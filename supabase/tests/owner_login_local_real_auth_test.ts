/**
 * Disposable local Supabase GoTrue integration test.
 *
 * Exercises the real password-grant backend and the actual username-login
 * function handler against a brand-new local user, not the production owner.
 * No production keys, passwords, or user data are needed.
 */
import { handleUsernameLogin } from "../functions/username-login/handler.ts";

function assert(ok: unknown, explanation: string): asserts ok {
  if (!ok) throw new Error(explanation);
}
async function requestJson(url: string, init: RequestInit): Promise<Record<string, unknown>> {
  const response = await fetch(url, init);
  const payload = await response.json() as Record<string, unknown>;
  assert(response.ok, `Local Supabase request failed with status ${response.status}`);
  return payload;
}
Deno.test("owner aliases issue one real GoTrue session while preventing account substitution", async () => {
  const root = Deno.env.get("SUPABASE_URL") || "";
  const anon = Deno.env.get("SUPABASE_ANON_KEY") || "";
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  assert(root.includes("127.0.0.1") || root.includes("localhost"), "Only a local auth server may be used");
  assert(anon.length > 10 && service.length > 10, "Missing local Supabase keys");

  const safeId = crypto.randomUUID().replaceAll("-", "");
  const ownerEmail = `owner-${safeId}@example.test`;
  const secondaryEmail = `alternative-${safeId}@example.test`;
  const memberEmail = `member-${safeId}@example.test`;
  const password = `LocalOnly-${crypto.randomUUID()}!`;

  async function signup(email: string): Promise<string> {
    const result = await requestJson(`${root}/auth/v1/signup`, {
      method: "POST",
      headers: { "apikey": anon, "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    const user = result.user as { id?: string } | undefined;
    assert(user?.id, "Local Supabase Auth signup did not return a user");
    return user.id;
  }

  const ownerId = await signup(ownerEmail);
  const memberId = await signup(memberEmail);
  assert(ownerId !== memberId, "Disposable owner and member must be different Auth identities");

  const keys = [
    "SUPABASE_URL", "SUPABASE_ANON_KEY", "SUPABASE_SERVICE_ROLE_KEY",
    "BLINK_OWNER_AUTH_USER_ID", "BLINK_OWNER_LOGIN_IDENTIFIER_MAP",
  ];
  const original = new Map(keys.map(key => [key, Deno.env.get(key)]));
  Deno.env.set("BLINK_OWNER_AUTH_USER_ID", ownerId);
  Deno.env.set("BLINK_OWNER_LOGIN_IDENTIFIER_MAP", JSON.stringify({
    "1234": ownerEmail,
    [secondaryEmail]: ownerEmail,
  }));

  const login = (username: string, secret: string) =>
    handleUsernameLogin(new Request("https://edge.invalid/functions/v1/username-login", {
      method: "POST",
      headers: { "apikey": anon, "Content-Type": "application/json" },
      body: JSON.stringify({ username, password: secret }),
    }));
  try {
    const primarySignIn = await requestJson(`${root}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: { "apikey": anon, "Content-Type": "application/json" },
      body: JSON.stringify({ email: ownerEmail, password }),
    });
    const direct = primarySignIn.user as { id: string };
    assert(direct.id === ownerId, "Direct owner sign-in used the wrong account");

    for (const alias of ["1234", secondaryEmail]) {
      const response = await login(alias, password);
      assert(response.status === 200, `Owner alias ${alias.includes("@") ? "email" : "numeric"} rejected by real GoTrue`);
      const session = await response.json() as { user: { id: string }; access_token?: string };
      assert(session.user.id === ownerId, "Alias login issued a token for the wrong user");
      assert(session.access_token, "Alias login did not issue an authenticated session");
    }

    let response = await login("1234", "not-the-real-password");
    assert(response.status === 401, "Incorrect password should never log in");

    Deno.env.set("BLINK_OWNER_AUTH_USER_ID", memberId);
    response = await login("1234", password);
    assert(response.status === 401, "Owner UUID mismatch must reject session");
    Deno.env.set("BLINK_OWNER_AUTH_USER_ID", ownerId);

    Deno.env.delete("BLINK_OWNER_LOGIN_IDENTIFIER_MAP");
    response = await login("1234", password);
    assert(response.status === 401, "Aliases must be disabled without server-side configuration");
    Deno.env.set("BLINK_OWNER_LOGIN_IDENTIFIER_MAP", JSON.stringify({
      "1234": ownerEmail,
      [secondaryEmail]: ownerEmail,
    }));

    // Simulate a newly registered username collision. The alias must fail closed.
    const createProfile = await fetch(`${root}/rest/v1/profiles`, {
      method: "POST",
      headers: {
        apikey: service,
        Authorization: `Bearer ${service}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ id: memberId, username: "1234", email: memberEmail }),
    });
    assert(createProfile.ok, `Could not seed a local username collision: ${createProfile.status}`);
    response = await login("1234", password);
    assert(response.status === 401, "Real database username collision must reject an owner alias");
  } finally {
    for (const [key, value] of original) {
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
  }
});
