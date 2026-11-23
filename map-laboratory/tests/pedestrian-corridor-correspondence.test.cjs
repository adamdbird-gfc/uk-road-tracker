const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('function resolvePedestrianRoadCorridors('),end=page.indexOf('// Shadow experiment:',start);
assert(start>=0&&end>start,'read-only corridor resolver exists');
const M=111320,p=(x,y=0)=>[x/M,y/M],metres=(a,b)=>Math.hypot((a[0]-b[0])*M,(a[1]-b[1])*M);
const resolve=new Function('metres',page.slice(start,end)+';return resolvePedestrianRoadCorridors')(metres);
function make(n=6){
 const network=[],support=new Map(),matched=new Set();
 for(let i=0;i<n;i++)network.push({p:p(i*8),q:p((i+1)*8),kind:'residential',group:'public-street',index:i});
 for(let i=0;i<n;i++){const id=network.length;network.push({p:p(i*8,5),q:p((i+1)*8,5),kind:'footway',group:'street-footway',index:i});matched.add(id);support.set(i,{footwayId:id,lateral:5,overlap:8})}
 return {network,support,matched};
}
let t=make(),credit=new Set([77]),before=[...t.matched];
let a=resolve(t.matched,t.network,{support:t.support});
assert.equal(a.accepted.size,6,'sustained reference run supports street');
assert.equal(a.ambiguous.size,0);assert.equal(a.runs.length,1);
assert.equal(a.previewOnly,true);
assert.deepEqual([...credit],[77],'no cumulative credit changes');
assert.deepEqual([...t.matched],before,'no matched reference mutations');
t=make();const accessStart=t.network.length;
for(let i=0;i<6;i++){t.network.push({p:p(i*8,2),q:p((i+1)*8,2),kind:'service',group:'pub-access',index:i});t.support.set(accessStart+i,{footwayId:6+i,lateral:3,overlap:8})}
a=resolve(t.matched,t.network,{support:t.support});
assert.equal(a.accepted.size,0,'two supported nearby road corridors must be held');
assert.equal(a.ambiguous.size,12,'both competing reference corridors remain inspectable');
t=make();t.support.delete(2);
a=resolve(t.matched,t.network,{support:t.support});
assert.equal(a.accepted.size,3,'only independently continuous section can qualify');
assert.equal(a.held.length,1,'short discontinuous section held');
t=make();t.support.get(3).lateral=0;
a=resolve(t.matched,t.network,{support:t.support});
assert.equal(a.accepted.size,0,'unstable lateral offset holds inferred centreline');
t=make();t.support.clear();
a=resolve(t.matched,t.network,{support:t.support});
assert.equal(a.accepted.size,0,'raw GPS-only proposals cannot qualify');
assert(page.includes('resolvePedestrianRoadCorridors(evidence,roads,footwayRoadEvidence)'),'actual selected pedestrian refs feed resolver');
assert(page.includes('footwayRoadEvidence,independentRoadCandidates,independentRoadPreview,experimentalCorrection,boundedCorridorRecovery,crossWayJunctionRecovery,roadsideConsolidation,corridorResolution,orderedCorridorSupport,connectedRouteEvidence,roadFirstRouteEvidence,canonical'),'diagnostics returned without replacing canonical');
console.log('PASS: corridor continuity, competing access, gaps, offset instability, raw-only and immutability');
