const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const begin=page.indexOf('function matchPedestrianConstrainedRoadRoute('),end=page.indexOf('// Read-only corridor correspondence',begin);
assert(begin>=0&&end>begin,'source-ordered route matching is implemented');
const M=111320,p=(x,y=0)=>[x/M,y/M],metres=(a,b)=>Math.hypot((a[0]-b[0])*M,(a[1]-b[1])*M);
const xyz=(a,o)=>[(a[0]-o[0])*M,(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1],t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const match=new Function('metres','xyz','proj',page.slice(begin,end)+';return matchPedestrianConstrainedRoadRoute')(metres,xyz,proj);
const build=(n=9,y=0)=>Array.from({length:n},(_,i)=>({p:p(i*8,y),q:p((i+1)*8,y),group:i<4?'First street':'Second street',index:i,kind:'residential',oneway:'yes'}));
const source=(y=4)=>[Array.from({length:10},(_,i)=>p(i*8,y))];
const network=build();
network.push({p:p(72,-8),q:p(72,-16),group:'station entrance',kind:'footway'});
network.push({p:p(16,4),q:p(24,4),group:'pub access',kind:'service'});
const baseline={ids:new Set([9]),sources:new Map([[9,'independent-path']])};
let r=match(baseline,network,source());
assert(r.added.size>=6,'source-aligned road recoverable without pre-credited road anchors');
assert(r.added.has(3)&&r.added.has(4),'continuous street crossing OSM-way boundary is retained');
assert(r.canonical.ids.has(9),'independent pedestrian station path stays in canonical coverage');
assert(!r.added.has(10),'pub service road is never substituted');
assert.deepEqual([...baseline.ids],[9],'baseline remains untouched');
assert(r.canonical.sources.get(4)==='experimental-pedestrian-constrained-road-route');
r=match(baseline,network,source(35));
assert.equal(r.added.size,0,'distant pedestrian-only shortcut is never mapped onto adjacent road');
r=match(baseline,network,[[p(0,4),p(24,4)],[p(48,4),p(72,4)]]);
assert(!r.runs.some(run=>run.span[1]-run.span[0]>8),'separate source lines cannot be stitched');
const duplicate=[...build(9,0),...build(9,8)];
r=match({ids:new Set(),sources:new Map()},duplicate,source(4));
assert.equal(r.added.size,0,'equal parallel physical-road alternatives must remain unresolved');
const isolatedFoot=[{p:p(0,0),q:p(72,0),group:'foot-only',kind:'footway'}];
r=match(baseline,isolatedFoot,source());
assert.equal(r.added.size,0,'actual pedestrian-only movement never manufactures roads');
const separateNetwork=[...build(9,0),...build(9,100).map(x=>({...x,p:p(x.p[0]*M,100),q:p(x.q[0]*M,100)}))];
const separateSource=[...source(4),...source(104)];
r=match({ids:new Set(),sources:new Map()},separateNetwork,separateSource);
assert([...r.added].some(id=>id<9)&&[...r.added].some(id=>id>=9),'separate journeys in one source should not be considered competing same-span road routes');
// One real pedestrian trajectory can momentarily bend away from both
// OSM road headings at a corner and return. It should not sever a
// continuous, genuinely endpoint-connected public-road corridor.
const bendNetwork=build(10);
const bendPath=[[p(0,4),p(8,4),p(16,4),p(24,4),p(32,4),p(40,4),p(40,9),p(48,4),p(56,4),p(64,4),p(72,4),p(80,4)]];
r=match({ids:new Set(),sources:new Map()},bendNetwork,bendPath);
assert(r.added.has(3)&&r.added.has(6),'real roadside route continues after short corner bend');
assert(r.held.junctionResumed>0,'an actual endpoint-local mismatch was held then rejoined');
const stationDeparture=[[p(0,4),p(8,4),p(16,4),p(24,4),p(32,4),p(40,4),p(40,9),p(40,20),p(40,30),p(40,40)]];
r=match({ids:new Set(),sources:new Map()},bendNetwork,stationDeparture);
assert(![...r.added].some(id=>id>=7),'long pedestrian departure never extends road coverage');
// Crowded urban junction: nine locally nearer short side-road references
// must not eject the established, continuous public road from the sample
// shortlist. These decoys are not connected to the surrounding main road.
const crowded=build(12,8);
for(let i=0;i<12;i++){
 const y=3.1+i*.07;
 crowded.push({p:p(24,y),q:p(32,y),kind:'residential',group:'unconnected decoy '+i,index:0});
}
r=match({ids:new Set(),sources:new Map()},crowded,[Array.from({length:13},(_,i)=>p(i*8,4))]);
assert(r.held.continuityCandidatesRetained>0,'connected street is explicitly preserved beyond the local top-nine cutoff');
assert(r.added.has(2)&&r.added.has(3)&&r.added.has(4)&&r.added.has(5),'continuous reference road survives short-lived closer junction candidates');
assert(![...r.added].some(id=>id>=12),'unconnected dead-end alternatives not falsely credited');
assert(r.held.alternativeStatesRetained>=0,'bounded alternative-state telemetry available');
assert(page.includes('matchPedestrianConstrainedRoadRoute(baselineCanonical,roads,prepared.lines)'),'experimental pipeline uses route-level matching');
assert(page.includes('const canonical=roadsideConsolidation.canonical'),'canonical result is real route output');
assert(page.includes('id="experimentalCoverage" aria-pressed="false"'),'experiment defaults off');
assert(!page.includes('experimentalCorrection.enabled?recoverCrossWayRoadJunctions('),'legacy extra recovery chain not executed');
console.log('PASS: anchored-free roadside alignment, cross-way connectivity, one-way independence, pub/station protection, ambiguity and archive safety');
