// Synthetic road and footway only. No private archive or coordinates.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const a=html.indexOf('function continuityTransition('),b=html.indexOf('function match(j){',a);
assert(a>=0&&b>a,'sequence matcher helpers');
const M=111320,D=Math.PI/180,p=(x,y=0)=>[x/M,y/M];
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const {selectPhysicalCorridorCandidates,chooseContinuousSamples}=new Function('metres','xyz','proj',html.slice(a,b)+';return {selectPhysicalCorridorCandidates,chooseContinuousSamples}')(metres,xyz,proj);
const net=[];
for(let i=0;i<6;i++)net.push({p:p(i*7),q:p((i+1)*7),index:i,group:'main-street',kind:'residential'});
for(let i=0;i<6;i++)net.push({p:p(i*7,5),q:p((i+1)*7,5),index:i,group:'pavement',kind:'footway'});
net.push({p:p(21,5),q:p(21,16),index:0,group:'station-access',kind:'footway'});
net.push({p:p(14,15),q:p(21,15),index:0,group:'separate-frontage',kind:'footway'});
const candidates=[{id:6,score:0.5},{id:12,score:3},{id:0,score:6.1}];
const picked=selectPhysicalCorridorCandidates(candidates,net);
assert(picked.some(c=>c.id===0&&c.roadsideCandidate),'legitimate road candidate is rescued from five-best cutoff');
assert(!picked.some(c=>c.id===12&&c.roadsideCandidate),'perpendicular station entrance is never reclassified');
assert(picked.length<=8,'candidate set is bounded');
const sequence=[];
for(let i=0;i<6;i++)sequence.push({candidates:selectPhysicalCorridorCandidates([
 {id:6+i,score:i===3?0.2:3.9},{id:i,score:i===3?6.5:6.1}
],net)});
const selected=chooseContinuousSamples(sequence,net);
assert(selected.chosen.every(c=>c.id<6),'coherent centreline wins over alternating footways');
assert(selected.chosen.some(c=>c.roadsideCandidate),'rescued road alternatives reach continuity selector');
const frontage=selectPhysicalCorridorCandidates([{id:13,score:0.4},{id:2,score:6.5}],net);
assert(!frontage.some(c=>c.id===2),'unrelated distant frontage cannot promote an adjacent road');
const entrance=selectPhysicalCorridorCandidates([{id:12,score:0.3},{id:2,score:6.3}],net);
assert(!entrance.some(c=>c.id===2),'perpendicular entrance cannot fabricate road coverage');
assert.deepEqual(selectPhysicalCorridorCandidates([],net),[],'unmatched samples stay unmatched');
console.log('PASS: centreline kept in continuity search; separate footpaths, station entrances and gaps protected');
