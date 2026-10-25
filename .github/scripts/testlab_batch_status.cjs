/**
 * Human-readable BLINK integration diagnostics from trusted main.
 * One deduplicated sticky comment per PR. This code never merges or approves.
 */
'use strict';

const OWNER = 'Glimmar-code';
const REPO = 'Blinkng';
const MARKER = '<!-- BLINK_TESTLAB_QUEUE_STATUS_V1 -->';
const repoArgs = {owner: OWNER, repo: REPO};
const COMMENT_LIMIT = 12;

const REASONS = Object.freeze({
  draft: ['Draft PR — excluded', 'Mark Ready for review only after development and required tests finish.'],
  'title-hold': ['PR title marks the change unfinished or superseded',
    'Resolve outstanding work; update the title only after it is genuinely ready.'],
  'body-hold': ['PR description says this must not be merged yet',
    'Complete testing and review; remove draft/hold wording only when ready.'],
  'unfinished-checklist': ['Unfinished PR checklist',
    'Finish and verify unchecked tasks before marking them complete.'],
  'hold-label': ['PR has a blocking label', 'Resolve the blocker and remove the hold label only when verified.'],
  inactive: ['PR is outside the recent-activity window',
    'Review whether the change is needed; refresh it with a verified commit if appropriate.'],
  'wrong-base': ['PR does not target main', 'Review its intended target and open a production integration PR when ready.'],
  'external-repo': ['Fork PR needs security review', 'Have a maintainer review the code and its provenance separately.'],
  'not-open': ['PR is no longer open', 'No action is needed.'],
  'integration-batch': ['Existing Testlab integration batch',
    'Follow combined quality gates; promotion occurs only after verification.'],
  'incomplete-files': ['Changed-file list is incomplete',
    'Investigate the diff/API pagination before trusting CI scope.'],
  'unsafe-ci-changes': ['CI/workflow policy requires separate review',
    'Review CI changes in their own protected PR; do not auto-batch unreviewed workflows.'],
  'staging-required': ['Backend or release staging evidence missing',
    'Verify safely in staging; record the required evidence, not a production-first experiment.'],
  'device-required': ['Independent device evidence missing',
    'Perform device acceptance tests and record the evidence before release.'],
  'source-ci': ['Required source CI missing, pending or failed',
    'Open Checks; fix failures or run missing required checks for this exact commit.'],
  'review-changes': ['Reviewer requested changes',
    'Address requested changes and reverify the updated revision.'],
  'changed-head': ['PR changed during verification',
    'Let the new commit finish its checks; automatic scanning will repeat.'],
  conflict: ['Code conflicts with another change',
    'Reconcile in Testlab without discarding functionality, then rerun integration tests.'],
  'already-integrated': ['Commits already in integration baseline',
    'Check functionality is preserved; do not merge duplicate work.'],
  queued: ['Source checks passed — eligible for Testlab',
    'No Update branch click is needed: integration retests against current main.'],
  batched: ['Included in isolated Testlab batch',
    'Review combined checks; release happens only after every required gate passes.'],
});

function statusEntry(pr, code, detail = '') {
  const item = REASONS[code] || ['Needs investigation', 'Inspect the trusted Testlab queue run.'];
  return {
    number: pr.number, sha: pr.head?.sha || '', code,
    message: item[0], action: item[1],
    detail: String(detail || '').replace(/[\r\n|<>]/g, ' ').slice(0, 120),
  };
}

function renderComment(entry) {
  const head = /^[a-f0-9]{40}$/.test(entry.sha) ? entry.sha.slice(0, 12) : 'unknown';
  const extra = entry.detail ? '\n\n**Details:** ' + entry.detail : '';
  return [
    MARKER,
    '### BLINK automatic integration status',
    '**Current state:** ' + entry.message,
    '**Next action:** ' + entry.action + extra,
    '**Checked PR head:** ' + head,
    '',
    '> GitHub may still show Update branch when main advances.',
    '> This alone is not a failure. Testlab integrates against the latest main',
    '> and repeats the combined quality checks before any production merge.',
  ].join('\n');
}

function isOurComment(comment) {
  return comment && typeof comment.body === 'string' &&
    comment.body.includes(MARKER) &&
    (comment.user?.type === 'Bot' || /\[bot\]$/.test(comment.user?.login || ''));
}

async function syncComment(github, entry) {
  const comments = await github.paginate(github.rest.issues.listComments, {
    ...repoArgs, issue_number: entry.number, per_page: 100
  });
  const previous = comments.find(isOurComment);
  // No new comment on successful/queued PRs. A preexisting hold notice
  // IS updated so it cannot remain misleading after the issue is resolved.
  if (!previous && (entry.code === 'queued' || entry.code === 'batched')) return 'no-comment-needed';
  const body = renderComment(entry);
  if (previous?.body === body) return 'unchanged';
  if (previous) {
    await github.rest.issues.updateComment({
      ...repoArgs, comment_id: previous.id, body
    });
    return 'updated';
  }
  await github.rest.issues.createComment({
    ...repoArgs, issue_number: entry.number, body
  });
  return 'created';
}

async function publishReport(github, core, entries, options = {}) {
  const byPr = new Map(entries.map(e => [e.number, e]));
  const rows = [...byPr.values()].sort((a, b) => b.number - a.number);
  if (core.summary && rows.length) {
    try {
      const cells = [['PR', 'Status', 'Next action']];
      for (const e of rows.slice(0, 60)) cells.push([
        '#' + e.number, e.message + (e.detail ? ' — ' + e.detail : ''), e.action
      ]);
      await core.summary.addHeading('BLINK Testlab queue — PR decisions', 2)
        .addRaw('Status only: no checks, branches, releases or reviews are bypassed.\n')
        .addTable(cells).write();
    } catch (error) {
      core.warning('Actions summary unavailable: ' + error.message);
    }
  }
  let writes = 0;
  for (const entry of rows) {
    if (writes >= (options.maxComments ?? COMMENT_LIMIT)) break;
    try {
      const result = await syncComment(github, entry);
      if (result === 'created' || result === 'updated') writes++;
      core.info('#' + entry.number + ': ' + entry.message + ' [' + result + ']');
    } catch (error) {
      core.warning('PR #' + entry.number + ' notice unavailable: ' + error.message);
    }
  }
  return {total: rows.length, writes};
}

module.exports = {
  statusEntry, renderComment, isOurComment, syncComment, publishReport, REASONS
};
