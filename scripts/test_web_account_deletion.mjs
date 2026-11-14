import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const parity = readFileSync('web/parity.js', 'utf8');
const start = parity.indexOf('  async function renderDataSettings()');
const end = parity.indexOf('  async function renderAccounts()', start);
assert.ok(start >= 0 && end > start, 'web data settings route must exist');
const section = parity.slice(start, end);
assert.match(section, /data-delete-account/, 'web data settings must expose account deletion');
assert.match(section, /delete_my_account/, 'web deletion must call the server deletion RPC');
assert.match(section, /p_confirmation/, 'web deletion must send the confirmation username');
assert.match(section, /saveSession\(null\)/, 'web deletion must clear the local session');
assert.match(section, /go\('\/login'\)/, 'web deletion must return to login after success');
assert.match(section, /replace\(\/\^@\/,''\)/, 'web deletion must accept an optional leading @');
console.log('PASS: authenticated web account deletion is exposed, confirmed, and session-clearing.');
