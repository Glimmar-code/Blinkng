import { isExpectedOwnerSession, resolveOwnerLoginAlias } from "./owner-identity.ts";

const OWNER_UID = "11111111-2222-3333-4444-555555555555";
const OWNER_EMAIL = "owner@example.test";
const mapping = JSON.stringify({
  "1234": OWNER_EMAIL,
  "backup@example.test": OWNER_EMAIL,
});

function equal(actual: unknown, expected: unknown, message: string): void {
  if (actual !== expected) throw new Error(message + ": expected " + expected + ", got " + actual);
}

Deno.test("unconfigured aliases fail closed", () => {
  equal(resolveOwnerLoginAlias("1234", undefined, OWNER_UID), "", "missing mapping");
  equal(resolveOwnerLoginAlias("1234", mapping, undefined), "", "missing owner id");
  equal(resolveOwnerLoginAlias("1234", mapping, "invalid"), "", "invalid owner id");
  equal(resolveOwnerLoginAlias("1234", "{", OWNER_UID), "", "invalid JSON");
});

Deno.test("owner aliases are case-insensitive and require exact identifier", () => {
  equal(resolveOwnerLoginAlias("1234", mapping, OWNER_UID), OWNER_EMAIL, "numeric username");
  equal(resolveOwnerLoginAlias("Backup@Example.Test", mapping, OWNER_UID), OWNER_EMAIL, "backup email");
  equal(resolveOwnerLoginAlias("other@example.test", mapping, OWNER_UID), "", "non-allowlisted");
});

Deno.test("reject malformed or dangerous owner mapping targets", () => {
  equal(resolveOwnerLoginAlias("1234", JSON.stringify({"1234":"not-an-email"}), OWNER_UID), "", "bad email");
  equal(resolveOwnerLoginAlias("1234", JSON.stringify({"1234":1234}), OWNER_UID), "", "non-string email");
  equal(resolveOwnerLoginAlias("1234", JSON.stringify({"1234":"x@example.test y"}), OWNER_UID), "", "space in email");
  equal(resolveOwnerLoginAlias("1234", JSON.stringify(["x@example.test"]), OWNER_UID), "", "list configuration");
});

Deno.test("aliased login must return matching owner user ID and email", () => {
  const valid = { user: { id: OWNER_UID.toUpperCase(), email: OWNER_EMAIL.toUpperCase() } };
  equal(isExpectedOwnerSession(valid, OWNER_EMAIL, OWNER_UID), true, "correct session");
  equal(isExpectedOwnerSession({ user: { ...valid.user, id: "aaaaaaaa-2222-3333-4444-555555555555" } }, OWNER_EMAIL, OWNER_UID), false, "different user");
  equal(isExpectedOwnerSession({ user: { ...valid.user, email: "other@example.test" } }, OWNER_EMAIL, OWNER_UID), false, "different email");
  equal(isExpectedOwnerSession({ user: null }, OWNER_EMAIL, OWNER_UID), false, "missing user");
  equal(isExpectedOwnerSession(null, OWNER_EMAIL, OWNER_UID), false, "missing response");
});
