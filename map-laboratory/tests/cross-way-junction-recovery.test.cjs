const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const begin=html.indexOf('function recoverCrossWayRoadJunctions('),end=html.indexOf('// Read-only corridor correspondence',begin);
assert(begin>=0&&end>begin,'cross-way recovery present');
const M=111320,p=(x,y=0)=>[x/M,y/M],metres=(a,b)=>Math.hypot((a[0]-b[0])*M,(a[1]-b[1])*M);
const xyz=(a,o)=>[(a[0]-o[0])*M,(a[1]-o[1])*M];
const recover=new Function('metres','xyz',html.slice(begin,end)+';return recoverCrossWayRoadJunctions')(metres,xyz);
const road=[
 {p:p(0,0),q:p(8,0),group:'Main Road',kind:'residential',index:0},
 {p:p(8,0),q:p(16,0),group:'Main Road',kind:'residential',index:1},
 {p:p(16,0),q:p(24,0),group:'Junction Way',kind:'unclassified',index:0},
 {p:p(24,0),q:p(32,0),group:'Junction Way',kind:'unclassified',index:1},
 {p:p(32,0),q:p(40,0),group:'Other Road',kind:'residential',index:0},
 {p:p(40,0),q:p(48,0),group:'Other Road',kind:'residential',index:1},
 {p:p(12,5),q:p(20,5),group:'Pub',kind:'service',index:0},
 {p:p(20,5),q:p(28,5),group:'Pub',kind:'service',index:1},
 {p:p(16,-10),q:p(24,-10),group:'Station',kind:'footway',index:0}
];
const baseline={ids:new Set([0,1,4,5,8]),sources:new Map([[8,'independent-path']])};
let result=recover(baseline,road,[[p(0,4),p(8,4),p(16,4),p(24,4),p(32,4),p(40,4),p(48,4)]]);
assert.deepEqual([...result.added].sort((a,b)=>a-b),[2,3],'recover missing cross-way junction pieces');
assert(result.canonical.ids.has(8),'independent station path preserved');
assert(!result.added.has(6)&&!result.added.has(7),'pub service roads never credited');
assert.deepEqual([...baseline.ids],[0,1,4,5,8],'baseline invariant');
let wrong=recover(baseline,road,[[p(0,20),p(48,20)]]);
assert.equal(wrong.added.size,0,'distant journey cannot credit junction');
wrong=recover(baseline,road,[[p(18,-12),p(18,12)]]);
assert.equal(wrong.added.size,0,'pedestrian crossing cannot credit junction');
wrong=recover(baseline,road,[[p(0,4),p(16,4)],[p(32,4),p(48,4)]]);
assert.equal(wrong.added.size,0,'GPS gap cannot be bridged by combining separate lines');
const broken=road.map(x=>({...x}));broken[2]={...broken[2],p:p(20,0)};
wrong=recover(baseline,broken,[[p(0,4),p(8,4),p(16,4),p(24,4),p(32,4),p(40,4),p(48,4)]]);
assert.equal(wrong.added.size,0,'no geometric connection means no recoverable junction');
wrong=recover({ids:new Set([0,1,8]),sources:new Map()},road,[[p(0,4),p(8,4),p(16,4),p(24,4),p(32,4),p(40,4),p(48,4)]]);
assert.equal(wrong.added.size,0,'no destination anchor means no credit');
assert(html.includes('matchPedestrianConstrainedRoadRoute(baselineCanonical'),'strict A/B gate');
assert(html.includes('const canonical=networkGlue.canonical'),'actual canonical renderer consumes correction');
assert(html.includes('crossWayJunctionRecovery:{added:'),'Inspector has attribution');
assert(html.includes('id="experimentalCoverage" aria-pressed="false"'),'A/B OFF by default');
console.log('PASS: cross-way anchored recovery, pub/station safety, GPS breaks, disconnected refs, no forced end, baseline intact');
