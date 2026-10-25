/**
 * Trusted, bounded CI incident controller.
 * Runs from main via workflow_run; never checks out or runs PR code with a write token.
 * GitHub Actions can retry transient failures, but cannot generally repair broken source.
 */
'use strict';

const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const FULL = OWNER + '/' + REPO;
const LABEL = 'ci-recovery-blocked';
const MAX_ATTEMPTS = 3; // one initial run plus at most two failed-job retries
const CHECKS = new Set([
  'Android quality gate',
  'Android runtime smoke',
  'Android release smoke test',
  'Windows desktop quality gate',
  'APK delivery integrity',
  'Web Parity Testlab Gate',
  'Supabase safety gate',
  'Secret leak guard',
  'Signed Android release',
  'Publish latest APK',
]);
const PRODUCTION = new Set(['Signed Android release', 'Publish latest APK']);
const NON_RETRYABLE = new Set(['Secret leak guard', 'Supabase safety gate']);
const BAD = new Set(['failure', 'timed_out', 'startup_failure', 'action_required']);
const BAD_JOB = new Set(['failure', 'timed_out', 'startup_failure', 'action_required']);
const SHORT = input => String(input || '').slice(0, 240);

function qualify(run) {
  if (!run || !CHECKS.has(run.name) || run.status !== 'completed') return null;
  if (!BAD.has(run.conclusion) || !Number.isSafeInteger(run.id) || run.id <= 0) return null;
  if (run.head_repository?.full_name !== FULL) return null;
  const branch = run.head_branch || '';
  if (branch !== 'main' && !/^Testlab(?:-|$)/.test(branch)) return null;
  if (branch === 'main' && !PRODUCTION.has(run.name)) return null;
  if (!Number.isInteger(run.run_attempt) || run.run_attempt < 1) return null;
  return { branch, runId: run.id, attempt: run.run_attempt, workflow: run.name };
}

function failedSteps(jobs) {
  return (jobs || []).filter(job => BAD_JOB.has(job.conclusion)).map(job => ({
    job: SHORT(job.name),
    url: job.html_url || '',
    steps: (job.steps || []).filter(s => BAD_JOB.has(s.conclusion)).map(s => SHORT(s.name)),
  }));
}

function isSecurityFailure(workflow, failures) {
  if (NON_RETRYABLE.has(workflow)) return true;
  const unsafe = /(secret|credential|api key|signing certificate|rls|permission|policy|security)/i;
  return failures.some(job => job.steps.some(s => unsafe.test(s)));
}

function issueMarker(branch, workflow) {
  const safe = value => value.replace(/[^a-zA-Z0-9_-]/g, '-');
  return '<!-- blink-ci-recovery:' + safe(branch) + ':' + safe(workflow) + ' -->';
}

function report(item, run, failures) {
  const marker = issueMarker(item.branch, item.workflow);
  const rows = failures.length ? failures.map(f =>
    '- **' + f.job + '**: ' + (f.steps.join(', ') || 'job failure without step detail') +
    (f.url ? ' — ' + f.url : '')).join('\n') : '- Job details unavailable; inspect the workflow link.';
  return marker + '\n' +
    'BLINK automatic CI recovery has stopped safely after detecting a persistent or non-retryable blocker.\n\n' +
    '**Branch:** ' + item.branch + '\n' +
    '**Workflow:** ' + item.workflow + '\n' +
    '**Commit:** ' + SHORT(run.head_sha) + '\n' +
    '**Attempt:** ' + item.attempt + ' / ' + MAX_ATTEMPTS + '\n' +
    '**Run:** ' + (run.html_url || '') + '\n\n' +
    '**Failed jobs and steps:**\n' + rows + '\n\n' +
    '**Next automatic-agent task:** Read the linked error logs, correct the underlying code or infrastructure configuration in a Testlab branch, rerun the affected checks, and obtain all required staging/device/backend evidence. ' +
    'Do not merge to main or publish until every required gate is green. ' +
    'For production signing or publishing failures, retain the last verified public APK and never change signing secrets automatically.\n\n' +
    'A retry is not a code fix; this controller has not bypassed checks or edited application code.';
}

async function createOrUpdateBlocker(github, item, run, failures, core) {
  const {data: labels} = await github.rest.issues.listLabelsForRepo({
    owner: OWNER, repo: REPO, per_page: 100,
  });
  if (!labels.some(label => label.name === LABEL)) {
    try {
      await github.rest.issues.createLabel({
        owner: OWNER, repo: REPO, name: LABEL, color: 'B60205',
        description: 'Automatic CI retry exhausted; requires Testlab code repair',
      });
    } catch (error) {
      if (error.status !== 422) throw error; // created by a parallel incident
    }
  }
  const {data} = await github.rest.issues.listForRepo({
    owner: OWNER, repo: REPO, state: 'open', labels: LABEL, per_page: 100,
  });
  const marker = issueMarker(item.branch, item.workflow);
  const existing = data.find(issue => !issue.pull_request && (issue.body || '').includes(marker));
  const body = report(item, run, failures);
  if (existing) {
    await github.rest.issues.update({owner: OWNER, repo: REPO, issue_number: existing.number, body});
    core.warning('Updated recovery blocker #' + existing.number);
  } else {
    const issue = await github.rest.issues.create({
      owner: OWNER, repo: REPO,
      title: '[BLINK CI] ' + item.branch + ': ' + item.workflow + ' blocked',
      body, labels: [LABEL],
    });
    core.warning('Created recovery blocker #' + issue.data.number);
  }
}

async function controller({github, context, core}) {
  if (context.eventName !== 'workflow_run' || context.repo.owner !== OWNER ||
      context.repo.repo !== REPO) return;
  const run = context.payload.workflow_run;
  const item = qualify(run);
  if (!item) return;
  // Do not retry old code after another change lands on Testlab or main.
  const {data: branch} = await github.rest.repos.getBranch({
    owner: OWNER, repo: REPO, branch: item.branch,
  });
  const currentSha = branch.commit.sha;
  const allowedShas = [run.head_sha, ...(run.pull_requests || []).map(pr => pr.head?.sha)];
  if (!allowedShas.includes(currentSha)) {
    core.info('Skipped superseded run ' + item.runId + '; branch moved to ' + currentSha);
    return;
  }
  const {data: fresh} = await github.rest.actions.getWorkflowRun({
    owner: OWNER, repo: REPO, run_id: item.runId,
  });
  if (fresh.run_attempt !== item.attempt || fresh.status !== 'completed' ||
      fresh.conclusion !== run.conclusion) {
    core.info('Skipped stale or already restarted run ' + item.runId);
    return;
  }
  const {data: jobs} = await github.rest.actions.listJobsForWorkflowRun({
    owner: OWNER, repo: REPO, run_id: item.runId, filter: 'latest', per_page: 100,
  });
  const failures = failedSteps(jobs.jobs);
  if (item.attempt < MAX_ATTEMPTS && !isSecurityFailure(item.workflow, failures)) {
    await github.rest.actions.reRunWorkflowFailedJobs({
      owner: OWNER, repo: REPO, run_id: item.runId,
      enable_debug_logging: item.attempt > 1,
    });
    core.info('Retried failed jobs in ' + item.workflow +
      ', attempt ' + (item.attempt + 1) + ' / ' + MAX_ATTEMPTS);
  } else {
    await createOrUpdateBlocker(github, item, run, failures, core);
  }
}

module.exports = controller;
module.exports.qualify = qualify;
module.exports.failedSteps = failedSteps;
module.exports.isSecurityFailure = isSecurityFailure;
module.exports.issueMarker = issueMarker;
module.exports.report = report;
