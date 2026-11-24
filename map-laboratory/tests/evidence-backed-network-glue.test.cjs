const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('function recoverSourceBackedNetworkConnections('),end=page.indexOf('// Optional local single-journey Android match evidence.',start);
assert(start>=0&&end>start,'network glue implementation present');
const M=111320,p=(x,y=0)=>[x/M,y/M],metres=(a,b)=>Math.hypot((a[0]-b[0])*M,(a[1]-b[1])*M),xyz=(a,o)=>[(a[0]-o[0])*M,(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1],t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const glue=new Function('metres','xyz','proj',page.slice(start,end)+';return recoverSourceBackedNetworkConnections')(metres,xyz,proj);
const roads=[
 {p:p(0),q:p(8),kind:'residential',group:'road-west',index:0},
 {p:p(16),q:p(24),kind:'residential',group:'road-east',index:0},
 {p:p(8),q:p(16),kind:'footway',group:'square-path',index:0},
 {p:p(8),q:p(8,8),kind:'footway',group:'unsupported-spur',index:0}
];
const base={ids:new Set([0,1]),sources:new Map([[0,'road'],[1,'road']]),independentPaths:new Set(),suppressedParallelPaths:new Set()};
let r=glue(base,roads,[[p(0),p(8),p(16),p(24)]]);
assert.deepEqual([...r.added],[2],'supported existing pedestrian reference bridges road and path corridor');
assert(r.canonical.independentPaths.has(2),'footpath connector is credited as independent pedestrian infrastructure');
assert(r.canonical.ids.has(0)&&r.canonical.ids.has(1),'both physical roads untouched');
assert(!r.added.has(3),'unsupported stub is not invented');
assert(base.ids.size===2,'original canonical input remains intact');
r=glue(base,roads,[[p(0),p(8)]]);
assert.equal(r.added.size,0,'no journey evidence means no glue');
const roadOnly=roads.map(x=>({...x}));roadOnly[2].kind='residential';
r=glue(base,roadOnly,[[p(8),p(16)]]);
assert(r.added.has(2),'short existing physical road gap can be recovered with evidence');
assert(!r.canonical.independentPaths.has(2),'road interval is never labelled pedestrian');
r=glue({...base,ids:new Set([0,1,2])},roads,[[p(8),p(16)]]);
assert.equal(r.added.size,0,'already connected route cannot be extended with spur');
const alternative=roads.concat([{p:p(8),q:p(16),kind:'path',group:'parallel-unresolved-route',index:0}]);
r=glue(base,alternative,[[p(8),p(16)]]);
assert.equal(r.added.size,0,'ambiguous parallel OSM references held');
const noEndpoint=roads.map(x=>({...x}));noEndpoint[2]={...noEndpoint[2],q:p(17,8)};
r=glue(base,noEndpoint,[[p(8),p(17,8)]]);
assert.equal(r.added.size,0,'one-ended pedestrian spur held, never extended');
// Multiple missing OSM sections, with road/pedestrian endpoints offset
// slightly from one another, should be bridged only with source evidence.
const chainNet=[
 {p:p(0),q:p(8),kind:'residential',group:'west',index:0},
 {p:p(32),q:p(40),kind:'residential',group:'east',index:0},
 {p:p(9.2),q:p(16.5),kind:'footway',group:'square-connection',index:0},
 {p:p(16.5),q:p(24.4),kind:'footway',group:'square-connection',index:1},
 {p:p(24.4),q:p(30.8),kind:'footway',group:'square-connection',index:2},
 {p:p(9.2),q:p(9.2,7),kind:'footway',group:'wrong-branch',index:0}
];
const chainBase={ids:new Set([0,1]),sources:new Map([[0,'west'],[1,'east']]),independentPaths:new Set(),suppressedParallelPaths:new Set()};
r=glue(chainBase,chainNet,[[p(0),p(40)]]);
assert.deepEqual([...r.added].sort((a,b)=>a-b),[2,3,4],'source supported three-link passage joins offset road endpoints');
assert.equal(r.diagnostics.bridgedPairs,1);
assert(!r.added.has(5),'one-ended spur never extends a credited corridor');
assert(chainBase.ids.size===2,'original credited network preserved');
r=glue(chainBase,chainNet,[[p(0),p(8)]]);
assert.equal(r.added.size,0,'missing source leg cannot justify three-link glue');
const twoWays=chainNet.concat([
 {p:p(9.2),q:p(16.5),kind:'path',group:'competing-square-path',index:0}
]);
r=glue(chainBase,twoWays,[[p(0),p(40)]]);
assert.equal(r.added.size,0,'equally source-supported competing link holds the whole ambiguous chain');
const noSecondAnchor=chainNet.map(x=>({...x}));noSecondAnchor[4]={...noSecondAnchor[4],q:p(29,10)};
r=glue(chainBase,noSecondAnchor,[[p(0),p(40)]]);
assert.equal(r.added.size,0,'do not extend chain towards a nearby destination without a second anchor');
assert(page.includes('const networkGlue=experimentalCorrection.enabled?recoverSourceBackedNetworkConnections('),'experimental mode only');
assert(page.includes('const canonical=networkGlue.canonical'),'glue becomes actual canonical coverage');
assert(page.includes('LAB r449')&&page.includes('>PR #449</a>'),'visible build revision is consistent');
console.log('PASS: source-backed bounded road/path glue, branch rejection, ambiguous alternatives and baseline invariance');
