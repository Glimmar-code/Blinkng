// Mocked Edge Function request tests: no hosted calls and no real credentials.
// Node 24 strips TypeScript annotations from the exact deployed function source.
import test from 'node:test';
import assert from 'node:assert/strict';

const base = 'https://test-contest.supabase.co';
const owner = '11111111-1111-4111-8111-111111111111';
const outsider = '22222222-2222-4222-8222-222222222222';
const admin = '33333333-3333-4333-8333-333333333333';
const file = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg';
const path = owner + '/' + file;
const endpoint = base + '/functions/v1/blink-contest-private-media/' + path;
let handler;
let record = { id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', user_id: owner, status: 'pending' };
let signedCalls = 0;
let signedLocation = '/object/sign/blink-contest-private/' + path + '?token=valid-signed-token';
const tokenOwners = { 'owner-jwt': owner, 'admin-jwt': admin, 'outsider-jwt': outsider };

globalThis.Deno = {
  env: { get: key => ({
    SUPABASE_URL: base,
    SUPABASE_SERVICE_ROLE_KEY: 'sb_secret_test_only',
    SUPABASE_ANON_KEY: 'sb_publishable_test_only',
  })[key] || '' },
  serve: fn => { handler = fn; },
};
globalThis.fetch = async (input, init = {}) => {
  const url = new URL(String(input));
  if (url.pathname === '/rest/v1/blink_contest_entries') {
    if (url.searchParams.get('media_url') !== 'eq.' + endpoint) return Response.json([]);
    return Response.json(record ? [record] : []);
  }
  if (url.pathname === '/auth/v1/user') {
    const token = new Headers(init.headers).get('Authorization')?.replace('Bearer ', '') || '';
    const id = tokenOwners[token];
    return id ? Response.json({ id }) : Response.json({}, { status: 401 });
  }
  if (url.pathname === '/rest/v1/rpc/admin_list_blink_contests') {
    const token = new Headers(init.headers).get('Authorization')?.replace('Bearer ', '') || '';
    return Response.json([], { status: token === 'admin-jwt' ? 200 : 403 });
  }
  if (url.pathname === '/storage/v1/object/sign/blink-contest-private/' + path) {
    signedCalls++;
    return Response.json({ signedURL: signedLocation });
  }
  throw new Error('Unexpected mocked outbound request: ' + url);
};

// Load the real deployment entrypoint, never a copied implementation.
await import('../../functions/blink-contest-private-media/index.ts');
assert.equal(typeof handler, 'function');

function request(jwt = null, url = endpoint) {
  const headers = jwt ? { Authorization: 'Bearer ' + jwt } : {};
  return handler(new Request(url, { method: 'GET', headers }));
}

test('pending photo rejects anonymous, another account and malformed links', async () => {
  record.status = 'pending';
  signedCalls = 0;
  assert.equal((await request()).status, 404);
  assert.equal((await request('outsider-jwt')).status, 404);
  assert.equal((await request('invalid-jwt')).status, 404);
  assert.equal((await request(null, base + '/functions/v1/blink-contest-private-media/../../etc/passwd')).status, 404);
  assert.equal(signedCalls, 0, 'no signed URL may be issued to unauthorized users');
});

test('pending entry only opens for the owner or verified admin', async () => {
  record.status = 'pending';
  const owned = await request('owner-jwt');
  assert.equal(owned.status, 302);
  assert.match(owned.headers.get('location') || '', /\/storage\/v1\/object\/sign\/blink-contest-private\//);
  const administrative = await request('admin-jwt');
  assert.equal(administrative.status, 302);
  assert.match(administrative.headers.get('Cache-Control') || '', /no-store/);
});

test('approved entries are available without user JWT, then close on rejection', async () => {
  record.status = 'approved';
  assert.equal((await request()).status, 302);
  record.status = 'rejected';
  assert.equal((await request()).status, 404);
  record = null;
  assert.equal((await request('admin-jwt')).status, 404);
});

test('signed redirect must remain on trusted Supabase Storage host', async () => {
  record = { id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', user_id: owner, status: 'approved' };
  signedLocation = 'https://attacker.example/phish?token=stolen';
  assert.equal((await request()).status, 404);
});
