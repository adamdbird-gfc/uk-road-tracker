// Source-backed entrance links are visual evidence, not extra road credit.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const begin=page.indexOf('function evidenceBackedWalkingLinks('),end=page.indexOf('function match(j){',begin);
assert.ok(begin>0&&end>begin);
const M=111320,D=Math.PI/180;
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const infer=new Function('xyz','metres','proj',page.slice(begin,end)+';return evidenceBackedWalkingLinks')(xyz,metres,proj);
const p=(x,y=0)=>[x/M,y/M],roads=[];
for(let i=0;i<8;i++)roads.push({p:p(i*8),q:p((i+1)*8),kind:'residential',group:'main',first:0});
for(let i=0;i<8;i++)roads.push({p:p(i*8,10),q:p((i+1)*8,10),kind:'footway',group:'frontage',first:Infinity});
const ids=new Set(Array.from({length:8},(_,i)=>i+8)),before=ids.size;
const links=infer([[p(40,0),p(40,10)]],ids,roads);
assert.equal(links.length,1,'accepted source with crossing from road to frontage creates a display link');
assert.equal(ids.size,before,'candidate never adds physical road completion');
assert.equal(infer([[p(30,10),p(42,10)]],ids,roads).length,0,'parallel path alone cannot create crossing');
assert.equal(infer([[p(40,0),p(40,25)]],ids,roads).length,0,'long unseen connection must not be invented');
assert.equal(infer([[p(40,0),p(40,10)]],new Set(),roads).length,0,'no verified frontage means no link');
// A multi-leg entry (the common road -> entrance -> frontage pattern)
// must meet the mapped surfaces at both ends rather than leave floating ink.
const multi=infer([[p(40,0),p(40,9),p(40,10)]],ids,roads);
assert.ok(multi.length>=1,'source-supported multi-leg connection is rendered');
assert.ok(metres(multi[0][0],p(40,0))<.5,'red link touches the covered road centreline');
assert.ok(metres(multi[multi.length-1][1],p(40,10))<.5,'red link touches frontage reference');
assert.equal(infer([[p(40,0),p(40,22),p(40,35)]],ids,roads).length,0,'unsupported distant excursion is not drawn');
// An incoming source leg may run along an ALREADY COVERED roadway
// before turning onto a newly traversed frontage path. The visual
// connection must not redraw that pre-covered roadway in bright red.
const alongside=infer([[p(0,0),p(30,0),p(40,0),p(40,10)]],ids,roads);
assert.ok(alongside.some(([a,b])=>metres(a,p(40,0))<.5&&metres(b,p(40,10))<.5),'retain actual perpendicular entrance');
assert.ok(alongside.every(([a,b])=>Math.abs(a[0]-b[0])*M<1),'never repaint previously covered along-road segments as a new visual link');
assert.equal(ids.size,before,'display correction never adds new road credit');
console.log('PASS: connection retained without redrawing previously credited road corridors');
