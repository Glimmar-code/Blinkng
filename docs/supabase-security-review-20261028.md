# BLINK Supabase access review (28 October 2026)

## Scope and evidence

The live Supabase security advisor identified 45 RLS-enabled tables with no policies and 10 anon-executable SECURITY DEFINER functions. The absence of RLS policies is **not** by itself a defect when the table is intentionally accessible only to trusted server-side code.

A read-only privilege audit found all 45 no-policy tables lacked `SELECT` for `anon` and `authenticated`; none allowed `authenticated INSERT`. Do **not** make up policies or grant blanket client access to quiet informational advisories. Some private tables are accessible only to their owner/database functions; do not grant service_role by default without dependency review.

Two of the 10 public-executable functions are trigger-only functions on `market_items` and `marketplace_orders`. The companion migration removes `PUBLIC`, `anon`, and `authenticated` EXECUTE while retaining trusted service execution and existing triggers. The isolated PostgreSQL workflow verifies this without accessing production.

The remaining eight anon-callable functions are RPCs used or potentially used in anonymous onboarding, public web pages, referral discovery, and campaign feeds:
- `blink_record_referral_link_open`
- `blink_referrer_suggestions`
- `check_profile_identity`
- `get_blink_live_campaigns`
- `get_public_web_content`
- `get_public_web_profile`
- `get_public_web_profile_theme`
- `record_public_web_view`

These require per-endpoint abuse/authorization review and client call-path testing. **Do not revoke them all blindly**; it may break account creation, public pages, or analytics.

## October 30 release obligations

- New public tables require explicit least-privilege `GRANT`/`REVOKE`, RLS and correctly scoped policies in the creating migration, verified by the existing repository permission checker.
- The repository does **not** yet track a complete `00000000000000_schema_baseline.sql`. The existing CI deliberately skips full `supabase db reset --local` and cannot certify a new-project rebuild. Provision a disposable test project, recover a safe sanitized schema baseline, then validate the *whole* historical migration sequence before claiming reset compatibility.
- Never run `supabase db reset` against production; do not change grants for all 45 no-policy tables.
- Before deployment, apply the migration in Testlab/staging and verify real market seller/order operations in addition to the isolated CI fixture; verify the live advisor again after deploying.
- Existing database tables keep their current grants after Supabase's October 30 default change.

Official guidance: https://supabase.com/docs/guides/api/securing-your-api
