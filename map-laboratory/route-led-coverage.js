/* LAB-only route-led cumulative experiment.
 * Accepted, manually corrected, RouteFirst reconstructed geometry is primary.
 * No extra network match, inferred gap, connector or branch is manufactured.
 */
const RouteLedCoverage=(()=>{
 const M=111320,D=Math.PI/180,STEP=8;
 const valid=p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
 const distance=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*D/2),(a[1]-b[1])*M);
 const point=(a,b,t)=>[a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t];
 // A bidirectional, metre-quantised segment identity. Partial overlaps
 // are intentionally NOT claimed as repeats unless these same portions
 // have the same endpoints: no unproven network equivalence.
 const quant=p=>[Math.round(p[0]*1e5),Math.round(p[1]*1e5)].join(',');
 const key=(a,b)=>{const p=quant(a),q=quant(b);return 'saved:'+ (p<q?p+'|'+q:q+'|'+p)};
 // Reuse the earlier Lab's source-backed endcap / roadside equivalence
 // principles before attributing new cumulative geometry. Never modify
 // the saved match or raw capture. Without supporting reference evidence,
 // leave the route untouched rather than guessing.
 const road=/^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(?:_link)?$/;
 const foot=/^(footway|pedestrian|path|steps|cycleway|bridleway|track)$/;
 function projection(p,a,b){
  const scale=Math.cos(a[1]*D),u=[(b[0]-a[0])*M*scale,(b[1]-a[1])*M],
   v=[(p[0]-a[0])*M*scale,(p[1]-a[1])*M],len=u[0]*u[0]+u[1]*u[1];
  if(len<.25)return {offset:Infinity,t:0};
  const t=Math.min(1,Math.max(0,(u[0]*v[0]+u[1]*v[1])/len));
  return {offset:Math.hypot(v[0]-t*u[0],v[1]-t*u[1]),t};
 }
 function cosine(a,b,c,d){
  const o=a,v=[(b[0]-a[0])*M*Math.cos(o[1]*D),(b[1]-a[1])*M];
  const w=[(d[0]-c[0])*M*Math.cos(o[1]*D),(d[1]-c[1])*M];
  return Math.abs((v[0]*w[0]+v[1]*w[1])/(Math.hypot(...v)*Math.hypot(...w)||1));
 }
 function segments(refs,match){
  const out=[];
  for(const ref of refs||[]){
   if(!match.test(ref.kind)||!Array.isArray(ref.coords))continue;
   for(let i=1;i<ref.coords.length;i++){
    const a=ref.coords[i-1],b=ref.coords[i];
    if(valid(a)&&valid(b)&&distance(a,b)>1)out.push([a,b]);
   }
  }
  return out;
 }
 function nearest(p,refs){
  let best=Infinity;
  for(const [a,b] of refs)best=Math.min(best,projection(p,a,b).offset);
  return best;
 }
 function roadsideFraction(points,roads){
  let length=0,backed=0;
  for(let i=1;i<points.length;i++){
   const a=points[i-1],b=points[i],d=distance(a,b);
   if(d<.5)continue;
   length+=d;
   const mid=point(a,b,.5);
   if(roads.some(([u,v])=>projection(mid,u,v).offset<9&&cosine(a,b,u,v)>.90))
    backed+=d;
  }
  return length?backed/length:0;
 }
 function cleanup(route){
  const roads=segments(route?.refs,road),feet=segments(route?.refs,foot);
  const parts=[],held=[];
  for(const input of route?.parts||[]){
   if(!Array.isArray(input)||input.length<2){parts.push(input);continue;}
   const line=input.slice();
   if(roads.length&&line.length>=5){
    // Suppress a short returning roadside excursion ONLY when:
    //  - it rejoins the same road location (not a through-shortcut);
    //  - most of its distance shadows a real road centreline; and
    //  - both ends are already on that reference centreline.
    // An independent footway corridor crossing BETWEEN roads fails these
    // checks and is retained, including its turns and entry transitions.
    let changed=true;
    while(changed){
     changed=false;
     for(let i=1;i<line.length-4&&!changed;i++){
      let walked=0;
      for(let j=i+1;j<Math.min(line.length-1,i+42);j++){
       walked+=distance(line[j-1],line[j]);
       if(walked>115)break;
       if(j<i+4||walked<24||distance(line[i],line[j])>1.25)continue;
       if(nearest(line[i],roads)>2||nearest(line[j],roads)>2)continue;
       if(roadsideFraction(line.slice(i,j+1),roads)<.72)continue;
       held.push({reason:'parallel-roadside-return',metres:walked});
       line.splice(i+1,j-i);
       changed=true;break;
      }
     }
    }
    // A very short leading hook onto a long, corroborated road centreline
    // must not claim an independent side-road section without a nearby raw
    // observation. This is not a blanket short-journey or endpoint filter.
    const raw=(route.recorded||[]).flat().filter(valid);
    const total=line.reduce((n,p,i)=>i?n+distance(line[i-1],p):0,0);
    if(total>45&&raw.length){
     let walked=0;
     for(let k=1;k<Math.min(9,line.length-2);k++){
      walked+=distance(line[k-1],line[k]);
      if(walked>9)break;
      if(walked<4||nearest(line[k],roads)>1.5)continue;
      if(raw.some(p=>distance(p,line[0])<12))break;
      if(nearest(point(line[0],line[k],.5),feet)<2.5)break;
      const downstream=line[k+2];
      if(distance(line[k],downstream)<25)continue;
      if(cosine(line[0],line[k],line[k],downstream)>.42)continue;
      if(roadsideFraction(line.slice(k,k+3),roads)<.75)continue;
      held.push({reason:'unverified-short-road-entrance',metres:walked});
      line.splice(0,k);
      break;
     }
    }
   }
   parts.push(line);
  }
  return {parts,held};
 }
 function intervals(route){
  const result=[],seen=new Set();
  for(const line of route?.parts||[]){
   if(!Array.isArray(line))continue;
   for(let i=1;i<line.length;i++){
    const a=line[i-1],b=line[i];if(!valid(a)||!valid(b))continue;
    const length=distance(a,b);
    if(length<.5||length>500)continue;
    const n=Math.ceil(length/STEP);
    for(let k=0;k<n;k++){
     const x=point(a,b,k/n),y=point(a,b,(k+1)/n),id=key(x,y);
     if(seen.has(id))continue;
     seen.add(id);result.push({id,a:x,b:y,type:'source',source:'saved-match'});
    }
   }
  }
  return result;
 }
 function accumulate(history,route){
  const next=new Set(history||[]),novel=[],repeat=[];
  const prepared=cleanup(route);
  for(const part of intervals(prepared)){
   if(next.has(part.id))repeat.push(part);
   else{novel.push(part);next.add(part.id)}
  }
  return {novel,repeat,next,held:prepared.held,sourceSegments:(route?.parts||[]).reduce((sum,line)=>sum+Math.max(0,(line?.length||0)-1),0)};
 }
 return {intervals,accumulate,cleanup,key};
})();
if(typeof module!=='undefined')module.exports=RouteLedCoverage;
