/**
 * Read-only diagnosis + GitHub issue-based BLINK release failure alerts.
 *
 * The workflow that imports this script MUST check out trusted main, never
 * checkout/run untrusted workflow_run head code. It never mutates releases,
 * skips GitHub's deployment safety gates, or attempts automated rollback.
 */
'use strict';

const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const FULL = OWNER + '/' + REPO;
const ALERT = 'blink-release-alert';
const MONITORED = new Set([
  'Signed Android release',
  'Publish latest APK',
  'Verify published BLINK APK',
  'Notify BLINK website release',
]);
const FAILURES = new Set(['failure', 'timed_out', 'action_required', 'startup_failure']);
const SUCCESS = 'success';

function qualify(run) {
  if (!run || !MONITORED.has(run.name)) return null;
  if (run.head_branch !== 'main' || run.head_repository?.full_name !== FULL) return null;
  if (run.status !== 'completed') return null;
  if (![...FAILURES, SUCCESS].includes(run.conclusion)) return null;
  if (!Number.isSafeInteger(run.id) || run.id <= 0) return null;
  return {
    workflow: run.name,
    runId: run.id,
    sha: String(run.head_sha || '').slice(0, 40),
    failed: FAILURES.has(run.conclusion),
    url: run.html_url || 'https://github.com/' + FULL + '/actions',
    conclusion: run.conclusion,
  };
}

function titleFor(workflow) {
  return '[BLINK release] ' + workflow + ' failed';
}

function marker(workflow) {
  return '<!-- blink-release-watch:' + workflow.replace(/[^A-Za-z0-9-]/g, '-') + ' -->';
}

function details(item) {
  return marker(item.workflow) + '\n' +
    'BLINK production release monitoring detected an unsuccessful GitHub Actions run.\n\n' +
    '**Workflow:** ' + item.workflow + '\n' +
    '**Conclusion:** ' + item.conclusion + '\n' +
    '**Run ID:** ' + item.runId + '\n' +
    '**Commit:** ' + item.sha + '\n' +
    '**Diagnostics:** ' + item.url + '\n\n' +
    '**Recovery:** Inspect and correct the exact failed job on Testlab, rerun required quality gates, ' +
    'reconcile with the current main branch, request explicit owner approval, and issue a new signed ' +
    'release only when safe. Leave the previous verified latest APK public.\n\n' +
    'This issue is an alert only: no automatic merge, rollback, restart, or publication was attempted.';
}

function issueMatches(issue, workflow) {
  return issue.title === titleFor(workflow) &&
    (issue.body || '').includes(marker(workflow));
}

async function ensureLabel(github) {
  try {
    await github.rest.issues.getLabel({owner: OWNER, repo: REPO, name: ALERT});
  } catch (error) {
    if (error.status !== 404) throw error;
    await github.rest.issues.createLabel({
      owner: OWNER, repo: REPO, name: ALERT, color: 'B60205',
      description: 'BLINK signed APK build, publication or delivery needs attention'
    });
  }
}

async function alert(github, core, item) {
  const issues = await github.paginate(github.rest.issues.listForRepo, {
    owner: OWNER, repo: REPO, state: 'all', labels: ALERT, per_page: 100,
  });
  const matching = issues.filter(issue => !issue.pull_request && issueMatches(issue, item.workflow))
    .sort((a,b)=>b.number-a.number);
  const open = matching.find(issue=>issue.state==='open');

  // Do not let an older run supersede the last recorded failure.
  const last = matching[0];
  if (last) {
    const match = (last.body || '').match(/\*\*Run ID:\*\* ([0-9]+)/);
    if (match && Number(match[1]) > item.runId) {
      core.info('Ignoring older release run ' + item.runId); return;
    }
  }

  if (item.failed) {
    await ensureLabel(github);
    const body = details(item);
    if (open) {
      if ((open.body || '').includes('**Run ID:** ' + item.runId)) {
        core.info('Release alert already exists for run ' + item.runId); return;
      }
      await github.rest.issues.update({
        owner: OWNER, repo: REPO, issue_number: open.number, body
      });
      core.warning('Updated existing release alert #' + open.number);
    } else {
      const created = await github.rest.issues.create({
        owner: OWNER, repo: REPO, title: titleFor(item.workflow),
        body, labels: [ALERT]
      });
      core.warning('Opened release alert #' + created.data.number);
    }
  } else if (open) {
    await github.rest.issues.createComment({
      owner: OWNER, repo: REPO, issue_number: open.number,
      body: 'Latest completed main run passed: ' + item.url +
        '\n\nCheck the latest public APK audit before treating publication as verified.'
    });
    await github.rest.issues.update({
      owner: OWNER, repo: REPO, issue_number: open.number, state: 'closed'
    });
    core.info('Closed recovered release alert #' + open.number);
  }
}

async function monitor({github, context, core}) {
  if (context.repo.owner !== OWNER || context.repo.repo !== REPO) {
    core.warning('Not the BLINK production repository'); return;
  }
  if (context.eventName === 'workflow_run') {
    const item = qualify(context.payload.workflow_run);
    if (item) await alert(github, core, item);
    return;
  }
  if (!['schedule','workflow_dispatch'].includes(context.eventName)) return;

  // Daily/manual fallback catches lost GitHub workflow_run webhooks.
  // Do not infer a release failure from a queued/running action.
  for (const name of MONITORED) {
    const workflows = await github.paginate(github.rest.actions.listRepoWorkflows, {
      owner: OWNER, repo: REPO, per_page: 100
    });
    const workflow = workflows.find(w=>w.name===name);
    if (!workflow) {
      core.warning('Missing monitored workflow: ' + name); continue;
    }
    const {data} = await github.rest.actions.listWorkflowRuns({
      owner: OWNER, repo: REPO, workflow_id: workflow.id, branch: 'main',
      status: 'completed', per_page: 5,
    });
    const run = (data.workflow_runs || [])[0];
    if (!run) continue;
    const item = qualify(run);
    if (item) await alert(github, core, item);
  }
}

module.exports = monitor;
module.exports.qualify = qualify;
module.exports.marker = marker;
module.exports.details = details;
module.exports.issueMatches = issueMatches;
