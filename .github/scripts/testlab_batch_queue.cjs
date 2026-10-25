/**
 * BLINK opt-in PR batching controller. Runs ONLY from trusted main.
 *
 * The GitHub API creates merge commits on an isolated Testlab-batch-* branch,
 * never executes untrusted PR code with a write token, and leaves conflicting
 * or unverified PRs untouched. GITHUB_TOKEN PR creation does not start Actions,
 * so we explicitly dispatch the existing read-only quality gates on the batch SHA.
 */
'use strict';

const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const QUEUE = 'batch-testlab';
const MAX_BATCH = 10;
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

function isCandidate(pr) {
  return pr.state === 'open' && !pr.draft && pr.base?.ref === 'main' &&
    pr.head?.repo?.full_name === OWNER + '/' + REPO &&
    !/^Testlab-batch-/.test(pr.head?.ref || '') &&
    labels(pr).has(QUEUE) &&
    !/\bdo not auto-merge\b/i.test(pr.body || '');
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

async function ensureQueueLabel(github) {
  try {
    await github.rest.issues.getLabel({...repoArgs, name: QUEUE});
  } catch (error) {
    if (error.status !== 404) throw error;
    await github.rest.issues.createLabel({
      ...repoArgs, name: QUEUE, color: '0366d6',
      description: 'Owner opt-in: combine this PR with other compatible changes in an isolated Testlab batch'
    });
  }
}

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

async function makeBatch(github, candidates, context, core) {
  const main = (await github.rest.repos.getBranch({
    ...repoArgs, branch: 'main'})).data.commit.sha;
  const branch = 'Testlab-batch-' + context.runId;
  // Create the isolated branch only when an opted-in source PR exists.
  await github.rest.git.createRef({
    ...repoArgs, ref: 'refs/heads/' + branch, sha: main
  });
  const added = [];
  const allChangedPaths = new Set();
  try {
    for (const pr of candidates) {
      const files = await allFiles(github, pr.number);
      if (!files.length || pr.changed_files > files.length) {
        core.warning('Skipping #' + pr.number + ': incomplete changed-file list');
        continue;
      }
      // workflow_dispatch runs code from the candidate ref. Refuse changes
      // to CI/workflow controllers entirely so queued source PRs cannot edit
      // their own privileged testing instructions before release review.
      if (files.some(p => /^\.github\/(?:workflows\/|actions\/|scripts\/)/.test(p))) {
        core.warning('Skipping #' + pr.number + ': CI controller changes require a separate reviewed PR');
        continue;
      }
      if (promo.filePathsRequireStaging(files) && !labels(pr).has('staging-verified')) {
        core.info('Skipping #' + pr.number + ': staged backend/release evidence missing');
        continue;
      }
      if (promo.requiresDeviceVerification(files, labels(pr)) &&
          !labels(pr).has('device-verified')) {
        core.info('Skipping #' + pr.number + ': independent device evidence missing');
        continue;
      }
      try {
        const result = await github.rest.repos.merge({
          ...repoArgs, base: branch, head: pr.head.sha,
          commit_message: 'Testlab integrate PR #' + pr.number + ': ' +
            (pr.title || '').slice(0, 90)
        });
        if (result.status === 204) {
          core.info('Skipping #' + pr.number + ': already present in baseline');
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
      core.info('No compatible opted-in PRs; removed empty Testlab branch');
      return;
    }
    const body = [
      '## Automatic Testlab integration batch',
      'Included source PRs: ' + added.map(n => '#' + n).join(', '),
      'Built from main ' + main + '.',
      '',
      'Source PRs remain open until safe production integration.',
      'This candidate must pass Android, Windows, backend, security and all applicable',
      'feature / release smoke checks at its exact combined SHA.',
      'Conflicts are never auto-resolved by choosing one side.',
      'Risky changes also need independent staging / device evidence before release.',
      'The default-branch promotion controller performs the final gate; no bypass.',
    ].join('\n');
    const response = await github.rest.pulls.create({
      ...repoArgs, title: 'Testlab: verified PR batch (' +
        added.map(n => '#' + n).join(', ') + ')',
      head: branch, base: 'main', body, draft: false
    });
    const pr = response.data;
    core.info('Opened Testlab batch PR #' + pr.number + ' for source PRs ' + added.join(', '));
    await dispatch(github, pr, [...allChangedPaths], core);
  } catch (error) {
    // If a PR was opened, preserve its branch for diagnosis. Never
    // discard a candidate with unmerged original work.
    throw error;
  }
}

async function run({github, context, core}) {
  await ensureQueueLabel(github);
  if (context.eventName === 'pull_request_target' &&
      (context.payload.action !== 'labeled' ||
       context.payload.label?.name !== QUEUE)) return;

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
  const eligible = list.filter(isCandidate)
    .sort((a, b) => a.number - b.number).slice(0, MAX_BATCH);
  if (!eligible.length) {
    core.info('No opted-in, open, internal main-targeting PRs in queue');
    return;
  }
  await makeBatch(github, eligible, context, core);
}

module.exports = run;
module.exports.isCandidate = isCandidate;
module.exports.isBatch = isBatch;
module.exports.relevantWorkflows = relevantWorkflows;
module.exports.bodyWithoutStaleMarkers = bodyWithoutStaleMarkers;
