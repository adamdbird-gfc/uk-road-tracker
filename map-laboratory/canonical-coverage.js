/* LAB-only physical-network attribution. Routes remain untouched.
 * All coordinates lon/lat. Candidate coverage is provisional until source
 * evidence and network alternatives have been validated.
 */
const CanonicalCoverage = (() => {
 const METRES=111320,DEG=Math.PI/180,STEP=8;
 const road=/^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(?:_link)?$/;
 const path=/^(footway|pedestrian|path|steps|cycleway|bridleway|track)$/;
 const valid=p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite);
 function xy(p,origin){return [(p[0]-origin[0])*METRES*Math.cos(origin[1]*DEG),(p[1]-origin[1])*METRES]}
 function distance(a,b){const p=xy(b,a);return Math.hypot(...p)}
 function project(point,a,b){
  const v=xy(b,a),w=xy(point,a),length=Math.hypot(...v);
  if(length<.5)return {distance:Infinity,parallel:0,t:0};
  const t=Math.max(0,Math.min(1,(w[0]*v[0]+w[1]*v[1])/(length*length)));
  return {distance:Math.hypot(w[0]-t*v[0],w[1]-t*v[1]),t};
 }
 function direction(a,b,c,d){
  const u=xy(b,a),v=xy(d,c),l=Math.hypot(...u)*Math.hypot(...v);
  return l<1?0:Math.abs((u[0]*v[0]+u[1]*v[1])/l);
 }
 const interpolate=(a,b,t)=>[a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t];
 const quant=p=>p.map(x=>Math.round(x*1e6)).join(',');
 const key=(a,b,kind)=>{
  const x=quant(a),y=quant(b);
  return kind+':'+(x<y?x+'|'+y:y+'|'+x);
 };
 function sliceRefs(refs){
  const output=[],seen=new Set();
  for(const ref of refs||[]){
   if(!road.test(ref.kind)&&!path.test(ref.kind))continue;
   if(!Array.isArray(ref.coords))continue;
   for(let i=1;i<ref.coords.length;i++){
    const p=ref.coords[i-1],q=ref.coords[i];
    if(!valid(p)||!valid(q))continue;
    const len=distance(p,q);
    if(len<1||len>500)continue;
    const count=Math.ceil(len/STEP);
    for(let j=0;j<count;j++){
     const a=interpolate(p,q,j/count),b=interpolate(p,q,(j+1)/count);
     const type=road.test(ref.kind)?'road':'path',id=key(a,b,type);
     if(seen.has(id))continue;
     seen.add(id);output.push({id,a,b,type,kind:ref.kind,source:ref.name||''});
    }
   }
  }
  return output;
 }
 function flattened(parts){
  const segments=[];
  for(const line of parts||[])for(let i=1;i<line.length;i++)
   if(valid(line[i-1])&&valid(line[i])&&distance(line[i-1],line[i])>=1)
    segments.push([line[i-1],line[i]]);
  return segments;
 }
 function support(a,b,segments){
  const mid=interpolate(a,b,.5),length=distance(a,b);
  let best=null;
  for(const [p,q] of segments){
   // Reject route segments that only come close at a perpendicular crossing.
   if(direction(a,b,p,q)<.78)continue;
   const pa=project(a,p,q),pb=project(b,p,q),pm=project(mid,p,q);
   const d=Math.max(pa.distance,pb.distance,pm.distance);
   if(d>10)continue;
   // A source segment must overlap the reference slice longitudinally;
   // nearby parallel but unrelated roads cannot create a new credit.
   const rp=project(p,a,b),rq=project(q,a,b);
   const v=xy(b,a),len=Math.hypot(...v);
   const along=x=>{const w=xy(x,a);return(w[0]*v[0]+w[1]*v[1])/len};
   const pAlong=along(p),qAlong=along(q);
   const overlap=Math.max(0,Math.min(length,Math.max(pAlong,qAlong))-Math.max(0,Math.min(pAlong,qAlong)));
   if(overlap<Math.min(2.5,length*.65))continue;
   const score=d+(1-direction(a,b,p,q))*9;
   if(!best||score<best.score)best={score,offset:d};
  }
  return best;
 }
 function classify(route){
  const slices=sliceRefs(route.refs),routeSegments=flattened(route.parts),candidates=[];
  for(const ref of slices){
   const proof=support(ref.a,ref.b,routeSegments);
   if(proof)candidates.push({...ref,score:proof.score,offset:proof.offset});
  }
  // Parallel pedestrian corridors are not additional road coverage. Keep
  // independent paths when no aligned road occupies the same physical corridor.
  const accepted=[],held=[];
  for(const c of candidates){
   if(c.type==='path'){
    const parallel=candidates.some(r=>r.type==='road'&&
     direction(c.a,c.b,r.a,r.b)>.9&&
     Math.min(project(c.a,r.a,r.b).distance,project(c.b,r.a,r.b).distance)<9);
    if(parallel){held.push({...c,reason:'parallel-roadside-path'});continue;}
   }
   const alternatives=candidates.filter(other=>other!==c&&other.type===c.type&&
    direction(c.a,c.b,other.a,other.b)>.9&&
    project(interpolate(c.a,c.b,.5),other.a,other.b).distance<5&&
    other.id!==c.id&&Math.abs(other.score-c.score)<1);
   if(alternatives.length){held.push({...c,reason:'ambiguous-parallel-network'});continue;}
   accepted.push(c);
  }
  return {accepted,held,sourceSegments:routeSegments.length,referenceSlices:slices.length};
 }
 function accumulate(history,route){
  const previous=history||new Set(),result=classify(route),novel=[],repeat=[];
  for(const c of result.accepted)(previous.has(c.id)?repeat:novel).push(c);
  // Mutate a separate ledger, never route source geometry or Android coverage.
  const next=new Set(previous);
  for(const c of novel)next.add(c.id);
  return {...result,novel,repeat,next};
 }
 return {sliceRefs,classify,accumulate,key};
})();
if(typeof module!=='undefined')module.exports=CanonicalCoverage;
