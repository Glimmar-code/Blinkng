import fs from 'node:fs';
import assert from 'node:assert/strict';

const parity=fs.readFileSync(new URL('../web/parity.js',import.meta.url),'utf8');
const app=fs.readFileSync(new URL('../web/app.js',import.meta.url),'utf8');
const menuBlock=parity.match(/const WEB_MENU = \[([\s\S]*?)\];/)?.[1];
assert.ok(menuBlock,'A curated list of web destinations is required');
const routes=[...menuBlock.matchAll(/route:'([^']+)'/g)].map(m=>m[1]);
assert.ok(routes.length>=10,'Web menu should provide real destinations');
assert.equal(new Set(routes).size,routes.length,'Duplicate web destinations');
for(const route of routes){
  assert.ok(app.includes(`path==='${route}'`)||app.includes(`path.startsWith('${route}'`)||parity.includes(`'${route}'`),`Unknown route: ${route}`);
}
assert.ok(parity.includes("shell('Explore Blink Web'"),'Use an accurate feature-menu heading');
assert.ok(parity.includes('WEB_MENU.filter('),'Both menu and command palette must search curated web routes');
assert.ok(!parity.includes("shell('500 app features on web'"),'Do not advertise internal checks as features');
assert.ok(!parity.includes('data-parity-go="/more">500'),'Do not show the misleading count in the header');
assert.ok(!parity.includes('decorateAndroidUiParityDock();'),'Engineering parity dock must not appear in public UI');
assert.ok(!parity.includes('queueMicrotask(decorateAndroidUiParityDock)'),'Do not restore engineering dock on page renders');
assert.ok(!parity.includes('Search 500 features'),'Command palette must not advertise 500 features');
console.log(`Web feature menu: ${routes.length} curated destinations, no false count or engineering dock.`);
