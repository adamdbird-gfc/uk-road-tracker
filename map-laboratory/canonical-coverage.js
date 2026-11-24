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
  // Match an entire reference slice against the union of ordered source-leg
  // overlaps. The old implementation required one source leg to cover 65%
  // of each 8m interval, falsely dropping intervals when the matched route
  // had several shorter consecutive edges. No new route is constructed.
  const length=distance(a,b),axis=xy(b,a),unit=[axis[0]/length,axis[1]/length];
  const spans=[];
  const along=point=>{const w=xy(point,a);return w[0]*unit[0]+w[1]*unit[1]};
  for(const [p,q] of segments){
   if(direction(a,b,p,q)<.78)continue;
   const pAlong=along(p),qAlong=along(q);
   const start=Math.max(0,Math.min(pAlong,qAlong));
   const end=Math.min(length,Math.max(pAlong,qAlong));
   if(end-start<.35)continue;
   // Measure the overlapping portion, not the remote midpoint of a long
   // source leg that may extend far beyond this small reference slice.
   const mid=interpolate(a,b,(start+end)/(2*length));
   const lateral=project(mid,p,q).distance;
   if(lateral>10)continue;
   spans.push({start,end,offset:lateral});
  }
  if(!spans.length)return null;
  spans.sort((x,y)=>x.start-y.start||x.end-y.end);
  let coverage=0,first=null,last=0,score=Infinity;
  for(const span of spans){
   if(first===null){first=span.start;last=span.end;score=span.offset;continue;}
   if(span.start>last+1.25){
    coverage+=last-first;first=span.start;last=span.end;
   }else last=Math.max(last,span.end);
   score=Math.min(score,span.offset);
  }
  if(first!==null)coverage+=last-first;
  // Require almost the whole reference slice to be supported: adjacent
  // fragments may combine, but a single nearby crossing cannot.
  if(coverage<length*.78)return null;
  return {score,offset:score,coverage};
 }

 function longitudinalOverlap(a,b,c,d){
  const axis=xy(b,a),len=Math.hypot(...axis);
  if(len<1)return 0;
  const along=point=>{const v=xy(point,a);return (v[0]*axis[0]+v[1]*axis[1])/len};
  const x=along(c),y=along(d);
  return Math.max(0,Math.min(len,Math.max(x,y))-Math.max(0,Math.min(x,y)));
 }
 // Keep independently matched footpaths that form a supported transfer between
 // two road approaches. This uses only the current journey and reference
 // geometry, not previous journeys, street names or geographic exceptions.
 function supportedPedestrianTransfers(accepted){
  const pathIndices=accepted.map((x,i)=>x.type==='path'?i:-1).filter(i=>i>=0);
  const roads=accepted.filter(x=>x.type==='road');
  const protectedIds=new Set(),seen=new Set();
  if(!pathIndices.length||roads.length<2)return protectedIds;
  const touching=(a,b)=>distance(a,b)<=1.8;
  const nearRoad=point=>roads.some(r=>project(point,r.a,r.b).distance<=5);
  for(const first of pathIndices){
   if(seen.has(first))continue;
   const group=[],queue=[first];seen.add(first);
   while(queue.length){
    const id=queue.pop(),current=accepted[id];group.push(id);
    for(const other of pathIndices){
     if(seen.has(other))continue;
     const candidate=accepted[other];
     if([current.a,current.b].some(p=>[candidate.a,candidate.b].some(q=>touching(p,q)))){
      seen.add(other);queue.push(other);
     }
    }
   }
   const tips=[];
   for(const id of group)for(const point of [accepted[id].a,accepted[id].b]){
    const degree=group.reduce((n,j)=>n+
     Number(touching(point,accepted[j].a))+Number(touching(point,accepted[j].b)),0);
    if(degree===1)tips.push(point);
   }
   if(tips.length!==2||!tips.every(nearRoad)||distance(tips[0],tips[1])<8)continue;
   // We have a geometrically independent road → path → road corridor.
   // Each component interval has already passed source overlap checks.
   for(const id of group)protectedIds.add(id);
  }
  return protectedIds;
 }
 // Small terminal tails are unsafe to credit if the recorded journey
 // neither starts/ends there nor explicitly turns back there. This rejects
 // speculative source-matched nibs without altering the saved route.
 function pruneTerminalTails(accepted,route,held,protectedIds=new Set()){
  const node=p=>p.map(v=>Math.round(v*1e5)).join(',');
  const neighbours=new Map();
  accepted.forEach((c,i)=>{
   for(const p of [c.a,c.b]){
    const k=node(p),list=neighbours.get(k)||[];
    list.push(i);neighbours.set(k,list);
   }
  });
  const ends=(route.parts||[]).flatMap(line=>line?.length?[line[0],line.at(-1)]:[]);
  const verifiedTurn=tip=>(route.parts||[]).some(line=>{
   for(let i=1;i<line.length-1;i++){
    if(distance(tip,line[i])>4)continue;
    const before=line[i-1],after=line[i+1];
    if(distance(before,tip)>3&&distance(after,tip)>3&&distance(before,after)<7)return true;
   }
   return false;
  });
  const excluded=new Set();
  for(let i=0;i<accepted.length;i++){
   if(excluded.has(i)||protectedIds.has(i))continue;
   const edge=accepted[i];
   for(const tip of [edge.a,edge.b]){
    if((neighbours.get(node(tip))||[]).length!==1)continue;
    const traversed=new Set(),chain=[];
    let current=i,vertex=node(tip),length=0;
    while(current!==undefined&&!traversed.has(current)){
     traversed.add(current);chain.push(current);
     const e=accepted[current];
     length+=distance(e.a,e.b);
     if(length>24)break;
     const nextKey=node(e.a)===vertex?node(e.b):node(e.a);
     const connected=(neighbours.get(nextKey)||[]).filter(x=>x!==current);
     // Record the vertex we actually reached even when its degree is
     // not two. Otherwise the junction check sees the preceding point.
     vertex=nextKey;
     if(connected.length!==1)break;
     current=connected[0];
    }
    if(length>24||!chain.length||chain.some(id=>excluded.has(id)||protectedIds.has(id)))continue;
    if(ends.some(end=>distance(end,tip)<5)||verifiedTurn(tip))continue;
    // Do not remove an isolated whole journey or a terminal run that ends
    // simply because the graph has incomplete reference coverage. It must
    // connect to a separate accepted branch at the opposite end.
    // 'vertex' is already the endpoint reached by walking the branch.
    // Previously this looked up the OTHER end of the last edge, which made
    // the junction degree test check the dangling segment instead.
    const farDegree=(neighbours.get(vertex)||[]).length;
    if(farDegree<3)continue;
    for(const id of chain)excluded.add(id);
   }
  }
  return accepted.filter((c,i)=>{
   if(!excluded.has(i))return true;
   held.push({...c,reason:'unsupported-short-terminal-branch'});
   return false;
  });
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
    project(interpolate(c.a,c.b,.5),other.a,other.b).distance<4&&
    project(interpolate(other.a,other.b,.5),c.a,c.b).distance<4&&
    longitudinalOverlap(c.a,c.b,other.a,other.b)>distance(c.a,c.b)*.6&&
    longitudinalOverlap(other.a,other.b,c.a,c.b)>distance(other.a,other.b)*.6&&
    Math.abs(c.offset-other.offset)<1&&
    other.id!==c.id&&Math.abs(other.score-c.score)<1);
   if(alternatives.length){held.push({...c,reason:'ambiguous-parallel-network'});continue;}
   accepted.push(c);
  }
  const transfers=supportedPedestrianTransfers(accepted);
  const safe=pruneTerminalTails(accepted,route,held,transfers);
  return {accepted:safe,held,protectedTransferIntervals:transfers.size,sourceSegments:routeSegments.length,referenceSlices:slices.length};
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
