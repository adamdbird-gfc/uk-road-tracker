/* LAB-only route-led cumulative experiment.
 * Accepted, manually corrected, RouteFirst reconstructed geometry is primary.
 * No extra network match, inferred gap, connector or branch is manufactured.
 */
const CentrelinePromotion=typeof module!=='undefined'?require('./roadside-centreline.js'):RoadsideCentreline;
const SharedRoadReferenceSource=typeof module!=='undefined'?require('./shared-road-catalogue.js'):SharedRoadCatalogue;
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
 // A sparse saved match can overshoot its final recorded position and
 // turn onto another road. Preserve the directly corroborated approach
 // up to the last observed position, not an invented junction endpoint.
 // This is not a general route-end truncation: it requires a GPS-backed
 // position on the approach, a sharp turn, and distinct network road refs.
 function trimUnsupportedTerminalTurn(line,route,held){
  const raw=(route.recorded||[]).flat().filter(valid),refs=route.refs||[];
  if(!raw.length||line.length<5)return;
  const destination=raw.at(-1);
  if(distance(destination,line.at(-1))<12)return;
  const suffix=Array(line.length).fill(0);for(let i=line.length-2;i>=0;i--)
   suffix[i]=suffix[i+1]+distance(line[i],line[i+1]);
  const roadWays=refs.filter(r=>road.test(r.kind)&&Array.isArray(r.coords)&&r.coords.length>1);
  if(roadWays.length<2)return;
  const nearWay=(a,b)=>{
   const mid=point(a,b,.5),options=[];
   for(let i=0;i<roadWays.length;i++)for(let j=1;j<roadWays[i].coords.length;j++){
    const u=roadWays[i].coords[j-1],v=roadWays[i].coords[j];
    if(!valid(u)||!valid(v)||cosine(a,b,u,v)<.8)continue;
    const offset=projection(mid,u,v).offset;
    if(offset<2.5)options.push({id:i,offset});
   }
   return options.sort((a,b)=>a.offset-b.offset)[0]||null;
  };
  for(let corner=line.length-2;corner>=2;corner--){
   const tail=suffix[corner];
   if(tail<6||tail>24)continue;
   const before=line[corner-1],junction=line[corner],after=line[corner+1];
   const approach=distance(before,junction);
   if(approach<3)continue;
   // Direction changes at the terminal junction, not along a legitimately
   // continuous road or through-route.
   const va=[(junction[0]-before[0])*M*Math.cos(junction[1]*D),(junction[1]-before[1])*M];
   const vb=[(after[0]-junction[0])*M*Math.cos(junction[1]*D),(after[1]-junction[1])*M];
   const dot=(va[0]*vb[0]+va[1]*vb[1])/(Math.hypot(...va)*Math.hypot(...vb)||1);
   if(dot>.45||dot<-.8)continue;
   if(distance(destination,junction)>20)continue;
   const main=nearWay(before,junction),side=nearWay(junction,line.at(-1));
   if(!main||!side||main.id===side.id)continue;
   // A final GPS observation beyond this turn is independent evidence of
   // the side road. A last fix within 4m of the approach, not the side
   // road, only corroborates travel as far as the junction.
   let lastFix=null;
   for(let i=Math.max(1,corner-8);i<=corner;i++){
    // Only a backed location on the *approach* can establish the end.
    // Never snap a point onto the side road or extrapolate to the junction.
    if(suffix[i]>40)continue;
    const evidence=projection(destination,line[i-1],line[i]);
    if(evidence.offset>3.5)continue;
    const snapped=point(line[i-1],line[i],evidence.t);
    const remainder=suffix[i]+distance(snapped,line[i]);
    if(remainder>40)continue;
    if(!lastFix||evidence.offset<lastFix.offset)
     lastFix={index:i,position:snapped,offset:evidence.offset,remainder};
   }
   if(!lastFix)continue;
   if(raw.some(p=>distance(p,line.at(-1))<8))continue;
   // Retain only corroborated journey geometry. The GPS endpoint may lie
   // partway along the road; never extend it just to make a tidy junction.
   const previous=line[lastFix.index-1];
   const kept=line.slice(0,lastFix.index);
   if(distance(previous,lastFix.position)>.5)kept.push(lastFix.position);
   if(kept.length<2)continue;
   const withheld=lastFix.remainder;
   held.push({reason:'unverified-terminal-road-turn',metres:Number(withheld.toFixed(1)),
    evidence:'last-recorded-position-on-approach'});
   line.splice(0,line.length,...kept);
   break;
  }
 }
 function cleanup(route){
  const roads=segments(route?.refs,road),feet=segments(route?.refs,foot);
  const aligned=CentrelinePromotion.align(route.sharedRoadReferences?.length?
   {...route,refs:SharedRoadReferenceSource.merge(route.refs,route.sharedRoadReferences)}:route);
  const parts=[],held=[];
  for(const input of aligned.parts||[]){
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
   if(roads.length)trimUnsupportedTerminalTurn(line,route,held);
   parts.push(line);
  }
  return {parts,held,centrelinePromotions:aligned.promoted};
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
  return {novel,repeat,next,held:prepared.held,centrelinePromotions:prepared.centrelinePromotions,sourceSegments:(route?.parts||[]).reduce((sum,line)=>sum+Math.max(0,(line?.length||0)-1),0)};
 }
 return {intervals,accumulate,cleanup,key};
})();
if(typeof module!=='undefined')module.exports=RouteLedCoverage;
