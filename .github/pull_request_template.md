## Summary

Describe what changed and why.

## Safety checklist

- [ ] Risky work was developed/tested in `Testlab` (or a feature branch targeting `Testlab`), not directly on `main`.
- [ ] Android quality gate passes.
- [ ] Windows desktop quality/parity gate passes for user-facing changes.
- [ ] Supabase safety gate passes.
- [ ] Any Supabase schema/Auth/RLS/Storage/Edge Function/RPC/trigger/cron/webhook change was tested in preview/staging first.
- [ ] New schema changes are versioned in `supabase/migrations/`.
- [ ] Destructive database work has an explicit rollback/recovery plan.
- [ ] Allowed and denied RLS/auth paths were tested where applicable.
- [ ] No service-role key, secret key, private credential, or production test data is committed or bundled in clients.
- [ ] Relevant manual smoke tests from `docs/RELEASE_SAFETY.md` pass.
- [ ] Android and Windows feature parity is implemented or an explicit platform exception is recorded.
- [ ] Previous known-good release/commit is identified for rollback.

## Supabase promotion

- Staging/preview project or branch:
- Migrations/functions tested:
- Production promotion plan:
- Rollback/recovery plan:

## Test evidence

List automated checks, device/build tests, and any important edge cases verified.
