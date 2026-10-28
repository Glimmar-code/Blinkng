#!/usr/bin/env node
/* Regression coverage for the APK-first branded public homepage. */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync('web/landing-relaunch.js', 'utf8');
const css = readFileSync('web/landing-relaunch.css', 'utf8');
const html = readFileSync('web/index.html', 'utf8');
const sw = readFileSync('web/sw.js', 'utf8');
const apk = 'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk';

assert.match(html, /landing-relaunch\.css/);
assert.match(html, /landing-relaunch\.js/);
assert.match(sw, /'\.\/landing-relaunch\.css'/);
assert.match(sw, /'\.\/landing-relaunch\.js'/);
assert.match(css, /\.br-hero/);
assert.match(css, /@media\(max-width:480px\)/);
assert.match(css, /prefers-reduced-motion:reduce/);
assert.match(css, /focus-visible/);

function renderAt(pathname) {
  const details = { open: false, scrollIntoView(){ this.scrolled = true; } };
  const retained = {children:[],append(node){this.children.push(node);}};
  const landing = {
    className: 'landing-page',
    innerHTML: '',
    attrs: {},
    setAttribute(name,value){this.attrs[name]=value;},
    querySelector(selector) { if(selector==='#blink-read-more')return details; if(selector==='#br-retained')return retained; return null; },
    querySelectorAll() { return []; },
    addEventListener(name,listener) { if(name==='click')this.onClick=listener; }
  };
  const classSet = values => {
    const current = new Set(values);
    return {
      add(value){ current.add(value); },
      remove(value){ current.delete(value); },
      contains(value){ return current.has(value); }
    };
  };
  const root = {
    classList: classSet(['boot']),
    querySelector(selector) {
      return selector.includes('.landing-page') && landing.className === 'landing-page' ? landing : null;
    }
  };
  const body = {classList: classSet([])};
  const state = { root, body, landing, details, retained, mutation: null };
  const context = {
    document: {body, getElementById: id => id==='app' ? root : null},
    location: {pathname},
    window: {matchMedia: ()=>({matches:true}),addEventListener(){}},
    MutationObserver: class {
      constructor(callback) {state.mutation=callback;}
      observe(target,options) {
        assert.equal(target,root);
        assert.equal(options.childList,true);
      }
    }
  };
  vm.runInNewContext(source, context, {filename:'landing-relaunch.js'});
  return state;
}
const home=renderAt('/');
assert.equal(home.landing.className,'br-page');
assert.equal(home.root.classList.contains('boot'),false,'Loading-screen grid must not leak into homepage');
assert.equal(home.body.classList.contains('br-marketing-active'),true,'Public marketing page must use scoped styling');
assert.match(css, /body\.br-marketing-active #parity-network-pill/, 'Floating network badge must not cover phone previews');
assert.match(css, /\.br-copy, \.br-copy h1, \.br-description/, 'Mobile hero heading and copy must align consistently');
for(const marker of [
  'blink-logo-v2.png','Connect,','Discover and','Beyond LINK',
  'Download BLINK APK','Read more about BLINK','BLINK Nigeria',
  'Illustrative app previews','./features/','./about/','./students/',
  './marketplace/','./privacy/','./faq/'
]) assert.ok(home.landing.innerHTML.includes(marker), 'Missing homepage element: '+marker);
assert.ok(home.landing.innerHTML.includes(apk),'Official APK alias must remain');
assert.ok(!/play\.google\.com|Google Play|Play Store/.test(home.landing.innerHTML),'No Play Store marketing before store release');
home.landing.onClick({
  target: {closest: selector => selector==='[data-reveal]' ? ({dataset:{reveal:''}}) : null},
  preventDefault() {}
});
assert.equal(home.details.open,true,'Read more button must reveal existing SEO detail');
assert.equal(home.details.scrolled,true,'Read more must move into view');
const deepLink=renderAt('/post/11111111-1111-1111-1111-111111111111');
assert.equal(deepLink.landing.className,'landing-page','Do not override private/shared deep links');
const login=renderAt('/login');
assert.equal(login.landing.className,'landing-page','Do not override app login');
console.log('PASS: BLINK public landing brand, APK link, accessible read-more and route isolation');