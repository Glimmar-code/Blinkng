# BLINK owner sign-in aliases — Testlab rollout

## Goal

One existing BLINK Auth user ID, one strong password managed by Supabase Auth, and three optional sign-in identifiers. The owner’s existing profile, data, admin privileges, and server-side RLS remain unchanged. Never create duplicate owner users to support extra identifiers.

## Existing identity checks (read-only)

- The primary account must be verified through Supabase Auth.
- Verify that the primary auth user UUID equals the sole active `private.admin_roles` record with `role = 'owner'`.
- Verify control of every secondary email address outside this repository before enabling it.
- Check for identifier collisions in Supabase Auth emails and `public.profiles.username`; do not shadow other users' existing identities.
- Treat the numeric identifier as a username/alias, **not** a password or OTP.

## Configuration: private Edge Function environment only

Two secrets are required before *any* alias works:

- `BLINK_OWNER_AUTH_USER_ID`: UUID of the verified existing owner in Supabase Auth.
- `BLINK_OWNER_LOGIN_IDENTIFIER_MAP`: JSON object mapping approved alternate login identifiers to the owner's primary Auth email, for example:
  `{"alternative@example.test":"owner@example.test","0000":"owner@example.test"}`.

Do not commit the real UUID, real email mapping, password, service-role key, or OTP to GitHub; do not place them in web config, Android/Windows binaries, or analytics. Alias mappings are **disabled by default**. Protect the function secret and reserve or otherwise prevent registration of the numerical owner alias.

**Do not reuse a password supplied in a chat.** The owner should set a new unique strong password directly using Supabase's verified-password-reset or authenticated password-change flow, and enable MFA for privileged operations.

## Behavior

1. On Android, Windows and web, ordinary email sign-in first uses Supabase Auth directly.
2. When an email password sign-in fails, the existing `username-login` function can securely recognize an allowed alternate email. Non-email identifiers already use this function.
3. The function only maps identifiers provided in the private allowlist when the private owner UUID is configured.
4. Supabase Auth itself verifies the password and supplies the session; the function checks the returned Auth UUID and primary email against the configured owner identity.
5. The client saves the normal Supabase access/refresh session. **No client-side admin role or new account** is issued.
6. Invalid identifiers and bad credentials receive a generic rejection; GoTrue HTTP 429 rate limits are retained.

## Checks before deployment

- Run `deno check supabase/functions/username-login/index.ts`.
- Run `deno test supabase/functions/username-login/owner-identity_test.ts`.
- Run `node --check web/app.js`.
- Run Android test, lint and build; Windows desktop build; website parity tests and auth route checks.
- In an isolated/local staging Auth environment, verify owner identifier mappings to *one* UUID, ordinary user sign-in, wrong password, 429 throttling, wrong owner UUID, no-config behavior, secondary-email conflict, expired sessions, logout, and password recovery.
- Confirm admin permissions are derived only from the server-side UUID/role, not an email string or client metadata.
- Have the owner perform a real-device login and private password update with MFA enabled.

## Release gate

As there is currently no Supabase hosted staging branch, **do not deploy this authentication function or set its secrets on production first**. Keep the PR as draft until staging tests, actual mail ownership, security review and Android/Windows/web checks pass. Only then merge to Testlab, obtain approvals, promote to main, and release a signed APK after installation verification. Do not infer deployment or a working login from passing static tests alone.
