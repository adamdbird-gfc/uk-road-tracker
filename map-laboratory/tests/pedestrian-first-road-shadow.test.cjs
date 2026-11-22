// Independent pedestrian-route-to-road shadow model. Synthetic coordinates only.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('function comparePedestrianRoadCentrelines('),end=page.indexOf('// Display-only micro-links',start);
assert(start>=0&&end>start,'pedestrian-first comparator found');
const M=111320,D=Math.PI/180,p=(x,y=0)=>[x/M,y/M];
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const compare=new Function('metres','xyz','proj','M','D',page.slice(start,end)+';return comparePedestrianRoadCentrelines')(metres,xyz,proj,M,D);
const roads=[];
for(let i=0;i<8;i++)roads.push({p:p(i*8,0),q:p((i+1)*8,0),group:'street',index:i,kind:'residential'});
for(let i=0;i<8;i++)roads.push({p:p(i*8,18),q:p((i+1)*8,18),group:'separate-street',index:i,kind:'residential'});
roads.push({p:p(32,-10),q:p(32,10),group:'crossing',index:0,kind:'service'});
roads.push({p:p(0,5),q:p(64,5),group:'pavement',index:0,kind:'footway'});
const source=[[p(0,5),p(32,5),p(64,5)]],existing=new Set([0,1]);
const before=[...existing];
const result=compare(source,roads,existing);
assert.equal(result.previewOnly,true);
assert.equal(result.additions.size,6,'entire uncredited part of nearby street proposed');
assert.equal(result.alreadyCredited.size,2,'existing credits not double-counted');
assert.equal(result.runs.length,1);
assert.equal(result.weak.size,0);
assert.equal(result.sourceLegs,2);
assert(!result.additions.has(16),'perpendicular crossing not treated as along-road');
assert(![...result.additions].some(id=>id>=8&&id<16),'distant independent parallel street not credited');
assert.deepEqual([...existing],before,'no modification to canonical credited ids');
const crossing=compare([[p(32,-12),p(32,12)]],roads,new Set());
assert(![...crossing.additions].some(id=>id<16),'across-road foot movement never implies lengthwise street travel');
const frontage=compare([[p(0,18),p(64,18)]],roads,new Set());
assert(![...frontage.additions].some(id=>id<8),'distinct distant parallel path does not credit the nearer street');
const short=compare([[p(0,5),p(12,5)]],roads,new Set());
assert.equal(short.additions.size,0,'short isolated hits do not become a credited proposal');
assert(short.weak.size>0,'short route candidates remain diagnostic only');
assert(page.includes('id="shadowRoads"'),'preview toggle is present');
assert(page.includes("stroke(proposals,'#00bcd4',3.8)"),'shadow results render independently');
assert(page.includes("previewOnly:true"),'explicitly read-only');
// Road-corridor competition: a nearer pub/service access road must not
// displace a continuous independently supported physical street.
const competing=[];
for(let i=0;i<8;i++)competing.push({p:p(i*8,6),q:p((i+1)*8,6),group:'public-road',index:i,kind:'secondary'});
for(let i=0;i<8;i++)competing.push({p:p(i*8,2),q:p((i+1)*8,2),group:'access-drive',index:i,kind:'service'});
const rival=compare([[p(0),p(32),p(64)]],competing,new Set());
assert.deepEqual([...rival.additions],[0,1,2,3,4,5,6,7],'pedestrian-first chooses coherent physical street over closer access drive');
assert.equal(rival.heldAccess.size,8,'nearby access road is held as a competing alternative');
assert.equal(rival.discardedCompetitors.size,0);
assert(rival.runs.length>=2,'both supported corridor runs are compared before selection');
// An isolated service road without a corroborated entered route remains
// inspectable as an ambiguous access candidate, not credited automatically.
const alone=compare([[p(0),p(32),p(64)]],competing.slice(8),new Set());
assert.equal(alone.additions.size,0,'service-only parallel alignment cannot by itself grant road completion');
assert.equal(alone.heldAccess.size,8);
const independentPublic=compare([[p(0,6),p(32,6),p(64,6)]],competing.slice(0,8),new Set());
assert.equal(independentPublic.additions.size,8,'Clive-like continuous public road remains proposed');
console.log('PASS: pedestrian-first preview finds supported parallel road, preserves distinct paths and old credits');
