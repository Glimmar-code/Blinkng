// Regression guard for legacy auth screens. No live accounts or credentials.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = path => readFileSync(new URL("../../" + path, import.meta.url), "utf8");
const legacy = source("app/src/main/java/com/example/ui/screens/AuthScreens.kt");
const reference = source("app/src/main/java/com/example/ui/screens/BlinkAuthReferenceForms.kt");
const remember = source("app/src/main/java/com/example/auth/RememberedLoginStore.kt");

const signIn = legacy.slice(legacy.indexOf("fun SignInScreen("), legacy.indexOf("fun SignUpScreen("));
const signUp = legacy.slice(legacy.indexOf("fun SignUpScreen("), legacy.indexOf("// ================================================================",legacy.indexOf("fun SignUpScreen(") + 30) < 0 ? undefined : legacy.indexOf("// ================================================================",legacy.indexOf("fun SignUpScreen(") + 30));

test("both legacy and current sign-in fields avoid persisting the password", () => {
  assert.match(signIn, /var password by remember \{ mutableStateOf\(""\) \}/);
  assert.doesNotMatch(signIn, /rememberedLogin\?\.password/);
  assert.doesNotMatch(signIn, /RememberedLoginStore\.save\([^)]*\bpassword\b/);
  assert.match(reference, /var password by remember \{ mutableStateOf\(""\) \}/);
  assert.match(signIn, /Remember my username/);
});

test("legacy signup password never enters encrypted durable drafts", () => {
  assert.match(signUp, /PersistentTextDraftStore\.clearValue\(signupContext, "com\/example\/ui\/screens\/AuthScreens\.kt:password:4"\)/);
  assert.match(signUp, /var password by remember \{ mutableStateOf\(""\) \}/);
  assert.doesNotMatch(signUp, /rememberPersistentTextState\(key\s*=\s*"[^"]*password/i);
});

test("legacy login store migrates away from saved passwords without restoring them", () => {
  assert.doesNotMatch(remember, /\.put\("password"/);
  assert.doesNotMatch(remember, /obj\.optString\("password"\)/);
  assert.match(remember, /val raw = JSONObject\(\)\.put\("identifier", clean\)/);
  assert.match(remember, /if \(obj\.has\("password"\)\)/);
  assert.match(remember, /if \(identifier\.isBlank\(\)\) clear\(context\)/);
  assert.match(remember, /else save\(context, identifier, ""\)/);
  assert.match(remember, /Login\(identifier\)\.takeIf/);
});
