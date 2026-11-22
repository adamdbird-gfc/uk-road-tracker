// No private GPS data: synthetic segments test final graph connectivity.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('function auditCanonicalContinuity('),end=page.indexOf('// Display-only micro-links',start);
assert(start>=0&&end>start,'post-canonical graph audit exists');
const M=111320,D=Math.PI/180,p=(x,y=0)=>[x/M,y/M];
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const audit=new Function('metres',page.slice(start,end)+';return auditCanonicalContinuity')(metres);
const refs=[
 {p:p(0),q:p(8)}, {p:p(8),q:p(16)}, {p:p(16),q:p(24)},
 {p:p(40),q:p(48)}, {p:p(48),q:p(56)},
 {p:p(0,5),q:p(8,5)},
 {p:p(24),q:p(40)}, // physical street interval absent from first replay
 {p:p(24,0),q:p(24,12)} // separate entrance attached to the main road
];
const initial=new Set([0,1,2,3,4]),snapshot=[...initial];
const before=audit({ids:initial},refs);
assert.equal(before.components,2,'gap between canonical road runs is reported');
assert.deepEqual(before.componentSizes,[3,2]);
assert.equal(before.unconnectedEndpoints,4,'each open road run has two free endpoints');
assert.deepEqual([...initial],snapshot,'inspection never awards coverage');
const joined=audit({ids:new Set([0,1,2,3,4,6])},refs);
assert.equal(joined.components,1,'adding an independently confirmed road interval reconnects the physical corridor');
assert.equal(joined.unconnectedEndpoints,2,'only legitimate route endpoints remain');
const independent=audit({ids:new Set([0,1,2,3,4,5])},refs);
assert.equal(independent.components,3,'parallel nearby path cannot be silently treated as the road');
const entrance=audit({ids:new Set([0,1,2,7])},refs);
assert.equal(entrance.components,1,'genuine entrance junction connects at its shared endpoint');
const none=audit({ids:new Set()},refs);
assert.equal(none.components,0);
assert.equal(none.unconnectedEndpoints,0);
assert(page.includes('canonicalContinuity=auditCanonicalContinuity(canonical,roads)'),'runtime audits after canonicalization');
assert(page.includes('canonicalContinuity.components'),'result exposes audited component count');
console.log('PASS: post-canonical connected road, missing gap, separate parallel path, genuine entrance and no credited filler');
