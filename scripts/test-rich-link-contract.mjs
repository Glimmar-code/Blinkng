import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const read = path => fs.readFileSync(new URL('../' + path, import.meta.url), 'utf8');

test('browser script parses after link-card injection', () => {
  assert.doesNotThrow(() => new vm.Script(read('web/app.js')));
  assert.doesNotThrow(() => new vm.Script(read('web/link-previews.js')));
});

test('web rich preview uses inert text and never injects remote metadata HTML', () => {
  const code = read('web/link-previews.js');
  const app = read('web/app.js');
  assert.match(code, /function richLinkCard\(text\)/);
  assert.match(code, /title\.textContent =/);
  assert.match(code, /desc\.textContent =/);
  assert.match(code, /img\.referrerPolicy='no-referrer'/);
  assert.match(code, /rel="noopener noreferrer"/);
  assert.match(code, /IntersectionObserver/);
  for (const surface of ['post.text', 'comment.content', 'm.content', 's.caption', 'r.text'])
    assert.ok(app.includes('richLinkCard(' + surface), surface);
});

test('Android and Windows share one URL recognition policy', () => {
  const shared = read('shared/src/main/kotlin/com/blinkng/shared/BlinkRichLinks.kt');
  assert.match(shared, /object BlinkRichLinks/);
  assert.match(shared, /rawUserInfo != null/);
  assert.match(read('app/src/main/java/com/example/ui/components/BlinkRichLinkPreview.kt'),
    /BlinkRichLinks\.all\(text\)/);
  assert.match(read('desktopApp/src/main/kotlin/com/blinkng/desktop/ui/DesktopRichLinkPreview.kt'),
    /BlinkRichLinks\.all\(text\)/);
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
  const window = {};
  vm.runInNewContext(read('web/link-previews.js'), {
    window, URL, location: {origin:'https://blink.com.ng'}
  });
  const richLinkCard = window.BlinkLinkPreview.card;
  assert.equal(richLinkCard('javascript:alert(1)'), '');
  assert.equal(richLinkCard('https://user:password@example.com'), '');
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
