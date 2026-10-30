import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const app=fs.readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const parity=fs.readFileSync(new URL('../web/parity.js',import.meta.url),'utf8');
const start=app.indexOf('  async function signOutTo(destination)');
const end=app.indexOf('  async function login(',start);
assert.ok(start>=0 && end>start,'Account switch must use the session cleanup function');
const snippet=app.slice(start,end);
const state={session:{access_token:'prior-account-token'},feed:[{id:1}],feedAsOf:'old',searchRequestId:3};
let destination=null,cleared=0,requested=0;
const context={
  window:{},state,
  token:()=>state.session?.access_token,
  api:async()=>{requested++;throw Error('offline');},
  clearSession:()=>{cleared++;state.session=null;},
  closeModal:()=>{},
  toast:()=>{},
  navigate:(route)=>{destination=route;},
};
vm.createContext(context);
vm.runInContext(snippet,context);
await context.window.BlinkWebAccount.switchToLogin();
assert.equal(destination,'/login','Account switch must open Login');
assert.equal(cleared,1,'Session must be cleared on logout failure as well');
assert.equal(state.session,null);
assert.equal(state.feed.length,0);
assert.equal(state.feedAsOf,null);
assert.equal(state.searchRequestId,4);
assert.equal(requested,1,'Revoke previous token when possible');

// Normal logout must still go to the public landing page.
state.session={access_token:'other-token'};
await vm.runInContext('logout()',context);
assert.equal(destination,'/');
assert.equal(cleared,2);

assert.match(parity,/data-switch-account>Sign in with another account/);
assert.ok(!parity.includes('data-parity-go="/login">Sign in with another account'),'Plain navigation must not bypass logout');
assert.ok(parity.includes('window.BlinkWebAccount.switchToLogin()'),'Accounts button must call real session teardown');
console.log('Web account switching: token cleanup, offline fallback and Login navigation passed.');
