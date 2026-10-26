import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const read = path => fs.readFileSync(new URL('../' + path, import.meta.url), 'utf8');

test('browser script parses after link-card injection', () => {
  assert.doesNotThrow(() => new vm.Script(read('web/app.js')));
});

test('web rich preview uses inert text and never injects remote metadata HTML', () => {
  const code = read('web/app.js');
  assert.match(code, /function richLinkCard\(text\)/);
  assert.match(code, /title\.textContent =/);
  assert.match(code, /desc\.textContent =/);
  assert.match(code, /img\.referrerPolicy='no-referrer'/);
  assert.match(code, /rel="noopener noreferrer"/);
  assert.match(code, /IntersectionObserver/);
  for (const surface of ['post.text', 'comment.content', 'm.content', 's.caption', 'r.text'])
    assert.ok(code.includes('richLinkCard(' + surface), surface);
});

test('Android and Windows share one URL recognition policy', () => {
  const shared = read('shared/src/main/kotlin/com/blinkng/shared/BlinkRichLinks.kt');
  assert.match(shared, /object BlinkRichLinks/);
  assert.match(shared, /rawUserInfo != null/);
  assert.match(read('app/src/main/java/com/example/ui/components/BlinkRichLinkPreview.kt'),
    /BlinkRichLinks\.first\(text\)/);
  assert.match(read('desktopApp/src/main/kotlin/com/blinkng/desktop/ui/DesktopRichLinkPreview.kt'),
    /BlinkRichLinks\.first\(text\)/);
});

test('private Android chat preview preference still gates metadata', () => {
  const messages = read('app/src/main/java/com/example/ui/screens/PremiumMessagesScreen.kt');
  assert.match(messages, /if \(allowLinkPreviews\) \{/);
  assert.match(messages, /BlinkRichLinkPreview/);
});

test('backend forbids arbitrary hosts, redirects and unbounded reads', () => {
  const code = read('supabase/functions/link-metadata/index.ts');
  assert.match(code, /allowedHosts\.has\(host\)/);
  assert.match(code, /redirect: "manual"/);
  assert.match(code, /128 \* 1024/);
  assert.match(code, /AbortSignal\.timeout/);
  assert.match(code, /if \(url\.protocol !== "https:"/);
  assert.match(code, /token\|secret\|key\|auth\|code\|signature\|session/);
});

test('profile sharing uses the official domain instead of temporary placeholder', () => {
  const code = read('supabase/functions/share-preview/index.ts');
  assert.match(code, /const SHARE_ORIGIN = "https:\/\/blink\.com\.ng"/);
  assert.doesNotMatch(code, /my-app\.com/);
});

test('browser previews render three distinct links, safely, with no inline HTML', () => {
  const js = read('web/app.js');
  const start = js.indexOf('  function previewLinkInText(text) {');
  const end = js.indexOf('  async function linkMetadata(url) {', start);
  assert.ok(start >= 0 && end > start, 'browser link renderer not found');
  const escape = value => String(value).replace(/[&<>"']/g, char =>
    ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'" :'&#39;'}[char]));
  const {previewLinkInText, richLinkCard} =
    new Function('esc', js.slice(start, end) + ';return {previewLinkInText, richLinkCard};')(escape);
  assert.equal(previewLinkInText('javascript:alert(1)'), null);
  assert.equal(previewLinkInText('https://user:password@example.com'), null);
  const cards = richLinkCard('https://one.example/path https://two.example/ https://one.example/path https://three.example/ https://four.example/');
  assert.equal((cards.match(/class="rich-link-card"/g)||[]).length, 3);
  assert.ok(cards.includes('one.example'));
  assert.ok(!cards.includes('four.example'));
  assert.ok(!richLinkCard('Plain text').includes('rich-link-card'));
  assert.ok(!richLinkCard('https://x.example/?v="><script>hacked</script>').includes('<script>'));
});

test('chat preview preference on Web is a server-authoritative per-account setting', () => {
  const js = read('web/app.js');
  assert.match(js, /user_chat_privacy/);
  assert.match(js, /p_allow_link_previews: !!enabled/);
  assert.match(js, /if\(!privacy.available\) throw/);
  assert.match(js, /!m.deleted_for_everyone&&chatPreviewPrivacy.allowed/);
});
