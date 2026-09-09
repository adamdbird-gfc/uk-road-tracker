#!/usr/bin/env node
import {readFileSync} from 'node:fs';

const app=readFileSync('app.js','utf8');
const html=readFileSync('index.html','utf8');
const collection=JSON.parse(readFileSync('collections/uk-motorway-services-v1.json','utf8'));
const failures=[];

for (const required of [
  'const visit=seg?.visit || seg?.placeVisit;',
  'function matchedMotorwayServiceVisits()',
  'const ROADSIDE_PACK_ID =',
  'function openRoadsidePackPreview()'
]) {
  if (!app.includes(required)) failures.push(`Roadside Pack regression contract missing: ${required}`);
}
if (!html.includes('Unlock for testing')) failures.push('Roadside Pack preview must retain its testing unlock action.');
if (collection.version!=='v1' || !Array.isArray(collection.services) ||
  collection.services.length<60 || collection.services.length>180) {
  failures.push('Motorway services collection must contain a plausible single-tick UK reference set.');
}
const corley=collection.services.filter(service=>/corley/i.test(service.name || ''));
if (corley.length!==1 || !Array.isArray(corley[0]?.points) || corley[0].points.length<2) {
  failures.push('Corley must remain one service-area tick with both carriageway locations.');
}
if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log('Roadside Pack regression checks passed.');
