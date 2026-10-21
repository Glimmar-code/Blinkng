/**
 * Fail-closed BLINK Testlab -> main promotion controller.
 *
 * Runs only from the trusted default branch in pull_request_target/schedule.
 * Never checks out or executes code from a pull request.
 * A PR is eligible only when its owner explicitly applies "auto-promote",
 * marks it ready for review, completes its checklist, and passes every gate.
 */
'use strict';

const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const OPT_IN = 'auto-promote';
const STAGING = 'staging-verified';
const DEVICE_REQUIRED = 'needs-device-verification';
const DEVICE_VERIFIED = 'device-verified';
const APPROVE = 'BLINK APPROVE ';
const REVOKE = 'BLINK REVOKE ';
const BASE_CHECKS = ['validate', 'guard', 'migration-safety', 'parity', 'windows-build', 'integrity'];

/**
 * Only a deliberately small allowlist can skip per-commit owner authorization.
 * Unknown paths and executable code fail closed, even when described as "UI".
 * The opt-in label, complete CI, and branch rules still apply to routine PRs.
 */
function isRoutineFile(path) {
  return /^(?:README|CHANGELOG|CONTRIBUTING)\.md$/i.test(path) ||
    /^docs\/[\w.\/-]+\.md$/i.test(path) ||
    /^web\/[\w.-]+\.css$/i.test(path) ||
    /^web\/[\w./-]+\.(?:png|jpe?g|webp)$/i.test(path) ||
    /^app\/src\/main\/res\/(?:drawable|mipmap)(?:-[\w]+)?\/[\w.-]+\.(?:png|jpe?g|webp)$/i.test(path);
}
function requiresOwnerApproval(files) {
  return !Array.isArray(files) || files.length === 0 || !files.every(isRoutineFile);
}

/**
 * OEM hardware, push delivery, native media, and installer behaviors cannot
 * be established by an Android emulator alone. A real-device or independent
 * hosted device-lab report is required; it must not require the owner to
 * repeatedly download a candidate APK onto a personal phone.
 */
function requiresDeviceVerification(files, labels = new Set()) {
  return labels.has(DEVICE_REQUIRED) || files.some(path =>
    /^app\/src\/main\/jniLibs\//i.test(path) ||
    /^app\/src\/main\/java\/.*(?:\/call\/|\/calling\/|\/webrtc\/|\/telecom\/|\/updater\/|\/installer\/|\/notifications?\/)/i.test(path) ||
    /^app\/src\/main\/java\/.*(?:Call|WebRTC|WebRtc|Telecom|Bluetooth|AudioRoute|InAppUpdate|ApkUpdate|ApkInstall|PushNotification)[\w]*\.kt$/i.test(path)
  );
}

function filePathsRequireStaging(files) {
  return files.some(path =>
    /^supabase\/(?:migrations\/|functions\/|config\.toml$)/.test(path) ||
    /^app\/proguard-rules\.pro$/.test(path) ||
    /^\.github\/(?:workflows\/(?:signed-release|publish-latest-apk|auto-promote-testlab)\.yml|scripts\/(?:publish_verified_apk|testlab_auto_promotion)\.(?:py|cjs))$/.test(path)
  );
}

function requiredChecks(files) {
  const result = new Set(BASE_CHECKS);
  const android = files.some(path =>
    /^app\/src\//.test(path) || /^shared\/src\//.test(path) ||
    /^app\/build\.gradle\.kts$/.test(path) ||
    /^(?:build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties)$/.test(path) ||
    /^gradle\//.test(path)
  );
  const release = android || files.some(path =>
    /^app\/(?:proguard-rules\.pro|google-services\.json\.example)$/.test(path) ||
    /^\.github\/(?:scripts\/restore_google_services\.sh|workflows\/(?:signed-release|release-smoke-test)\.yml)$/.test(path)
  );
  const web = files.some(path => /^web\//.test(path) ||
    /^\.github\/workflows\/web-parity-test\.yml$/.test(path));
  const domain = web || files.some(path =>
    /^app\/src\/main\/AndroidManifest\.xml$/.test(path) ||
    /^app\/src\/main\/java\/com\/example\/(?:MainActivity\.kt|ui\/screens\/VideoReelsScreen\.kt|sharing\/|.*)$/.test(path) &&
      (/MainActivity\.kt$|VideoReelsScreen\.kt$|\/sharing\//.test(path)) ||
    /^\.github\/workflows\/(?:domain-integration-check|deploy-web-preview)\.yml$/.test(path)
  );
  if (android) result.add('runtime-smoke');
  if (release) result.add('release-smoke');
  if (web) result.add('web-parity');
  if (domain) result.add('verify');
  if (files.some(path =>
    /^\.github\/scripts\/.*verified_apk\.py$/.test(path) ||
    /^\.github\/workflows\/(?:publish-latest-apk|apk-delivery-check)\.yml$/.test(path)
  )) result.add('integrity');
  return [...result];
}

function releaseWorkflows(files) {
  const toRun = [];
  if (files.some(path =>
    /^(?:app\/|shared\/|gradle\/)/.test(path) ||
    /^(?:build\.gradle\.kts|settings\.gradle\.kts|gradle\.properties)$/.test(path)
  )) toRun.push('signed-release.yml');
  if (files.some(path =>
    /^web\//.test(path) ||
    /^\.github\/workflows\/deploy-web-preview\.yml$/.test(path) ||
    /^\.github\/scripts\/restore_google_services\.sh$/.test(path) ||
    /^scripts\/sync_android_launcher_assets\.py$/.test(path)
  )) toRun.push('deploy-web-preview.yml');
  return toRun;
}

function preflight(pr, changedFiles) {
  if (!pr || pr.state !== 'open' || pr.draft) return 'PR is not open and ready for review';
  if (pr.base?.ref !== 'main') return 'PR does not target main';
  if (pr.head?.repo?.full_name !== OWNER + '/' + REPO) return 'PR head is not an internal Testlab branch';
  if (!/^Testlab(?:-|$)/.test(pr.head?.ref || '')) return 'PR head must be Testlab or Testlab-*';
  const labels = new Set((pr.labels || []).map(label => label.name));
  if (!labels.has(OPT_IN)) return 'No auto-promote opt-in label';
  if (/^\s*(?:[-*+]|\d+\.)\s*\[\s\]/m.test(pr.body || '')) return 'Unfinished task checklist';
  if (/\bdo not auto-merge\b/i.test(pr.body || '')) return 'PR explicitly prohibits auto-merge';
  if (filePathsRequireStaging(changedFiles) && !labels.has(STAGING)) return 'Staging verification is not attested';
  if (requiresDeviceVerification(changedFiles, labels) && !labels.has(DEVICE_VERIFIED)) return 'Independent physical-device verification is not attested';
  if (pr.mergeable !== true || pr.mergeable_state === 'dirty') return 'PR is not cleanly mergeable';
  if ((pr.changed_files || 0) > changedFiles.length) return 'Could not examine every changed file';
  return null;
}

/**
 * An opt-in label is NOT authorization to ship. BLINK's repository owner must
 * explicitly approve the exact current commit SHA in a PR comment.
 *
 * GitHub does not permit PR authors to approve their own PRs via a review, so
 * this exact owner-authored command supports single-maintainer releases.
 * A new push changes the SHA and invalidates all previous approvals.
 * The latest owner command for that SHA wins (including revocation).
 */
function approvalGate(comments, headSha) {
  if (!/^[a-f0-9]{40}$/.test(headSha || '')) {
    return 'Invalid release head SHA';
  }
  let decision = null;
  for (const comment of [...(comments || [])].sort((a, b) => a.id - b.id)) {
    if (comment.user?.login !== OWNER) continue;
    const body = (comment.body || '').trim();
    if (body === APPROVE + headSha) decision = 'approved';
    if (body === REVOKE + headSha) decision = 'revoked';
  }
  if (decision === 'revoked') return 'Repository owner revoked this exact release';
  if (decision !== 'approved') {
    return 'Explicit owner approval missing for current SHA: ' + APPROVE + headSha;
  }
  return null;
}

function checkGate(checks, required, number, baseSha, headSha) {
  // Checks can be rerun. Use the newest run of each job, never an old success.
  const latest = new Map();
  for (const check of checks) {
    if (check.app?.slug !== 'github-actions') continue;
    if (!(check.pull_requests || []).some(p =>
      p.number === number && p.base?.sha === baseSha && p.head?.sha === headSha)) continue;
    if (!latest.has(check.name) || check.id > latest.get(check.name).id) {
      latest.set(check.name, check);
    }
  }
  for (const name of required) {
    const found = latest.get(name);
    if (!found || found.status !== 'completed' || found.conclusion !== 'success') {
      return 'Required CI gate not successful: ' + name;
    }
  }
  for (const check of latest.values()) {
    if (check.status !== 'completed') return 'CI still running: ' + check.name;
    if (['failure', 'timed_out', 'cancelled', 'action_required', 'startup_failure'].includes(check.conclusion)) {
      return 'Another CI job failed: ' + check.name;
    }
  }
  return null;
}

async function ensureLabel(github, name, color, description) {
  const args = {owner: OWNER, repo: REPO, name};
  try {
    await github.rest.issues.getLabel(args);
  } catch (error) {
    if (error.status !== 404) throw error;
    await github.rest.issues.createLabel({...args, color, description});
  }
}

async function run({github, context, core}) {
  // Labels are bootstrapped by the trusted main workflow; no manual setup.
  await ensureLabel(github, OPT_IN, '0e8a16', 'Merge when Testlab checklist and all CI pass');
  await ensureLabel(github, STAGING, '5319e7', 'Staging validation verified for backend or release changes');
  await ensureLabel(github, DEVICE_REQUIRED, 'd73a4a', 'This PR needs independent physical-device or hosted device-lab testing');
  await ensureLabel(github, DEVICE_VERIFIED, '0e8a16', 'Independent physical-device acceptance evidence reviewed on current SHA');

  let candidates;
  if (context.eventName === 'pull_request_target') {
    const number = context.payload.pull_request?.number;
    if (!number) return;
    candidates = [{number}];
  } else {
    // Work even if GitHub loses a webhook or CI completes after the PR event.
    candidates = await github.paginate(github.rest.pulls.list, {
      owner: OWNER, repo: REPO, state: 'open', base: 'main', per_page: 100
    });
    candidates = candidates.filter(pr => (pr.labels || []).some(label => label.name === OPT_IN));
  }

  const errors = [];
  for (const candidate of candidates) {
    const number = candidate.number;
    try {
      const {data: pr} = await github.rest.pulls.get({owner: OWNER, repo: REPO, pull_number: number});
      if (!(pr.labels || []).some(label => label.name === OPT_IN)) continue;
      const files = await github.paginate(github.rest.pulls.listFiles, {
        owner: OWNER, repo: REPO, pull_number: number, per_page: 100
      });
      const paths = files.map(file => file.filename);
      const reason = preflight(pr, paths);
      if (reason) { core.info('#' + number + ': ' + reason); continue; }

      const {data: main} = await github.rest.repos.getBranch({owner: OWNER, repo: REPO, branch: 'main'});
      if (pr.base.sha !== main.commit.sha) {
        core.info('#' + number + ': base changed; wait for integration refresh'); continue;
      }
      const {data: comparison} = await github.rest.repos.compareCommits({
        owner: OWNER, repo: REPO, base: 'main', head: pr.head.sha
      });
      if (comparison.behind_by !== 0 || comparison.ahead_by < 1) {
        core.info('#' + number + ': Testlab branch must incorporate latest main'); continue;
      }

      const reviews = await github.paginate(github.rest.pulls.listReviews, {
        owner: OWNER, repo: REPO, pull_number: number, per_page: 100
      });
      const byReviewer = new Map();
      for (const review of reviews) if (review.state !== 'PENDING') {
        const user = review.user?.login;
        if (user && (!byReviewer.has(user) || review.id > byReviewer.get(user).id)) {
          byReviewer.set(user, review);
        }
      }
      if ([...byReviewer.values()].some(review => review.state === 'CHANGES_REQUESTED')) {
        core.info('#' + number + ': unresolved request for changes'); continue;
      }

      const checks = await github.paginate(github.rest.checks.listForRef, {
        owner: OWNER, repo: REPO, ref: pr.head.sha, per_page: 100, filter: 'latest'
      });
      const fail = checkGate(checks, requiredChecks(paths), number, pr.base.sha, pr.head.sha);
      if (fail) { core.info('#' + number + ': ' + fail); continue; }

      // Verify final human approval only after all checks have passed, and
      // immediately before the final PR/main consistency check and merge.
      if (requiresOwnerApproval(paths)) {
        const comments = await github.paginate(github.rest.issues.listComments, {
          owner: OWNER, repo: REPO, issue_number: number, per_page: 100
        });
        const approvalFailure = approvalGate(comments, pr.head.sha);
        if (approvalFailure) { core.info('#' + number + ': ' + approvalFailure); continue; }
      } else {
        core.info('#' + number + ': routine-only change; full CI and explicit auto-promote opt-in apply without per-SHA owner comment');
      }

      const {data: current} = await github.rest.pulls.get({owner: OWNER, repo: REPO, pull_number: number});
      const {data: finalMain} = await github.rest.repos.getBranch({owner: OWNER, repo: REPO, branch: 'main'});
      if (current.head.sha !== pr.head.sha || current.base.sha !== pr.base.sha ||
          finalMain.commit.sha !== main.commit.sha || preflight(current, paths)) {
        core.info('#' + number + ': PR or main changed during verification; try next run'); continue;
      }

      const merged = await github.rest.pulls.merge({
        owner: OWNER, repo: REPO, pull_number: number, sha: pr.head.sha,
        merge_method: 'squash', commit_title: 'Promote verified Testlab PR #' + number
      });
      if (!merged.data.merged) throw new Error('GitHub did not confirm the merge');
      core.info('Promoted verified Testlab PR #' + number + ' into main');

      // GITHUB_TOKEN merges do NOT trigger downstream push workflows. Dispatch
      // production jobs explicitly to keep releases/website in sync with main.
      for (const workflow_id of releaseWorkflows(paths)) {
        try {
          await github.rest.actions.createWorkflowDispatch({
            owner: OWNER, repo: REPO, workflow_id, ref: 'main'
          });
          core.info('Dispatched ' + workflow_id + ' after merging #' + number);
        } catch (error) {
          errors.push('PR #' + number + ' merged, but ' + workflow_id +
            ' could not start: ' + error.message);
        }
      }
    } catch (error) {
      errors.push('PR #' + number + ': ' + error.message);
    }
  }
  if (errors.length) core.setFailed(errors.join('\n'));
}

module.exports = run;
module.exports.preflight = preflight;
module.exports.isRoutineFile = isRoutineFile;
module.exports.requiresOwnerApproval = requiresOwnerApproval;
module.exports.requiresDeviceVerification = requiresDeviceVerification;
module.exports.requiredChecks = requiredChecks;
module.exports.checkGate = checkGate;
module.exports.approvalGate = approvalGate;
module.exports.filePathsRequireStaging = filePathsRequireStaging;
module.exports.releaseWorkflows = releaseWorkflows;
