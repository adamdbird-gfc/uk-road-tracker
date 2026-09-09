#!/usr/bin/env node
import {readFileSync} from 'node:fs';

const app=readFileSync('app.js','utf8');
const index=JSON.parse(readFileSync('canonical-a-roads-v4/index.json','utf8'));
const failures=[];

const mustContain=[
  'coverage:[...road.coveredAnchorIds].sort((a,b)=>a-b)',
  'const storedCoverage=Array.isArray(stored.coverage)',
  "const anchorBudget=zoom<7 ? 18000 : zoom<9 ? 24000 : 30000;",
  "function canonicalARoadGeometry(paths,fallbackAnchors=[])",
  "function sampledCanonicalARoadPath(path,stride)",
  "short link can never become a dot",
  "lineCap:'round',lineJoin:'round'",
  "color:covered?'#32c96b':'#d93a3a'",
  "dataset.activeScreen!=='map'",
  'async function hydrateCanonicalARoadsForMap()'
];
for (const needle of mustContain) {
  if (!app.includes(needle)) failures.push(`A-road regression contract missing: ${needle}`);
}
const a1026=index.roads?.['GB:A1026'];
if (!a1026?.file || !Number.isFinite(Number(a1026.total_km)) || Number(a1026.total_km)<=0) {
  failures.push('A1026 must remain a valid static A-road reference.');
}
if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log('A-road regression checks passed.');
