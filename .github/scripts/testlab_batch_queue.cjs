/**
 * BLINK automatic verified-PR batching controller. Runs ONLY from trusted main.
 *
 * The GitHub API creates merge commits on an isolated Testlab-batch-* branch,
 * never executes untrusted PR code with a write token, and leaves conflicting
 * or unverified PRs untouched. GITHUB_TOKEN PR creation does not start Actions,
 * so we explicitly dispatch the existing read-only quality gates on the batch SHA.
 */
'use strict';

const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const QUEUE = 'batch-testlab'; // Legacy optional priority flag, not required.
const MAX_BATCH = 10;
const MAX_SCAN = 60;
const ACTIVE_DAYS = 30;
const BLOCKED_LABELS = new Set([
  'do-not-merge', 'do-not-batch', 'hold', 'blocked', 'wip',
  'needs-review', 'needs-staging', 'needs-device-verification',
]);
const promo = require('./testlab_auto_promotion.cjs');

const WORKFLOW = Object.freeze({
  validate: 'android-build-check.yml',
  guard: 'secret-leak-guard.yml',
  'migration-safety': 'supabase-safety-check.yml',
  parity: 'windows-desktop-check.yml',
  'windows-build': 'windows-desktop-check.yml',
  integrity: 'apk-delivery-check.yml',
  'runtime-smoke': 'android-runtime-smoke.yml',
  'release-smoke': 'release-smoke-test.yml',
  'web-parity': 'web-parity-test.yml',
  verify: 'domain-integration-check.yml',
});

const repoArgs = {owner: OWNER, repo: REPO};
const labels = p => new Set((p.labels || []).map(x => x.name));
const isBatch = p => p.base?.ref === 'main' &&
  /^Testlab-batch-[0-9]+$/.test(p.head?.ref || '') &&
  p.head?.repo?.full_name === OWNER + '/' + REPO;

function isCandidate(pr, now = Date.now()) {
  const age = now - Date.parse(pr.updated_at || '');
  const title = pr.title || '';
  const body = pr.body || '';
  const tags = labels(pr);
  return pr.state === 'open' && !pr.draft && pr.base?.ref === 'main' &&
    pr.head?.repo?.full_name === OWNER + '/' + REPO &&
    !/^Testlab-batch-/.test(pr.head?.ref || '') &&
    Number.isFinite(age) && age >= 0 && age <= ACTIVE_DAYS * 86400000 &&
    !/^\s*\[(?:draft|wip|superseded(?:\s+[^\]]+)?|hold|blocked|do not merge)\]/i.test(title) &&
    !/\b(?:superseded by|do not auto-merge|do not merge|not for production|staging only|hold (?:the )?merge|must not be merged|should not be merged|this is draft|keep this draft|keep (?:it|the pr) (?:as )?draft|not ready for (?:production|merge))\b/i.test(body) &&
    !/^\s*(?:[-*+]|\d+\.)\s*\[\s\]/m.test(body) &&
    ![...tags].some(name => BLOCKED_LABELS.has(name.toLowerCase()));
}

/** Source checks may be on an older main base. Verify THIS head's checks, then
 * require full integration testing on an up-to-date Testlab batch before main.
 * Do not mistake a successful job from another PR or old SHA for verification.
 */
function sourceChecksGreen(checks, pr, files) {
  const latest = new Map();
  for (const c of checks) {
    if (c.app?.slug !== 'github-actions' || c.head_sha !== pr.head.sha ||
        !(c.pull_requests || []).some(p => p.number === pr.number)) continue;
    if (!latest.has(c.name) || c.id > latest.get(c.name).id) latest.set(c.name, c);
  }
  if (!latest.size) return 'No verifiable current-head CI checks';
  for (const name of promo.requiredChecks(files)) {
    const c = latest.get(name);
    if (!c || c.status !== 'completed' || c.conclusion !== 'success') {
      return 'Missing or unsuccessful source check: ' + name;
    }
  }
  for (const c of latest.values()) {
    if (c.status !== 'completed' ||
        ['failure', 'timed_out', 'cancelled', 'action_required', 'startup_failure'].includes(c.conclusion)) {
      return 'Source CI pending or failed: ' + c.name;
    }
  }
  return null;
}

function hasBlockingReview(reviews) {
  const latest = new Map();
  for (const review of reviews) {
    if (review.state === 'PENDING' || !review.user?.login) continue;
    const old = latest.get(review.user.login);
    if (!old || review.id > old.id) latest.set(review.user.login, review);
  }
  return [...latest.values()].some(r => r.state === 'CHANGES_REQUESTED');
}

function relevantWorkflows(paths) {
  const files = new Set(promo.requiredChecks(paths).map(check => WORKFLOW[check]));
  if (paths.some(p => /^game3d\//.test(p))) files.add('blink-life-3d-check.yml');
  if (files.has(undefined)) throw new Error('Unknown required quality gate');
  return [...files].sort();
}

function bodyWithoutStaleMarkers(body) {
  return (body || '')
    .replace(/\n?<!-- BLINK_AGENT_VERIFIED_SHA: [a-f0-9]{40} -->/g, '')
    .replace(/\n?<!-- BLINK_BATCH_DISPATCH_SHA: [a-f0-9]{40} -->/g, '')
    .trimEnd();
}

async function allFiles(github, number) {
  const data = await github.paginate(github.rest.pulls.listFiles, {
    ...repoArgs, pull_number: number, per_page: 100
  });
  return data.map(f => f.filename);
}

// The former batch-testlab label remains supported but is no longer required.


async function dispatch(github, pr, paths, core) {
  const sha = pr.head.sha;
  if ((pr.body || '').includes('<!-- BLINK_BATCH_DISPATCH_SHA: ' + sha + ' -->')) return false;
  for (const workflow_id of relevantWorkflows(paths)) {
    await github.rest.actions.createWorkflowDispatch({
      ...repoArgs, workflow_id, ref: pr.head.ref
    });
    core.info('Dispatched ' + workflow_id + ' for ' + pr.head.ref + ' @ ' + sha);
  }
  await github.rest.pulls.update({
    ...repoArgs, pull_number: pr.number,
    body: bodyWithoutStaleMarkers(pr.body) +
      '\n\n<!-- BLINK_BATCH_DISPATCH_SHA: ' + sha + ' -->'
  });
  return true;
}

async function processBatch(github, pr, core) {
  // A newer main always invalidates previous results; never reuse CI from
  // a different integration baseline.
  const comparison = (await github.rest.repos.compareCommits({
    ...repoArgs, base: 'main', head: pr.head.sha
  })).data;
  if (comparison.behind_by > 0) {
    // Remove stale opt-in *before* touching the code. A GITHUB_TOKEN update
    // will not fire a pull_request_target synchronize event.
    for (const name of ['auto-promote', 'staging-verified', 'device-verified']) {
      if (!labels(pr).has(name)) continue;
      await github.rest.issues.removeLabel({
        ...repoArgs, issue_number: pr.number, name
      });
    }
    await github.rest.pulls.update({
      ...repoArgs, pull_number: pr.number,
      body: bodyWithoutStaleMarkers(pr.body)
    });
    try {
      await github.rest.repos.merge({
        ...repoArgs, base: pr.head.ref, head: 'main',
        commit_message: 'Update combined Testlab batch against current main'
      });
      core.info('Updated Testlab batch #' + pr.number + ' to latest main');
    } catch (error) {
      if (error.status === 409) {
        core.warning('Batch #' + pr.number + ' conflicts with current main; no production merge');
        return;
      }
      throw error;
    }
    return; // Check head SHA on the next scheduled run.
  }

  const files = await allFiles(github, pr.number);
  if (pr.changed_files > files.length || !files.length) {
    core.warning('Batch file list is incomplete; refusing promotion');
    return;
  }
  // Explicitly run workflow_dispatch checks (a PR made with GITHUB_TOKEN
  // cannot automatically trigger a pull_request build).
  if (await dispatch(github, pr, files, core)) return;

  const required = promo.requiredChecks(files);
  const checks = await github.paginate(github.rest.checks.listForRef, {
    ...repoArgs, ref: pr.head.sha, per_page: 100, filter: 'latest'
  });
  const result = promo.checkGate(checks, required, pr.number,
    pr.base.sha, pr.head.sha, {allowDispatchForBatch: true});
  if (result) { core.info('Batch #' + pr.number + ': ' + result); return; }

  if (promo.filePathsRequireStaging(files) && !labels(pr).has('staging-verified')) {
    core.warning('Batch #' + pr.number +
      ' needs genuine staging verification of the combined revision');
    return;
  }
  if (promo.requiresDeviceVerification(files, labels(pr)) &&
      !labels(pr).has('device-verified')) {
    core.warning('Batch #' + pr.number +
      ' needs independent real-device evidence for combined revision');
    return;
  }
  if (pr.mergeable !== true || pr.mergeable_state === 'dirty') {
    core.warning('Batch #' + pr.number + ' not mergeable; leave main unchanged');
    return;
  }
  // Confirm immutable SHA / base right before releasing the candidate.
  const fresh = (await github.rest.pulls.get({...repoArgs,
    pull_number: pr.number})).data;
  const current = (await github.rest.repos.getBranch({
    ...repoArgs, branch: 'main'})).data.commit.sha;
  if (fresh.head.sha !== pr.head.sha || fresh.base.sha !== current) {
    core.info('Batch changed after testing; waiting for latest revision');
    return;
  }
  const marker = '<!-- BLINK_AGENT_VERIFIED_SHA: ' + fresh.head.sha + ' -->';
  if (!(fresh.body || '').includes(marker)) {
    await github.rest.pulls.update({
      ...repoArgs, pull_number: pr.number,
      body: bodyWithoutStaleMarkers(fresh.body) +
        '\n\n<!-- BLINK_BATCH_DISPATCH_SHA: ' + fresh.head.sha + ' -->\n' + marker
    });
  }
  if (!labels(fresh).has('auto-promote')) {
    // Existing trusted Testlab promotion controller does the final check
    // and is the *only* code allowed to merge or start production releases.
    await github.rest.issues.addLabels({
      ...repoArgs, issue_number: pr.number, labels: ['auto-promote']
    });
    core.info('Batch #' + pr.number + ' green at ' + fresh.head.sha +
      '; enrolled in existing fail-closed Testlab -> main auto-promotion');
  }
}

async function selectVerified(github, candidates, core) {
  const ready = [];
  for (const summary of candidates.slice(0, MAX_SCAN)) {
    if (ready.length >= MAX_BATCH) break;
    const pr = (await github.rest.pulls.get({
      ...repoArgs, pull_number: summary.number
    })).data;
    if (!isCandidate(pr)) continue;
    const files = await allFiles(github, pr.number);
    if (!files.length || pr.changed_files > files.length) {
      core.info('Skipping #' + pr.number + ': incomplete changed-file list');
      continue;
    }
    // A write-enabled dispatcher must not execute altered workflow, action
    // or CI helper code supplied by another PR.
    if (files.some(p => /^\.github\/(?:workflows\/|actions\/|scripts\/)/.test(p) ||
        /^scripts\/(?:ci_change_scope\.py|check_[^/]+|test_[^/]+)$/.test(p))) {
      core.info('Skipping #' + pr.number + ': CI policy or testing changes require separate review');
      continue;
    }
    if (promo.filePathsRequireStaging(files) && !labels(pr).has('staging-verified')) {
      core.info('Skipping #' + pr.number + ': hosted staging evidence missing');
      continue;
    }
    if (promo.requiresDeviceVerification(files, labels(pr)) &&
        !labels(pr).has('device-verified')) {
      core.info('Skipping #' + pr.number + ': device evidence missing');
      continue;
    }
    const checks = await github.paginate(github.rest.checks.listForRef, {
      ...repoArgs, ref: pr.head.sha, per_page: 100, filter: 'latest'
    });
    const reason = sourceChecksGreen(checks, pr, files);
    if (reason) { core.info('Skipping #' + pr.number + ': ' + reason); continue; }
    const reviews = await github.paginate(github.rest.pulls.listReviews, {
      ...repoArgs, pull_number: pr.number, per_page: 100
    });
    if (hasBlockingReview(reviews)) {
      core.info('Skipping #' + pr.number + ': requested review changes unresolved');
      continue;
    }
    ready.push({pr, files});
    core.info('Eligible PR #' + pr.number + ': current-head CI green; will retest against main');
  }
  return ready;
}

async function makeBatch(github, candidates, context, core) {
  // Select + check source PRs before creating the integration branch so
  // empty/noncompliant PR collections cannot generate repeated empty branches.
  const approved = await selectVerified(github, candidates, core);
  if (!approved.length) {
    core.info('No recent, green, ready-for-production PRs found; no changes made');
    return;
  }
  const main = (await github.rest.repos.getBranch({
    ...repoArgs, branch: 'main'})).data.commit.sha;
  const branch = 'Testlab-batch-' + context.runId;
  await github.rest.git.createRef({
    ...repoArgs, ref: 'refs/heads/' + branch, sha: main
  });
  const added = [];
  const allChangedPaths = new Set();
  for (const {pr, files} of approved) {
    // Avoid a TOCTOU merge of newer untested source commits.
    const latest = (await github.rest.pulls.get({
      ...repoArgs, pull_number: pr.number
    })).data;
    if (!isCandidate(latest) || latest.head.sha !== pr.head.sha ||
        latest.base.ref !== 'main') {
      core.warning('Skipping #' + pr.number + ': PR changed after source verification');
      continue;
    }
    try {
      const result = await github.rest.repos.merge({
        ...repoArgs, base: branch, head: pr.head.sha,
        commit_message: 'Testlab integrate PR #' + pr.number + ': ' +
          (pr.title || '').slice(0, 90)
      });
      if (result.status === 204) {
        core.info('Skipping #' + pr.number + ': already present in main or prior batch');
        continue;
      }
      added.push(pr.number);
      for (const path of files) allChangedPaths.add(path);
      core.info('Safely combined PR #' + pr.number + ' without executing its code');
    } catch (error) {
      if (error.status === 409) {
        core.warning('PR #' + pr.number + ': merge conflict, left open and untouched');
        continue;
      }
      throw error;
    }
  }
  if (!added.length) {
    await github.rest.git.deleteRef({...repoArgs, ref: 'heads/' + branch});
    core.info('No compatible PRs; removed empty Testlab branch');
    return;
  }
  const body = [
    '## Automatically discovered, verified Testlab integration batch',
    'Included source PRs: ' + added.map(n => '#' + n).join(', '),
    'Built from main ' + main + '.',
    '',
    'Source PRs were current-head CI green; all combined checks must also pass.',
    'Source PRs remain open until safe production integration.',
    'Conflicts are never auto-resolved by choosing one side.',
    'Risky changes require independent staging/device verification.',
    'The trusted main-branch controller must reverify and promote; no bypass.',
  ].join('\n');
  const response = await github.rest.pulls.create({
    ...repoArgs, title: 'Testlab: verified PR batch (' +
      added.map(n => '#' + n).join(', ') + ')',
    head: branch, base: 'main', body, draft: false
  });
  const pr = response.data;
  core.info('Opened Testlab batch PR #' + pr.number + ' for ' + added.join(', '));
  await dispatch(github, pr, [...allChangedPaths], core);
}

async function run({github, context, core}) {
  if (context.eventName === 'pull_request_target' &&
      !['opened', 'reopened', 'synchronize', 'ready_for_review', 'labeled'].includes(
        context.payload.action)) return;

  const list = await github.paginate(github.rest.pulls.list, {
    ...repoArgs, state: 'open', base: 'main', per_page: 100
  });
  const existing = list.filter(isBatch).sort((a, b) => b.number - a.number);
  if (existing.length) {
    // One batch at a time prevents overlapping rollouts and protects main.
    await processBatch(github, (await github.rest.pulls.get({
      ...repoArgs, pull_number: existing[0].number
    })).data, core);
    return;
  }
  // Array.filter passes the array index as the second argument: never
  // accidentally treat that index as the current timestamp.
  const eligible = list.filter(pr => isCandidate(pr))
    // Examine newest active work first; old abandoned PRs should not
    // automatically become production releases.
    .sort((a, b) => (b.updated_at || '').localeCompare(a.updated_at || ''));
  if (!eligible.length) {
    core.info('No recent, ready, internal main-targeting PRs found');
    return;
  }
  await makeBatch(github, eligible, context, core);
}

module.exports = run;
module.exports.isCandidate = isCandidate;
module.exports.isBatch = isBatch;
module.exports.sourceChecksGreen = sourceChecksGreen;
module.exports.hasBlockingReview = hasBlockingReview;
module.exports.selectVerified = selectVerified;
module.exports.relevantWorkflows = relevantWorkflows;
module.exports.bodyWithoutStaleMarkers = bodyWithoutStaleMarkers;
