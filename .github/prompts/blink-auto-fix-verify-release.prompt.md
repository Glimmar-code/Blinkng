---
description: BLINK auto-fix, verify, Testlab, main, signed APK release
---

# BLINK — Auto-fix, test, merge and release

Use the canonical project policy in [docs/BLINK_AUTO_FIX_VERIFY_RELEASE.md](../../docs/BLINK_AUTO_FIX_VERIFY_RELEASE.md), [AGENTS.md](../../AGENTS.md), and [.github/copilot-instructions.md](../copilot-instructions.md).

**Owner command:** When all required checks pass, push fixes to Testlab, merge tested changes to main and publish the updated signed app. If errors remain, inspect logs, fix the real root cause and reverify; repeat within the active coding session until all required checks pass or report a real blocker.

1. Inspect current commits/CI, reproduce the failure, fix source and tests for both Android and Windows where relevant.
2. Run all required build, security, SQL/RLS, integration, native/game, and release checks; never fake a pass.
3. Push to a feature branch and merge to Testlab only when its checks are green; verify the Testlab merge.
4. Before main, prove real backend/provider/device integration where required. Use no paid staging infrastructure without separate approval. If unavailable, STOP; do not use production for first-time experiments.
5. Promote a **narrow verified PR**, not all experimental Testlab changes, to main only after checks are green.
6. Verify signed production APK build and publication, and link actual release. GitHub publication alone is **not** an automatic install/update on users' devices.
7. Report exact outcomes, errors, fixes, remaining blockers, PR and release links.

**BLINK-specific constraint:** Leave Supabase Auth signup confirmation email settings untouched. Do not commit tokens or secrets. Do not weaken production access controls.
