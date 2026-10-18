'use strict';

/**
 * Read-only diagnosis plus a deduplicated GitHub Issue when production release
 * checks fail. This never merges, dispatches a build, or changes a public APK.
 * Only trusted GitHub Actions workflow_run metadata is acted upon.
 */
const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const LABEL = 'blink-release-incident';
const WATCHED = new Set([
  'Signed Android release',
  'Publish latest APK',
  'Verify published BLINK APK',
]);
const BAD = new Set(['failure', 'timed_out', 'startup_failure', 'action_required']);

function classify(run) {
  if (!run || !WATCHED.has(run.name)) return null;
  if (run.head_repository?.full_name !== OWNER + '/' + REPO) return null;
  if (run.head_branch !== 'main') return null;
  if (!BAD.has(run.conclusion) || run.status !== 'completed') return null;
  if (!Number.isSafeInteger(run.id) || run.id < 1) return null;
  return {
    title: '[BLINK release] ' + run.name + ' failed on main',
    source: run.name,
    sha: /^[0-9a-f]{40}$/.test(run.head_sha || '') ? run.head_sha : '(unknown)',
    url: 'https://github.com/' + OWNER + '/' + REPO + '/actions/runs/' + run.id,
    conclusion: run.conclusion,
  };
}

function incidentMessage(alert) {
  return [
    'BLINK production-release failure detected by the read-only incident watch.',
    '',
    '- Pipeline: **' + alert.source + '**',
    '- Result: **' + alert.conclusion + '**',
    '- Source commit: `' + alert.sha + '`',
    '- GitHub Actions details: ' + alert.url,
    '',
    'The monitor did **not** merge, rebuild, unpublish, or replace any APK.',
    '',
    'Release operator checklist:',
    '1. Inspect the failing GitHub job logs and current production main SHA.',
    '2. Correct the cause in a Testlab branch; rerun required checks.',
    '3. Verify signed package version, identity, SHA-256, release metadata and live download.',
    '4. Do not republish an older version or overwrite a previously verified release.',
  ].join('\n');
}

async function watch({github, context, core}) {
  const run = context.payload.workflow_run;
  const alert = classify(run);
  if (!alert) { core.info('No actionable trusted production release failure'); return; }
  const params = {owner: OWNER, repo: REPO};

  try {
    await github.rest.issues.getLabel({...params, name: LABEL});
  } catch (error) {
    if (error.status !== 404) throw error;
    await github.rest.issues.createLabel({
      ...params, name: LABEL, color: 'b60205',
      description: 'Production APK signing, publishing, or public download failed',
    });
  }

  const issues = await github.paginate(github.rest.issues.listForRepo, {
    ...params, state: 'open', labels: LABEL, per_page: 100,
  });
  const existing = issues.find(issue => !issue.pull_request && issue.title === alert.title);
  if (existing) {
    await github.rest.issues.createComment({
      ...params, issue_number: existing.number, body: incidentMessage(alert),
    });
    core.warning('Updated release incident #' + existing.number + ': ' + alert.url);
    return;
  }
  const {data: created} = await github.rest.issues.create({
    ...params, title: alert.title, body: incidentMessage(alert), labels: [LABEL],
  });
  core.warning('Opened release incident #' + created.number + ': ' + alert.url);
}

module.exports = watch;
module.exports.classify = classify;
module.exports.incidentMessage = incidentMessage;
