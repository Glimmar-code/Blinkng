#!/usr/bin/env python3
"""Fail-closed static contract audit. No API calls, credentials or production data."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
project = (root / "ios/project.yml").read_text()
settings = (root / "ios/BLINKiOS/Sources/Calls/BLINKVoIPConfiguration.swift").read_text()
registry = (root / "ios/BLINKiOS/Sources/Calls/BLINKVoIPPushManager.swift").read_text()
auth = (root / "ios/BLINKiOS/Sources/Auth/BLINKAuthStore.swift").read_text()
migration = (root / "supabase/migrations/20261021110000_ios_voip_tokens_and_dispatch.sql").read_text().lower()
register = (root / "supabase/functions/ios-voip-register/index.ts").read_text()
dispatch = (root / "supabase/functions/ios-voip-dispatch/index.ts").read_text()
existing_android = (root / "supabase/functions/send-call-notification/index.ts").read_text()
function_config = (root / "supabase/config.toml").read_text()

checks = {
    "VoIP client OFF by default": 'BLINK_VOIP_PUSH_ENABLED: "NO"' in project,
    "APNs environment not guessed": 'BLINK_APNS_ENVIRONMENT: "unconfigured"' in project,
    "VoIP release flag checked": 'enabledInBuild' in settings and 'BLINK_VOIP_PUSH_ENABLED' in settings,
    "PushKit startup is gated": "guard BLINKVoIPConfiguration.enabledInBuild else" in registry,
    "Registration server disabled unless explicitly enabled": 'Deno.env.get("BLINK_IOS_VOIP_SERVER_ENABLED") !== "true"' in register,
    "Dispatch server double-disabled unless enabled": 'Deno.env.get("BLINK_IOS_VOIP_DISPATCH_ENABLED") !== "true"' in dispatch,
    "Registration verifies JWT server side": 'admin.auth.getUser(jwt)' in register,
    "Registration includes explicit revocation": 'action === "revoke"' in register,
    "iOS signout attempts revocation first": "revokeForSignOut(userJWT: token)" in auth,
    "APNs sends only true VoIP": '"apns-push-type": "voip"' in dispatch,
    "APNs sender checks real ringing calls": 'call.status !== "ringing"' in dispatch,
    "APNs sender checks blocks": '.from("blocks")' in dispatch,
    "APNs sender gated behind strong internal header": '"x-blink-ios-dispatch-secret"' in dispatch,
    "APNs sender per-device dedupe": '.from("ios_voip_dispatches").insert' in dispatch,
    "Call dispatcher only invoked with double flag": 'Deno.env.get("BLINK_IOS_VOIP_DISPATCH_ENABLED") === "true"' in existing_android,
    "Token table RLS enabled": "alter table public.ios_voip_tokens enable row level security" in migration,
    "No client token grants": "revoke all on public.ios_voip_tokens from public, anon, authenticated" in migration,
    "Token table only grants server access": "grant select, insert, update, delete on public.ios_voip_tokens to service_role" in migration,
    "Dispatch table RLS enabled": "alter table public.ios_voip_dispatches enable row level security" in migration,
    "Dispatch denies direct client access": "revoke all on public.ios_voip_dispatches from public, anon, authenticated" in migration,
    "Registration requires Supabase gateway JWT": '[functions.ios-voip-register]\nverify_jwt = true' in function_config,
    "Dispatcher uses private service auth instead of client JWT": '[functions.ios-voip-dispatch]\nverify_jwt = false' in function_config,
}
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + " " + name)
if not all(checks.values()):
    raise SystemExit(1)
print(f"BLINK iOS VoIP audit: {len(checks)} static checks passed. Real APNs delivery NOT verified.")
