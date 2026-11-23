const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const a=page.indexOf('function sourceBackedEndpointRenderLinks('),b=page.indexOf('// A nearby footway interval must actually overlap',a);
assert(a>0&&b>a);
const M=111320,p=(x,y=0)=>[x/M,y/M],metres=(a,b)=>Math.hypot((a[0]-b[0])*M,(a[1]-b[1])*M),xyz=(a,o)=>[(a[0]-o[0])*M,(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1],t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const link=new Function('metres','xyz','proj',page.slice(a,b)+';return sourceBackedEndpointRenderLinks')(metres,xyz,proj);
const network=[{p:p(0),q:p(10),group:'road',kind:'residential'},
 {p:p(18,1),q:p(27,1),group:'square',kind:'footway'},
 {p:p(10),q:p(10,8),group:'spur',kind:'footway'}];
const base={ids:new Set([0,1]),independentPaths:new Set([1]),sources:new Map([[0,'road'],[1,'path']])};
let result=link(base,network,[[p(0),p(10),p(18,1),p(27,1)]]);
assert(result.links.length>=1,'missing road-to-square footway join gets source-backed visual connection');
assert.equal(base.ids.size,2,'no new cumulative coverage awarded');
assert.deepEqual([...base.ids],[0,1],'canonical remains unchanged');
result=link(base,network,[[p(0),p(10)]]);
assert.equal(result.links.length,0,'no source route, no visual interpolation');
result=link(base,network,[[p(0),p(50,50)]]);
assert.equal(result.links.length,0,'nearby OSM endpoints alone cannot invent a link');
// Sparse recording with a bend: neither single GPS leg supports both
// network endpoints, but two consecutive source legs trace the connection.
const bent=[{p:p(0,0),q:p(10,0),group:'street',kind:'residential'},
 {p:p(18,10),q:p(27,10),group:'square',kind:'footway'}];
result=link(base,bent,[[p(0,0),p(10,0),p(10,10),p(18,10),p(27,10)]]);
assert(result.links.length>=2,'consecutive source legs should preserve a bent junction connection');
assert.equal(base.ids.size,2,'multi-leg visual continuity must never credit additional roads');
result=link(base,bent,[[p(0,0),p(10,0)],[p(10,10),p(18,10),p(27,10)]]);
assert.equal(result.links.length,0,'must not join across separate source polylines');
// Road-to-road gap supported by pedestrian evidence with a lateral GPS
// detour must still render on the physical road centreline.
const roads=[{p:p(0,0),q:p(10,0),group:'high-street',kind:'residential'},
 {p:p(18,0),q:p(28,0),group:'high-street',kind:'residential'}];
result=link(base,roads,[[p(0,0),p(10,0),p(14,3),p(18,0),p(28,0)]]);
assert.deepEqual(result.links,[[p(10,0),p(18,0)]],'road corridor stays straight despite pavement-offset GPS vertex');
assert.equal(base.ids.size,2,'road-centreline display join must not award new coverage');
// A genuine bend into a pedestrian-only corridor retains recorded shape.
result=link(base,bent,[[p(0,0),p(10,0),p(10,10),p(18,10),p(27,10)]]);
assert(result.links.some(([a,b])=>Math.abs(a[1]-b[1])>1/M),'genuine footway turn remains available');
// Not all road-to-road transitions are straight: reject blind straightening
// when the endpoint tangents do not align with the proposed connector.
const roadTurn=[roads[0],{p:p(18,8),q:p(18,18),group:'side-road',kind:'residential'}];
result=link(base,roadTurn,[[p(0,0),p(10,0),p(10,8),p(18,8),p(18,18)]]);
assert(!result.links.some(([a,b])=>metres(a,p(10,0))<.1&&metres(b,p(18,8))<.1),'turning road cannot be simplified to an unsupported diagonal');
const far=[network[0],{...network[1],p:p(55,0),q:p(64,0)}];
result=link(base,far,[[p(0),p(64)]]);
assert.equal(result.links.length,0,'long unsupported reference gaps do not qualify');
const joined=[network[0],{...network[1],p:p(10,0),q:p(27,1)}];
result=link(base,joined,[[p(0),p(27,1)]]);
assert.equal(result.links.length,0,'already touching accepted edges require no rendered bridge');
assert(page.includes('const visualContinuity=experimentalCorrection.enabled?sourceBackedEndpointRenderLinks('),'OFF remains unchanged');
assert(page.includes('stroke(connectors,\'#ff4b55\',3.2)'),'display-only links are rendered');
assert(page.includes('LAB r431')&&page.includes('>PR #431</a>'));
console.log('PASS: source-backed visual road-to-footway joins, no invented coverage, no-source/far/connected guards');
