// Source-backed road gap recovery: all coordinates synthetic.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const a=html.indexOf('function repairEvidenceBackedRoadGaps('),b=html.indexOf('// Display-only micro-links',a);
assert(a>0&&b>a,'repair stage exists');
const M=111320,D=Math.PI/180,p=(x,y=0)=>[x/M,y/M];
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const repair=new Function('metres','xyz','proj',html.slice(a,b)+';return repairEvidenceBackedRoadGaps')(metres,xyz,proj);
const refs=[
 {p:p(0),q:p(12),kind:'residential',group:'before'},
 {p:p(12),q:p(20),kind:'residential',group:'cross-way-junction'},
 {p:p(20),q:p(32),kind:'residential',group:'after'},
 {p:p(12,6),q:p(20,6),kind:'residential',group:'parallel-street'},
 {p:p(12),q:p(12,10),kind:'footway',group:'station-entrance'},
 {p:p(32),q:p(40),kind:'residential',group:'unanchored-nib'},
 {p:p(40),q:p(48),kind:'residential',group:'unanchored'};
];
const base={ids:new Set([0,2]),sources:new Map([[0,'physical-road-centreline'],[2,'physical-road-centreline']])};
const original=[...base.ids];
const cross=repair(base,[[p(0),p(32)]],refs);
assert.deepEqual([...cross.added],[1],'real short interval across distinct OSM ways recovered');
assert.equal(cross.canonical.sources.get(1),'source-backed-junction-road');
assert.deepEqual([...base.ids],original,'input coverage remains immutable');
assert(!cross.canonical.ids.has(5),'dead-end terminal nib not auto-credited');
assert(!cross.canonical.ids.has(4),'pedestrian junction never transformed into road credit');
const noEvidence=repair(base,[[p(0),p(12)],[p(20),p(32)]],refs);
assert.equal(noEvidence.added.size,0,'nearby endpoint-only evidence cannot claim untraversed gap');
const beside=repair(base,[[p(0,6),p(32,6)]],refs);
assert.equal(beside.added.size,0,'parallel neighbouring carriageway cannot claim centreline');
const sharp=repair(base,[[p(16,-10),p(16,10)]],refs);
assert.equal(sharp.added.size,0,'perpendicular crossing cannot claim along-road section');
const tooShort=repair(base,[[p(12),p(16)]],refs);
assert.equal(tooShort.added.size,0,'partial journey traversal cannot claim complete interval');
const independent=repair({ids:new Set([0,2,4]),sources:new Map(base.sources)},[[p(0),p(32)]],refs);
assert(independent.canonical.ids.has(4),'genuine entrance remains independently credited');
assert(html.includes('auditCanonicalContinuity(canonical,roads)'),'audits repaired canonical output');
assert(html.includes('source-verified short road gaps recovered'),'diagnostics distinguish repaired intervals');
console.log('PASS: only source-traversed, doubly anchored short road gaps added; footpaths, nibbles and carriageways preserved');
