#!/usr/bin/env node
// Independently verify BLINK's public custom-domain delivery.
// No credentials: run only after DNS + GitHub Pages domain and HTTPS are live.
import assert from 'node:assert/strict';
import { resolve4 } from 'node:dns/promises';

const APEX = 'blink.com.ng';
const HOST = 'blink.com.ng';
const ORIGIN = `https://${HOST}`;
const EXPECTED_ADS = 'google.com, pub-9152580730716304, DIRECT, f08c47fec0942fa0';

async function checkUrl(path, validate) {
  const response = await fetch(ORIGIN + path, {
    redirect: 'follow',
    headers: { 'User-Agent': 'BLINK-domain-verifier/1.0' },
    signal: AbortSignal.timeout(12000),
  });
  assert.equal(response.status, 200, `${path} returned ${response.status}`);
  assert.equal(new URL(response.url).origin, ORIGIN, `${path} left canonical host`);
  const text = await response.text();
  validate(text, response);
  console.log(`PASS ${path}`);
}

async function main() {
  // Existence checks; do not assume a specific CDN or nameserver provider.
  for (const hostname of [APEX]) {
    const addresses = await resolve4(hostname);
    assert.ok(addresses.length > 0, `${hostname} has no public IPv4 record`);
    console.log(`PASS DNS ${hostname}: ${addresses.join(', ')}`);
  }
  const apex = await fetch(`https://${APEX}/`, {
    redirect: 'follow',
    signal: AbortSignal.timeout(12000),
  });
  assert.equal(apex.status, 200, 'Root domain must respond successfully');
  assert.equal(new URL(apex.url).origin, ORIGIN, 'Apex must remain on canonical host');
  console.log('PASS HTTPS apex canonical');

  await checkUrl('/', body => {
    assert.ok(body.includes('Blink'), 'BLINK homepage missing');
    assert.ok(body.includes('<link rel="canonical" href="https://blink.com.ng/"'), 'Canonical tag mismatch');
  });
  await checkUrl('/privacy/', body => assert.ok(body.includes('Privacy Policy'), 'Privacy document missing'));
  await checkUrl('/app-ads.txt', (body, response) => {
    assert.ok(body.split(/\r?\n/).some(x => x.trim() === EXPECTED_ADS), 'AdMob publisher line missing');
    assert.ok(!response.headers.get('content-type')?.includes('text/html'), 'app-ads.txt served as HTML');
  });
  await checkUrl('/.well-known/assetlinks.json', body => {
    const associations = JSON.parse(body);
    const record = associations.find(x => x.target?.package_name === 'com.aistudio.blink.appvtwo');
    assert.ok(record?.relation?.includes('delegate_permission/common.handle_all_urls'), 'Android App Links relation missing');
    assert.ok(record.target.sha256_cert_fingerprints.some(x =>
      /^([A-F0-9]{2}:){31}[A-F0-9]{2}$/i.test(x)
    ), 'Android production signing fingerprint invalid');
  });
  await checkUrl('/config.js', body => {
    assert.ok(body.includes('siteUrl: "https://blink.com.ng"'), 'Web Supabase OAuth redirect base incorrect');
  });
  await checkUrl('/sitemap.xml', body => {
    assert.ok(body.includes('https://blink.com.ng/privacy/'), 'Sitemap references old host');
  });
  await checkUrl('/robots.txt', body => {
    assert.ok(body.includes('https://blink.com.ng/sitemap.xml'), 'Robots references old sitemap');
  });
  await checkUrl('/manifest.webmanifest', body => {
    const data = JSON.parse(body);
    assert.equal(data.short_name, 'BLINK');
  });
  console.log('ALL public custom-domain tests passed. Dashboard/provider approvals require separate checks.');
}

main().catch(error => {
  console.error('BLINK custom-domain verification failed:', error.message);
  process.exitCode = 1;
});
