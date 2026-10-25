# BLINK Pull Request Preservation Rule

## Core promise

**A failed, stale or similar PR must never be silently discarded.** Treat an unmerged PR as potentially containing unique work until its exact HEAD revision is proven preserved in the intended branch or a merged replacement. Similar titles, changed file names, green web previews, or an older PR's close status are not proof.

### Normal repair workflow

1. Read the failing check's actual logs; repair the root cause in the **same branch and PR** instead of opening another identical PR.
2. Rerun relevant Android, Windows, Supabase, security and runtime checks. A green result for an older SHA does not count for the current head.
3. Make risky changes in `Testlab` or a current `Testlab-*` branch. Preserve newer main work. Merge to main only after applicable gates pass.
4. Before consolidating multiple PRs, compare each original PR's changed files and intended behavior against the consolidated branch. Maintain a feature/commit checklist; include missing work instead of overwriting it.
5. **Do not close old PRs while a replacement is still open/unmerged**, simply because the replacement looks similar.
6. Closing an intentionally abandoned PR is permitted only when the repository owner explicitly approves dropping its outstanding changes. Keep that fact visible.

### Automated close protection

The trusted default-branch workflow `.github/workflows/pr-preserve-unmerged.yml` observes **closed but unmerged** PRs, first checks whether their changed files already match the target branch, and reopens those without verified proof. GitHub does not support a native pre-close rule for this; this is a **post-close reopen guard**, and it requires GitHub Actions permission to reopen PRs. It never merges or deletes anything.

For an unmerged PR #123 at HEAD SHA `<sha>`, one of the following is needed **before closing**:

* **Preserved via replacement:** first merge replacement PR #456 into the original target branch or `main`. The repository owner posts the exact standalone comment: `BLINK SUPERSEDED BY #456 HEAD <sha>`.
* **Already on target:** exact matching source/target file blobs are recognized automatically. An optional owner comment is `BLINK PRESERVED ON BASE #123 HEAD <sha>`.
* **Intentionally dropped:** repository owner must explicitly comment `BLINK ABANDON #123 HEAD <sha>`.

The source HEAD must be the *current* exact SHA (40 hex characters). Owner-only, SHA-bound standalone comments prevent another agent merely saying "superseded"; labels can be used for organization but are not required. For preserved cases, the guard independently verifies that **every changed source file (including deletions and renames) has identical file contents** at the referenced merged commit or the current target branch tip. If files evolved and are semantically equivalent but not byte-identical, the guard conservatively reopens the PR: manually reconcile and record that work rather than falsely claiming an automated verification.

**Caveats:** automated blob comparison is conservative, not a full behavioral proof. This workflow does not prevent deletion of branches, user-authorized abandonment, or a code change overwriting work after a merge. It cannot repair CI by itself. The job only becomes active once merged into the default branch; a workflow error or permissions issue can prevent reopening, so monitor its runs.

### Agent checklist before closing any unmerged PR

Record the old PR number and exact head SHA; linked replacement PR / verified target ref; full file-by-file comparison and any intentionally omitted behavior; relevant current-SHA checks; and the reason for closure. Never delete a source branch while the preservation audit or code recovery is incomplete.
