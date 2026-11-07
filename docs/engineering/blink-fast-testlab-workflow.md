# BLINK coordinated Testlab development

## One scope → one Testlab PR

1. Inspect the complete diff and affected Android, shared, Windows, database,
   website and workflow contracts before changing source.
2. Implement a cohesive batch in one feature branch; don't repeatedly push
   one-line experiments. A failed local test is a reason to fix the batch.
3. Run \`python3 scripts/blink_preflight.py --base origin/main\` before pushing.
   Use \`--full\` when Android/Windows toolchains are available. Quick mode
   checks CI policy and comparison-tool tests, not an actual app installation.
4. Push once ready for Testlab PR CI. CI selects affected expensive suites;
   never hide failed checks or reuse evidence from a changed input digest.
5. For UI changes, capture actual device/emulator screenshots. Run
   \`python3 scripts/testing/visual_reference_audit.py reference.png actual.png --out-dir build/visual-audit\`,
   inspect \`overlay.png\`, \`difference.png\`, \`report.json\`, fix differences,
   then capture again. Keep original reference and actual screenshots as
   reviewed evidence. Synthetic comparator tests cannot approve real UI.
6. Confirm Android, Windows parity, backend staging, device and security tests
   as applicable on the exact last commit. Record the actual artifact links.
7. Only after checks pass, use the existing opt-in promotion flow. The
   \`auto-promote\` label and exact-head agent approval are mandatory;
   UI surface changes additionally require \`visual-verified\` and
   \`BLINK_VISUAL_VERIFIED_SHA\`. Relevant backend and device gates stay
   mandatory. New commits invalidate approvals.

## Status report for every BLINK PR

| Field | Required evidence |
| --- | --- |
| Completed | Actual files and behavior implemented |
| Local preflight | Commands run and pass/fail on exact commit |
| CI | Required check names, latest SHA and results |
| Screenshot audit | Reference, actual, overlay, difference and report URLs |
| Staging/device | Test location/account scope and verified outcome |
| Remaining blockers | Any missing build, backend, release, design or consent |
| Main merge | Merge SHA only after complete verification |

No forced merge, production-first migration, unapproved paid staging or
fabricated screenshot match. PRs with outstanding checks stay open.
