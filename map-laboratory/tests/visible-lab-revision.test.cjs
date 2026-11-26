const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const header=page.match(/<body><header>([\s\S]*?)<\/header>/)?.[1];
assert(header,'header exists');
assert(header.includes('id="labBuild"'),'revision appears directly in header above map');
assert(header.includes('LAB r490'),'loaded revision is unambiguous');
assert(header.includes('26 Nov 2026'),'human-readable revision date is visible');
assert(header.includes('pull/490'),'revision links to the exact change');
assert(header.includes('>PR #490</a>'),'displayed PR number agrees with linked PR');
assert(header.includes('Loaded page version'),'label does not claim GitHub Pages deployment freshness');
assert(header.includes('aria-label="Laboratory code revision loaded in this browser"'),'revision accessible');
const css=page.split('<style>')[1].split('</style>')[0];
assert(css.includes('#labBuild{display:flex'),'small wrapped revision remains mobile-visible');
assert(css.includes('font-size:11px'),'revision unobtrusive on mobile');
assert(!page.includes('fetch("https://github.com')&&!page.includes('github API'), 'version does not depend on external network requests or source metadata');
console.log('PASS: persistent screenshot-visible Laboratory code revision, link and accessible mobile label');

assert.match(page,/<div id="map"><\/div>\s*<div id="buttons">/,
 'replay navigation sits immediately under the map');
assert.equal([...page.matchAll(/id="(?:back|next)"/g)].length,2,
 'the previous and next buttons keep unique event targets');
