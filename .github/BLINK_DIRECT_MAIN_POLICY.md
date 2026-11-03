# BLINK: verified PRs directly to main (Testlab retired)

From the date this change is merged, **main** remains the protected production branch.
Do **not** push unverified changes to main or create new work on the retired
`Testlab` branch.

1. Create a short-lived `feature/*`, `fix/*` or `chore/*` branch **from
   the current main commit**. Preserve all newer production behavior.
2. Open a PR directly into `main`. Do not bypass the required branch rules:
   `validate`, `migration-safety`, `guard`, `windows-build` and `parity`.
3. Repair failed checks on the feature branch and rerun them. Never certify an
   older commit as evidence for the latest head. Require staging and real-device
   evidence when the change touches those areas.
4. To opt a fully verified internal PR into automatic merge, add label
   `auto-promote` and include exactly
   `<!-- BLINK_AGENT_VERIFIED_SHA: <current 40-character head sha> -->`
   in the PR body. `staging-verified` and `device-verified` are required for
   applicable changes. A subsequent push invalidates the authorization.
5. The trusted main-only controller checks the latest main ancestry, all
   relevant GitHub Actions, all changed files and review holds before merging.
   It dispatches relevant production workflows after a bot merge. A failing
   or missing check **blocks** the merge.
6. Never place database migrations, payment changes, private media policies or
   signing/release changes in production without appropriate independent
   staging verification. For large changes, continue using isolated feature
   branches and PRs, not unreviewed direct pushes.

The old `Testlab` tip was archived at
`archive/Testlab-before-promotion-20261025` at commit
`1ecc1857583fdb6860ed6fc80ec99859e67cbe98`.
`retire-testlab-branch.yml` is a one-time main-run cleanup: it deletes only
the exact obsolete Testlab ref if the backup is intact and there is no
open PR using it. If repository rules deny deletion, they must be addressed
in GitHub settings; **never force-push, delete the backup or bypass protections**.
The obsolete scheduled Testlab batch integration workflow is retired.
