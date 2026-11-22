// Physical corridor contract. Synthetic geometry only; no owner archive or locations.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const begin=html.indexOf('function promoteParallelRoadCentrelines(');
const end=html.indexOf('// Display-only micro-links',begin);
assert(begin>=0&&end>begin,'canonical coverage helpers present');
const M=111320,D=Math.PI/180,pt=(x,y=0)=>[x/M,y/M];
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const {canonicalizePhysicalCoverage,promoteParallelRoadCentrelines,roadEquivalentFootways}=
 new Function('xyz','proj',html.slice(begin,end)+';return {canonicalizePhysicalCoverage,promoteParallelRoadCentrelines,roadEquivalentFootways}')(xyz,proj);
const refs=[];
for(let i=0;i<8;i++)refs.push({p:pt(i*8),q:pt((i+1)*8),kind:'residential',group:'street',index:i,first:Infinity});
for(let i=0;i<8;i++)refs.push({p:pt(i*8,4),q:pt((i+1)*8,4),kind:'footway',group:'pavement-one',index:i,first:Infinity});
for(let i=0;i<8;i++)refs.push({p:pt(i*8,-4),q:pt((i+1)*8,-4),kind:'footway',group:'pavement-two',index:i,first:Infinity});
for(let i=0;i<8;i++)refs.push({p:pt(i*8,18),q:pt((i+1)*8,18),kind:'footway',group:'separate-frontage',index:i,first:Infinity});
const entrance=refs.push({p:pt(24,4),q:pt(24,18),kind:'footway',group:'entrance',index:0,first:Infinity})-1;
const current=new Set([...Array.from({length:8},(_,i)=>8+i),...Array.from({length:8},(_,i)=>16+i),...Array.from({length:8},(_,i)=>24+i),entrance]);
const frozen=[...current];
const got=canonicalizePhysicalCoverage(current,refs,true);
assert.equal(got.ids.size,17,'one centreline, one distinct frontage, one connecting path');
for(let i=0;i<8;i++){
 assert(got.ids.has(i),'physical street credited at centreline');
 assert(!got.ids.has(8+i)&&!got.ids.has(16+i),'both pavements consolidate into street');
 assert(got.ids.has(24+i),'independent frontage remains');
}
assert(got.ids.has(entrance),'perpendicular pedestrian connector survives');
assert.equal(got.suppressedParallelPaths.size,16);
assert.equal(got.independentPaths.size,9);
assert.equal(got.sources.get(0),'physical-road-centreline');
assert.equal(got.sources.get(entrance),'independent-path');
assert.deepEqual([...current],frozen,'canonicalization never edits source match IDs');
assert(refs.every(r=>r.first===Infinity),'source reference chronology remains unchanged');
// Preserve the pre-refactor pipeline's accepted output across the same fixture.
const legacyIds=new Set(current),promoted=promoteParallelRoadCentrelines(legacyIds,refs);
for(const id of promoted.promoted)legacyIds.add(id);
for(const id of promoted.equivalent)legacyIds.delete(id);
for(const id of roadEquivalentFootways(legacyIds,refs))legacyIds.delete(id);
assert.deepEqual([...got.ids].sort((a,b)=>a-b),[...legacyIds].sort((a,b)=>a-b),'accepted first-stage coverage unchanged');
// If roads were already credited yesterday, today’s matching pavement must
// not appear as a second new physical corridor.
for(let i=0;i<8;i++)refs[i].first=0;
const repeats=canonicalizePhysicalCoverage(new Set([8,9,10,11]),refs,false);
assert.equal(repeats.ids.size,0,'already credited road receives no duplicate pavement credit');
assert.equal(repeats.suppressedParallelPaths.size,4);
// A genuinely separate station entrance must remain independently credited.
const station=canonicalizePhysicalCoverage(new Set([entrance]),refs,false);
assert(station.ids.has(entrance),'distinct entrance retained');
assert.equal(station.suppressedParallelPaths.size,0);
console.log('PASS: one canonical road, both pavements consolidated, distinct paths and chronology preserved');
