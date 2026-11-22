// Read-only graph-constrained route diagnostic: never fabricate an edge.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('function connectedRouteShadow('),end=page.indexOf('function match(j){',start);
assert(start>=0&&end>start,'connected-route shadow present');
const M=111320,p=(x,y)=>[x/M,y/M],metres=(a,b)=>Math.hypot((a[0]-b[0])*M,(a[1]-b[1])*M);
const resolve=new Function('metres',page.slice(start,end)+';return connectedRouteShadow')(metres);
const network=[
 {p:p(0,0),q:p(8,0),kind:'residential',group:'street',index:0},
 {p:p(8,0),q:p(16,0),kind:'residential',group:'street',index:1},
 {p:p(16,0),q:p(24,0),kind:'residential',group:'street',index:2},
 // Very close pub access is parallel but has no connected OSM endpoint
 {p:p(8,3),q:p(16,3),kind:'service',group:'pub-access',index:0},
 {p:p(16,3),q:p(24,3),kind:'service',group:'pub-access',index:1},
 // Real intersecting pedestrian path into station
 {p:p(24,0),q:p(24,-8),kind:'footway',group:'station-entrance',index:0},
 {p:p(24,-8),q:p(24,-16),kind:'footway',group:'station-entrance',index:1},
 // An unrelated road within the search radius but without endpoint
 {p:p(24,4),q:p(32,4),kind:'residential',group:'other-street',index:0},
 // Proper junction turn from street
 {p:p(24,0),q:p(24,8),kind:'residential',group:'junction-turn',index:0}
];
const probe=(...ids)=>({sourceSegment:1,candidates:ids.map((id,i)=>({id,score:i?1:0}))});
let r=resolve([probe(0),probe(1,3),probe(2,4)],network);
assert.deepEqual([...r.supported],[0,1,2],'connected main street survives despite nearby access proposals');
assert(!r.supported.has(3),'pub service access cannot win by proximity');
r=resolve([probe(0),probe(3),probe(4)],network);
assert.equal(r.runs.length,0,'disconnected access must not be joined into fictitious route');
assert(r.breaks>0,'missing physical connection diagnosed');
r=resolve([probe(0),probe(1),probe(2),probe(5),probe(6)],network);
assert.deepEqual([...r.supported],[0,1,2,5,6],'genuine connected pedestrian station entrance retained');
r=resolve([probe(0),probe(1),probe(2),probe(8)],network);
assert.deepEqual([...r.supported],[0,1,2,8],'physically connected road turn is eligible');
r=resolve([probe(0),probe(1),probe(2),probe()],network);
assert(r.unresolved===1,'unmatched gap remains explicit');
r=resolve([probe(0),probe(0),probe(0)],network);
assert.equal(r.supported.size,0,'stationary/repeated single interval is not a sustained route');
assert(page.includes('connectedShadows.push(connectedRouteShadow(probes,roads))'),'runtime shadow separately evaluated');
assert(page.includes('connectedRouteEvidence,canonical,'),'diagnostic does not replace canonical match');
assert(page.includes('connectedRouteDiagnostic:'),'inspector exposes diagnostic status');
assert(page.includes('id="connectedShadow" aria-pressed="false"'),'independent connected route button present');
assert(page.includes('showConnectedShadow=!showConnectedShadow'),'button toggles connected reference evidence');
assert(page.includes("stroke(lines,'#f45cba',5)"),'visible connected route overlay is separate from existing cyan and mint');
assert(page.includes('reference intervals (NOT credited)'),'button reports diagnostic rather than coverage');
console.log('PASS: continuous road, disconnected pub access, station path, genuine turn, gaps and no credit mutation');
