/* BLINK: lazy metadata cards, isolated so the main mobile bundle stays small. */
(function () {
  'use strict';
  const esc = value => String(value ?? '').replace(/[&<>"']/g,
    c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const safeUrl = value => {
    try {
      const url = new URL(String(value || ''), location.origin);
      return ['https:', 'http:'].includes(url.protocol) ? url.href : '';
    } catch { return ''; }
  };
  const richPreviewCache = new Map();
  const richPreviewEndpoint = 'https://jhwgifrlxwspoedxjaly.supabase.co/functions/v1/link-metadata';
  function previewLinkInText(text) {
    const match = String(text || '').match(/https?:\/\/[^\s<>"']+/i);
    if(!match) return null;
    const raw = match[0].replace(/[.,!?;:)\]}]+$/, '');
    if(raw.length > 2048) return null;
    try {
      const url = new URL(raw);
      if(!['http:', 'https:'].includes(url.protocol) || !url.hostname.includes('.') ||
        url.username || url.password) return null;
      return url;
    } catch { return null; }
  }
  function richLinkCard(text) {
    const raw = String(text || '').match(/https?:\/\/[^\s<>"']+/gi) || [];
    const seen = new Set();
    return raw.map(value => previewLinkInText(value)).filter(url => {
      if(!url || seen.has(url.href)) return false;
      seen.add(url.href); return true;
    }).slice(0,3).map(url => `<a class="rich-link-card" data-link-card="${esc(url.href)}" href="${esc(url.href)}" target="_blank" rel="noopener noreferrer" aria-label="Open ${esc(url.hostname)} link">
      <span class="rich-link-host">${esc(url.hostname)}</span>
      <strong class="rich-link-title">${esc(url.hostname)}</strong>
      <span class="rich-link-description">${esc(url.href.slice(0,180))}</span>
    </a>`).join('');
  }
  async function linkMetadata(url) {
    if(richPreviewCache.has(url)) return richPreviewCache.get(url);
    const promise = (async () => {
      try {
        const response = await fetch(richPreviewEndpoint+'?url='+encodeURIComponent(url),
          {signal: AbortSignal.timeout(4500), headers: {'Accept':'application/json'}});
        if(!response.ok) return null;
        return await response.json();
      } catch { return null; }
    })();
    richPreviewCache.set(url, promise);
    if(richPreviewCache.size > 180) richPreviewCache.delete(richPreviewCache.keys().next().value);
    return promise;
  }
  function hydrateLinkCards() {
    const cards = [...document.querySelectorAll('.rich-link-card:not([data-preview-bound])')];
    const hydrate = async card => {
      if(!card.isConnected) return;
      const metadata = await linkMetadata(card.dataset.linkCard);
      if(!card.isConnected || !metadata) return;
      const title = card.querySelector('.rich-link-title');
      const desc = card.querySelector('.rich-link-description');
      if(title && metadata.title) title.textContent = String(metadata.title).slice(0,160);
      if(desc && metadata.description) desc.textContent = String(metadata.description).slice(0,240);
      const image = safeUrl(metadata.image);
      if(image && image.startsWith('https://') && !card.querySelector('img')) {
        const img = document.createElement('img');
        img.src = image; img.alt = 'Website thumbnail'; img.loading='lazy';
        img.referrerPolicy='no-referrer';
        card.prepend(img);
      }
    };
    if(!('IntersectionObserver' in window)) {
      cards.slice(0,20).forEach(card => {card.dataset.previewBound='1'; hydrate(card);});
      return;
    }
    const observer = new IntersectionObserver(entries => {
      for(const entry of entries) if(entry.isIntersecting) {
        observer.unobserve(entry.target);
        hydrate(entry.target);
      }
    }, {rootMargin:'280px'});
    cards.forEach(card => {card.dataset.previewBound='1';observer.observe(card);});
  }


  // This reads the same RLS-protected account setting as Android and Windows.
  async function readChatPreviewPrivacy(table) {
    try {
      const rows = await table('user_chat_privacy', 'select=who_can_message,who_can_call,who_can_group_invite,show_online,show_last_seen,send_read_receipts,show_typing,show_recording,show_profile_photo_in_chat,allow_link_previews,notification_preview&limit=1');
      const row = rows?.[0] || {};
      return { allowed: row.allow_link_previews !== false, row, available: true };
    } catch {
      // Never load remote chat previews when the user's preference is unknown.
      return { allowed: false, row: null, available: false };
    }
  }
  async function saveChatPreviewPrivacy(privacy, enabled, rpc) {
    if(!privacy.available) throw Error('Chat privacy settings are temporarily unavailable.');
    const row = privacy.row || {};
    const normal = (key, fallback) => row[key] == null ? fallback : row[key];
    const result = await rpc('update_chat_privacy', {
      p_who_can_message: normal('who_can_message', 'everyone'),
      p_who_can_call: normal('who_can_call', 'everyone'),
      p_who_can_group_invite: normal('who_can_group_invite', 'everyone'),
      p_show_online: normal('show_online', true),
      p_show_last_seen: normal('show_last_seen', true),
      p_send_read_receipts: normal('send_read_receipts', true),
      p_show_typing: normal('show_typing', true),
      p_show_recording: normal('show_recording', true),
      p_show_profile_photo_in_chat: normal('show_profile_photo_in_chat', true),
      p_allow_link_previews: !!enabled,
      p_notification_preview: normal('notification_preview', 'full')
    });
    if(result !== true) throw Error('Could not save link preview preference.');
  }
  window.BlinkLinkPreview = Object.freeze({card: richLinkCard, hydrate: hydrateLinkCards, readPrivacy: readChatPreviewPrivacy, savePrivacy: saveChatPreviewPrivacy});
})();
