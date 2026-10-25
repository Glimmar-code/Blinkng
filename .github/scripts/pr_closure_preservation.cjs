'use strict';

// Runs only from trusted main via pull_request_target. Never execute PR code.
// A closed, unmerged PR is reopened unless its owner-attested disposition is
// independently verified against actual GitHub file blobs.
module.exports = async function preserveUnmergedPr({ github, context, core }) {
  const { owner, repo } = context.repo;
  const pr = context.payload.pull_request;
  if (!pr || pr.merged || pr.state !== 'closed') return;

  const headSha = pr.head.sha.toLowerCase();
  const comments = await github.paginate(github.rest.issues.listComments, {
    owner, repo, issue_number: pr.number, per_page: 100
  });
  // For intentional disposal or supersession, require an explicit CURRENT-HEAD
  // owner comment. A PR title/description/label is never authorization.
  const ownerStatements = comments
    .filter(comment => comment.user && comment.user.login.toLowerCase() === owner.toLowerCase())
    .map(comment => (comment.body || '').trim());
  const ownerSaid = text => ownerStatements.includes(text);
  let preserved = false;
  let reason = 'No verified disposition for the exact source PR revision.';

  if (ownerSaid('BLINK ABANDON #' + pr.number + ' HEAD ' + headSha)) {
    preserved = true;
    reason = 'Repository owner explicitly approved intentional abandonment.';
  }

  async function fileBlobSha(path, ref) {
    try {
      const { data } = await github.rest.repos.getContent({ owner, repo, path, ref });
      if (Array.isArray(data) || !data.sha || data.type !== 'file') {
        throw new Error('Expected a file at ' + path + ' @ ' + ref);
      }
      return data.sha;
    } catch (error) {
      if (error.status === 404) return null; // Deletion can be preserved.
      throw error; // Unknown permission/API errors MUST NOT authorize closure.
    }
  }

  async function verifyExactSnapshot(ref) {
    const files = await github.paginate(github.rest.pulls.listFiles, {
      owner, repo, pull_number: pr.number, per_page: 100
    });
    // Conservative: don't allow a huge, truncated or empty diff to silently
    // count as preserved. Require a separate explicit owner abandonment.
    if (!files.length || files.length > 250) {
      return { pass: false, reason: 'Diff is empty or too large for automated proof (' + files.length + ' files).' };
    }
    const mismatch = [];
    for (const file of files) {
      const [source, destination] = await Promise.all([
        fileBlobSha(file.filename, headSha),
        fileBlobSha(file.filename, ref)
      ]);
      if (source !== destination) mismatch.push(file.filename);
      if (file.status === 'renamed' && file.previous_filename) {
        const [oldAtHead, oldAtDest] = await Promise.all([
          fileBlobSha(file.previous_filename, headSha),
          fileBlobSha(file.previous_filename, ref)
        ]);
        if (oldAtHead !== oldAtDest) mismatch.push(file.previous_filename);
      }
      // Limit API calls and fail closed if exact preservation isn't provable.
      if (mismatch.length >= 15) break;
    }
    return mismatch.length
      ? { pass: false, reason: 'Source file blobs differ or are missing: ' + mismatch.join(', ') }
      : { pass: true, reason: 'All source changed-file blobs match the verified commit.' };
  }

  // Auto-recognize an already-integrated PR even without owner annotation.
  // Exact matching content is necessary here, but may be too strict for
  // semantic supersession; in that case use an owner-attested replacement.
  if (!preserved) {
    try {
      const current = (await github.rest.repos.getBranch({
        owner, repo, branch: pr.base.ref
      })).data.commit.sha;
      const integrated = await verifyExactSnapshot(current);
      if (integrated.pass) {
        preserved = true;
        reason = 'Original changed files already match the target branch.';
      }
    } catch (error) {
      reason = 'Target-branch comparison unavailable: ' + error.message;
    }
  }

  if (!preserved && ownerSaid('BLINK PRESERVED ON BASE #' + pr.number + ' HEAD ' + headSha)) {
    const baseBranch = pr.base.ref;
    const baseRef = (await github.rest.repos.getBranch({ owner, repo, branch: baseBranch })).data.commit.sha;
    const result = await verifyExactSnapshot(baseRef);
    preserved = result.pass;
    reason = 'Compare source with current ' + baseBranch + ': ' + result.reason;
  }

  if (!preserved) {
    const signature = new RegExp('^BLINK SUPERSEDED BY #(\\d+) HEAD ' + headSha + '$', 'i');
    for (const statement of ownerStatements.slice().reverse()) {
      const match = statement.match(signature);
      if (!match) continue;
      const replacementNumber = Number(match[1]);
      if (replacementNumber === pr.number) {
        reason = 'A PR cannot supersede itself.';
        break;
      }
      try {
        const replacement = (await github.rest.pulls.get({
          owner, repo, pull_number: replacementNumber
        })).data;
        if (!replacement.merged || !replacement.merge_commit_sha) {
          reason = 'Replacement PR #' + replacementNumber + ' is not merged.';
          break;
        }
        // New PR must have landed in the original target or in production main.
        if (replacement.base.ref !== pr.base.ref && replacement.base.ref !== 'main') {
          reason = 'Replacement PR targets an unrelated branch.';
          break;
        }
        const result = await verifyExactSnapshot(replacement.merge_commit_sha);
        preserved = result.pass;
        reason = 'Merged replacement PR #' + replacementNumber + ': ' + result.reason;
      } catch (error) {
        reason = 'Cannot verify replacement PR #' + replacementNumber + ': ' + error.message;
      }
      break;
    }
  }

  if (preserved) {
    core.info('BLINK PR #' + pr.number + ' may remain closed: ' + reason);
    return;
  }

  core.warning('BLINK PR #' + pr.number + ' closed without preservation proof: ' + reason);
  // Reopen rather than silently discard work. Never force-merge or push code.
  await github.rest.pulls.update({ owner, repo, pull_number: pr.number, state: 'open' });
  await github.rest.issues.createComment({
    owner, repo, issue_number: pr.number,
    body: '🛡️ **BLINK PR preservation guard reopened this unmerged PR.**\n\n' +
      'Reason: ' + reason + '\n\n' +
      'A newer/similar PR does not prove this work was carried over. Fix failed ' +
      'checks on the existing branch where possible. To close as superseded, ' +
      'merge the replacement first, and have ' +
      'the repository owner comment exactly:\n' +
      '`BLINK SUPERSEDED BY #<merged-PR-number> HEAD ' + headSha + '`\n\n' +
      'The guard compares every changed file against the merged commit. ' +
      'For work already on the target branch, use owner comment `BLINK PRESERVED ON BASE #' + pr.number + ' HEAD ' + headSha + '`. ' +
      'To intentionally drop unfinished work, use owner comment `BLINK ABANDON #' + pr.number + ' HEAD ' + headSha + '`. ' +
      'See `.github/PR_PRESERVATION_POLICY.md`.'
  });
};
