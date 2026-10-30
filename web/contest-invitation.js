/* Safe approved-contest invitation. Never asks for credentials or records a vote on the website. */
(function () {
  'use strict';
  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  function render(root, contestId, entryId) {
    if (!UUID.test(contestId) || !UUID.test(entryId)) return false;
    const config = window.BLINK_WEB_CONFIG || {};
    const apiBase = String(config.supabaseUrl || '').replace(/\/$/, '');
    const key = String(config.publishableKey || '');
    const download = 'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk';
    const voteUrl = 'https://blink.com.ng/contest/' + contestId.toLowerCase() + '/' + entryId.toLowerCase();
    const pkg = String(config.androidPackage || 'com.aistudio.blink.appvtwo').replace(/[^a-zA-Z0-9_.]/g, '');
    const openHref = 'intent://contest/' + contestId + '/' + entryId +
      '#Intent;scheme=blink;package=' + pkg +
      ';S.browser_fallback_url=' + encodeURIComponent(voteUrl) + ';end';

    document.title = 'Vote for a contestant · BLINK Contest';
    root.innerHTML =
      '<main class="contest-invite-page"><div class="contest-invite-wrap">' +
      '<div class="contest-invite-brand"><img src="/blink-logo-v2.png" alt=""> BLINK</div>' +
      '<section class="contest-invite-flyer" aria-label="Personalized BLINK contestant invitation">' +
      '<span class="contest-invite-tag">Official contest entry</span>' +
      '<div id="contest-invite-avatar" class="contest-invite-avatar" aria-hidden="true">B</div>' +
      '<h1 id="contest-invite-name" class="contest-invite-name">Opening contestant…</h1>' +
      '<div id="contest-invite-handle" class="contest-invite-handle"></div>' +
      '<div class="contest-invite-label">I am contesting in</div>' +
      '<h2 id="contest-invite-contest" class="contest-invite-contest">BLINK Contest</h2>' +
      '<div class="contest-invite-stats"><div><strong id="contest-invite-rank">—</strong>' +
      '<span>Current position</span></div><div><strong id="contest-invite-votes">—</strong>' +
      '<span>Free votes</span></div></div>' +
      '<p class="contest-invite-appeal">Help me reach the top!</p>' +
      '<p class="contest-invite-sub">Support my entry with your free vote. One vote per account.</p>' +
      '<div class="contest-invite-domain">BLINK.COM.NG / CONTEST</div>' +
      '</section><section class="contest-invite-actions" aria-label="Vote and download">' +
      '<a id="contest-invite-open" class="contest-invite-action" href="#">Open BLINK to vote</a>' +
      '<a class="contest-invite-action secondary" href="' + download +
        '" target="_blank" rel="noopener noreferrer">Don\'t have BLINK? Download official Android app</a>' +
      '<button id="contest-invite-copy" class="contest-invite-action secondary" type="button">Copy voting link</button>' +
      '</section><p class="contest-invite-help">Already installed BLINK? Tap <strong>Open BLINK to vote</strong>. ' +
      'After downloading and installing the APK, come back to this page and tap it again. ' +
      'Sign in or create an account when BLINK asks; your exact contestant will open. ' +
      'Your vote is only counted when you personally tap Vote in BLINK.</p>' +
      '<p id="contest-invite-status" role="status" class="contest-invite-help" aria-live="polite"></p>' +
      '</div></main>';

    const status = root.querySelector('#contest-invite-status');
    const open = root.querySelector('#contest-invite-open');
    const copy = root.querySelector('#contest-invite-copy');
    open.href = openHref;
    // Keep the original website URL in the browser's history for post-install continuation.
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(voteUrl);
        status.textContent = 'Your personal voting link has been copied.';
      } catch (_) {
        status.textContent = 'Copy this link from your browser address bar to share the invitation.';
      }
    });

    (async () => {
      if (!apiBase || !key) {
        status.textContent = 'Contest information is temporarily unavailable. The voting link remains available.';
        return;
      }
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15000);
      let unavailable = false;
      try {
        const response = await fetch(apiBase + '/rest/v1/rpc/get_blink_contest_candidate', {
          method: 'POST',
          headers: { 'apikey': key, 'Content-Type': 'application/json', 'Accept': 'application/json' },
          body: JSON.stringify({p_contest_id: contestId, p_entry_id: entryId}),
          cache: 'no-store', signal: controller.signal
        });
        if (!response.ok) {
          unavailable = [400, 404, 410].includes(response.status);
          throw Error('not_available');
        }
        const candidate = await response.json();
        if (!candidate || String(candidate.id).toLowerCase() !== entryId.toLowerCase()) { unavailable = true; throw Error('not_available'); }
        const name = String(candidate.name || candidate.username || 'BLINK contestant').slice(0, 90);
        const username = String(candidate.username || '').slice(0, 40);
        root.querySelector('#contest-invite-name').textContent = name;
        root.querySelector('#contest-invite-handle').textContent = username ? '@' + username : '';
        root.querySelector('#contest-invite-contest').textContent = String(candidate.contest_title || 'BLINK Contest').slice(0,100);
        root.querySelector('#contest-invite-rank').textContent = Number(candidate.rank) > 0 ? '#' + Number(candidate.rank) : '—';
        root.querySelector('#contest-invite-votes').textContent = Number(candidate.votes || 0).toLocaleString('en-NG');
        const avatar = root.querySelector('#contest-invite-avatar');
        avatar.textContent = name.trim().charAt(0).toUpperCase() || 'B';
        const avatarUrl = String(candidate.avatar_url || '');
        try {
          const parsed = new URL(avatarUrl);
          if (parsed.protocol === 'https:') {
            const img = document.createElement('img');
            img.src = parsed.href;
            img.referrerPolicy = 'no-referrer';
            img.alt = '';
            img.addEventListener('error', () => img.remove(), {once:true});
            avatar.replaceChildren(img);
          }
        } catch (_) { /* Keep initial fallback */ }
        document.title = name + ' · Vote in BLINK Contest';
        if (candidate.contest_status !== 'voting') {
          status.textContent = 'Voting may not be open yet, or this contest has ended. BLINK will show the current status.';
        }
      } catch (_) {
        root.querySelector('#contest-invite-name').textContent = 'Contestant unavailable';
        status.textContent = 'This entry could not be verified. Only approved contest entries can receive votes.';
        if (unavailable) {
          open.setAttribute('aria-disabled', 'true');
          open.removeAttribute('href');
        } else {
          status.textContent = 'Contest information could not be loaded. You can still open BLINK to verify this invitation and vote.';
        }
      } finally { clearTimeout(timeout); }
    })();
    return true;
  }

  window.BlinkContestInvite = Object.freeze({render});
})();
