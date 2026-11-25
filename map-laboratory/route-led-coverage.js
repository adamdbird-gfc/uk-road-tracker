/* LAB-only route-led cumulative experiment.
 * Accepted, manually corrected, RouteFirst reconstructed geometry is primary.
 * No extra network match, inferred gap, connector or branch is manufactured.
 */
const CentrelinePromotion=typeof module!=='undefined'?require('./roadside-centreline.js'):RoadsideCentreline;
const PublicRoadSource=typeof module!=='undefined'?require('./independent-road-network.js'):IndependentRoadNetwork;
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
 // With two Timeline points the initial fix can plausibly belong to the
 // same road the match subsequently follows in the opposite direction.
 // Keep only that already-travelled road from the projected start.
 function trimRedundantStartHook(line,route,refs,held){
  if(!route.onFoot||route.corrected||route.sourceType!=='timeline_import'||
     route.source!=='saved match'||route.parts.length!==1||line.length<6)return;
  const raw=route.recorded?.[0];
  if(route.recorded?.length!==1||raw.length!==2||
     !raw.every(valid)||distance(raw[0],line[0])>5)return;
  const roads=segments(refs.filter(ref=>ref.nodes),road);
  if(!roads.length||nearest(raw[1],roads)>17)return;
  let walked=0;
  for(let i=1;i<line.length-2;i++){
   const a=line[i-1],b=line[i];
   walked+=distance(a,b);
   if(walked>72)break;
   if(walked<24||distance(a,b)<5)continue;
   const hit=projection(raw[0],a,b);
   const snapped=point(a,b,hit.t);
   if(hit.offset>7.5||nearest(snapped,roads)>1.25||
      roadsideFraction(line.slice(i-1,i+3),roads)<.90)continue;
   // The hook heads away from the destination before returning to its
   // starting corridor. A straight or sideways access leg fails this.
   const out=[line[0],line[1]],back=[a,b];
   const axis=(x,y)=>[(y[0]-x[0])*Math.cos(x[1]*D),y[1]-x[1]];
   const [u,v]=axis(...out),[x,y]=axis(...back);
   if((u*x+v*y)/(Math.hypot(u,v)*Math.hypot(x,y))>-.8||
      line.slice(0,i).some(p=>nearest(p,roads)>14))continue;
   // Only the same physical road can explain the short reverse excursion.
   if(!roads.some(([u,v])=>projection(snapped,u,v).offset<=1.25&&
      projection(b,u,v).offset<=1.25))continue;
   held.push({reason:'redundant-pedestrian-start-hook',
    metres:Number((walked-distance(snapped,b)).toFixed(1))});
   line.splice(0,i,...(distance(snapped,b)>.25?[snapped]:[]));
   return;
  }
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
 // Ordered equivalent of the legacy island safeguard. A detached interior
 // part may be withheld only when one continuous, already travelled road
 // covers its full length and the present journey offers no raw support.
 function suppressIslands(parts,route,refs,held){
  if(route.corrected||parts.length<3)return parts;
  const roads=segments(refs,road);
  const pedestrian=segments(refs.filter(ref=>!ref.footway||ref.footway==='sidewalk'),/^(footway|path)$/);
  if(!roads.length||!pedestrian.length)return parts;
  const raw=(route.recorded||[]).flat().filter(valid);
  return parts.filter((part,index)=>{
   if(index===0||index===parts.length-1||part.length<2)return true;
   const length=part.reduce((sum,p,i)=>i?sum+distance(part[i-1],p):sum,0);
   if(length<2||length>30||part.some(p=>raw.some(fix=>distance(p,fix)<6)))return true;
   if(!part.slice(1).every((p,i)=>{
    const a=part[i],mid=point(a,p,.5);
    return nearest(mid,roads)>2&&nearest(mid,pedestrian)<1.25;
   }))return true;
   // Require both nearby parts to be already centred on the same road
   // edge, and to meet or overlap. Never remove an island to fill a route gap.
   const before=parts[index-1],after=parts[index+1];
   if(before.length<2||after.length<2)return true;
   const bridge=[before.at(-2),before.at(-1),after[0],after[1]];
   if(distance(bridge[1],bridge[2])>2)return true;
   const along=(a,b,p)=>(b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0]);
   const duplicate=roads.some(([a,b])=>{
    if(bridge.some(p=>projection(p,a,b).offset>1.25))return false;
    const axis=[bridge[0],bridge[3]];
    if(distance(...axis)<length*.72)return false;
    const offsets=part.map(p=>projection(p,...axis));
    return offsets.every(({offset,t})=>offset>=2&&offset<=16&&t>0&&t<1)&&
     part.every(p=>Math.sign(along(...axis,p))===Math.sign(along(...axis,part[0])))&&
     part.slice(1).every((p,i)=>cosine(part[i],p,...axis)>.85);
   });
   if(!duplicate)return true;
   held.push({reason:'detached-roadside-duplicate',metres:length});
   return false;
  });
 }
 // Remove a tiny loop that returns to the same travelled junction.
 // An observation that favours the excursion, or independent crossing
 // geometry on it, is stronger evidence than its shape.
 function suppressMicroReturns(line,route,refs,held){
  if(line.length<5||!refs.some(ref=>road.test(ref.kind)))return;
  const cross=segments(refs.filter(ref=>ref.footway==='crossing'||/^(steps|corridor)$/.test(ref.kind)),foot);
  const roadEdges=segments(refs,road);
  const raw=(route.recorded||[]).flat().filter(valid);
  for(let i=1;i<line.length-3;i++){
   const junction=line[i];
   let walked=0;
   for(let j=i+1;j<Math.min(line.length-1,i+14);j++){
    walked+=distance(line[j-1],line[j]);
    if(walked>19)break;
    if(j<i+2||walked<3||distance(junction,line[j])>2)continue;
    if(nearest(junction,roadEdges)>1.25||nearest(line[j],roadEdges)>1.25)continue;
    const span=line.slice(i,j+1),legs=span.slice(1).map((p,k)=>[span[k],p]);
    if(!span.slice(1,-1).some(p=>distance(p,junction)>=1.4))continue;
    const usesCrossing=legs.some(([a,b])=>distance(a,b)>1&&cross.some(([u,v])=>
      cosine(a,b,u,v)>.9&&projection(point(a,b,.5),u,v).offset<1.25));
    const savedReturn=usesCrossing&&route.parts.some(source=>source.some((p,at)=>
      distance(p,junction)<.5&&span.every((q,k)=>source[at+k]&&distance(q,source[at+k])<.5)));
    if(savedReturn)break;
    if(raw.some(p=>nearest(p,legs)<7&&distance(p,junction)-nearest(p,legs)>4))break;
    held.push({reason:'short-junction-return',metres:Number(walked.toFixed(1))});
    line.splice(i+1,j-i);
    break;
   }
  }
 }
 // A sparse final fix near a previously traversed junction can leave
 // a short spur on an adjacent path. Withhold only an uncorroborated
 // turn and retain the already supported road junction.
 function trimShortPathNib(line,route,refs,held){
  if(line.length<5)return;
  const fix=route.recorded?.at(-1)?.at(-1);
  if(!valid(fix))return;
  const roads=segments(refs,road),paths=segments(refs,/^(footway|path|cycleway)$/);
  let length=0;
  for(let i=line.length-2;i>=2;i--){
   const a=line[i],b=line[i+1],before=line[i-1];
   length+=distance(a,b);
   if(length>9)break;
   if(length<3||distance(a,fix)>10||cosine(before,a,a,b)>.65||
      nearest(a,roads)>1.25||nearest(line.at(-1),roads)<2.5)continue;
   const tail=line.slice(i+1).map((point,k)=>[line[i+k],point]);
   if(nearest(fix,tail)<=3||nearest(point(a,b,.5),paths)>1.25)continue;
   if(!line.slice(0,i-2).some(p=>distance(p,a)<1.25))continue;
   held.push({reason:'unverified-junction-endcap',metres:Number(length.toFixed(1))});
   line.splice(i+1);
   break;
  }
 }
 // A clipped walking endpoint can meet a later stretch of this same journey
 // via a mapped footway retained in its saved match references. Draw that
 // connection without treating the inferred link as new coverage.
 function endpointLinks(parts,route){
  if(!route.onFoot||route.corrected||route.source!=='saved match'||parts.length!==1||
     !route.independentRoadReferences?.length||route.railTransitions?.length)return [];
  const line=parts[0],raw=(route.recorded||[]).flat().filter(valid);
  if(line?.length<6||raw.length<2)return [];
  const roads=segments(route.independentRoadReferences,road);
  const footways=(route.refs||[]).filter(ref=>/^(footway|pedestrian|path)$/.test(ref.kind)&&
   !ref.bridge&&!ref.tunnel&&ref.indoor!=='yes'&&ref.coords?.every(valid));
  function connect(points,fix){
   if(distance(points[0],fix)>6||nearest(points[0],roads)<3)return null;
   const candidates=new Map();
   let terminal=0;
   for(let i=0;i<points.length-2;i++){
    if(i)terminal+=distance(points[i-1],points[i]);
    if(terminal>5)break;
    for(const ref of footways)for(const path of [ref.coords,ref.coords.toReversed()]){
     if(distance(points[i],path[0])>.25)continue;
     let length=terminal;
     for(let j=1;j<path.length;j++){
      length+=distance(path[j-1],path[j]);
      if(length>22)break;
      const end=path[j];
      if(length<5||nearest(end,roads)>.25||
         !points.slice(i+5).some(p=>distance(p,end)<.25))continue;
      const connector=path.slice(0,j+1);
      const trail=connector.slice(1).map((b,k)=>[connector[k],b]);
      if(nearest(fix,trail)>4.5||distance(fix,end)<1.5)continue;
      candidates.set(connector.map(p=>p.join(',')).join(';'),connector);
      break;
     }
    }
   }
   return candidates.size===1?[...candidates.values()][0]:null;
  }
  return [connect(line,raw[0]),connect(line.toReversed(),raw.at(-1))].filter(Boolean);
 }
 // Assess each street excursion between pedestrian observations. An import
 // can include a genuine road approach as well as an off-road section.
 function withholdRoadExcursions(route,held){
  const {parts,refs=[]}=route;
  if(!route.onFoot||route.corrected||route.source!=='saved match'||
     route.sourceType!=='timeline_import'||parts.length!==1||route.railTransitions?.length)
   return route;
  const line=parts[0],raw=route.recorded?.length===1?route.recorded[0]:[];
  if(raw.length<2||!raw.every(valid)||line.length<3||!line.every(valid)||
     (raw.length>3&&!raw.slice(1).some((p,i)=>distance(raw[i],p)>50)))return route;
  const roadWays=refs.filter(ref=>road.test(ref.kind)).map(ref=>({ref,edges:segments([ref],road)}));
  const roads=roadWays.flatMap(way=>way.edges);
  const paths=segments(refs.filter(ref=>!['sidewalk','crossing'].includes(ref.footway)&&
   !ref.bridge&&!ref.tunnel&&ref.indoor!=='yes'),/^(footway|path|pedestrian|track)$/);
  if(!roads.length||!paths.length)return route;

  const edges=line.slice(1).map((b,i)=>[line[i],b]);
  // Match the whole leg so a crossing or a nearby path is not a road leg.
  const follows=(edge,network)=>network.some(([u,v])=>cosine(...edge,u,v)>=.97&&
   edge.every(p=>projection(p,u,v).offset<=2));
  const runs=[];
  for(let i=0;i<edges.length;i++){
   const matched=roadWays.filter(way=>follows(edges[i],way.edges));
   if(!matched.length)continue;
   const last=runs.at(-1);
   if(last?.end===i){
    last.end=i+1;
    for(const way of matched)last.ways.add(way.ref);
   }else runs.push({start:i,end:i+1,ways:new Set(matched.map(way=>way.ref))});
  }
  const length=(start,end)=>edges.slice(start,end).reduce((sum,[a,b])=>sum+distance(a,b),0);
  const observed=(start,end)=>{
   const span=edges.slice(start,end);
   return raw.some(fix=>nearest(fix,span)<=18);
  };
  const onPath=raw.map(fix=>nearest(fix,paths)<=30&&nearest(fix,roads)>=25);
  function anchors(from,limit,direction){
   const nearby=[];
   let metres=0;
   for(let i=from;i!==limit&&metres<550;i+=direction){
    const edge=edges[direction>0?i:i-1];
    metres+=distance(...edge);
    if(follows(edge,paths))nearby.push(edge);
   }
   return raw.flatMap((fix,i)=>onPath[i]&&nearest(fix,nearby)<=30?[i]:[]);
  }
  const kept=[],withheldWays=new Set();
  let start=0;
  for(let n=0;n<runs.length;n++){
   const run=runs[n],next=runs[n+1];
   // Assess the street and its returning branch together so an observed
   // visit to the branch also protects the streets that provide access.
   const returns=next&&distance(line[run.end],line[next.start])<=1.25&&
    distance(line[run.start],line[next.end])<=1.25&&
    length(run.end,next.start)<=150;
   const end=returns?next.end:run.end;
   const previousEnd=runs[n-1]?.end??0;
   if(returns)n++;
   const metres=length(run.start,end);
   if(metres<32||observed(run.start,end))continue;
   const before=anchors(run.start,previousEnd,-1);
   const after=anchors(end,runs[n+1]?.start??edges.length,1);
   if(!before.length||!after.length||before[0]>=after.at(-1))continue;
   if(run.start>start)kept.push(line.slice(start,run.start+1));
   start=end;
   for(const ref of run.ways)withheldWays.add(ref);
   if(returns)for(const ref of next.ways)withheldWays.add(ref);
   held.push({reason:'unverified-road-excursion-between-pedestrian-observations',
    metres:Number(metres.toFixed(1))});
  }
  if(!start)return route;
  if(start<line.length-1)kept.push(line.slice(start));
  // Do not snap surviving paths back onto the same uncorroborated way.
  // Original geometry on that way can still be retained elsewhere.
  return {...route,parts:kept,refs:refs.filter(ref=>!withheldWays.has(ref))};
 }
 function cleanup(route){
  const roads=segments(route?.refs,road),feet=segments(route?.refs,foot);
  // Static road geometry comes only from this journey's own references
  // or the pinned public OSM network, never from other journeys.
  const physical=route.independentRoadReferences?.length?
   {...route,refs:PublicRoadSource.merge(route.refs,route.independentRoadReferences)}:route;
  const parts=[],held=[];
  const withPhysicalRoads=withholdRoadExcursions(physical,held);
  const screening=route.diagnosticsRequested?CentrelinePromotion.evidence(withPhysicalRoads):null;
  const aligned=route.onFoot===false||route.source==='recorded'?
   restoreRoundabout(route):CentrelinePromotion.align(withPhysicalRoads);
  for(const input of aligned.parts||[]){
   if(!Array.isArray(input)||input.length<2){parts.push(input);continue;}
   const line=input.slice();
   if(route.onFoot&&!route.corrected&&route.source==='saved match'){
    const refs=withPhysicalRoads.refs||[];
    if(input===aligned.parts[0])trimRedundantStartHook(line,route,refs,held);
    suppressMicroReturns(line,route,refs,held);
    if(input===aligned.parts.at(-1))trimShortPathNib(line,route,refs,held);
   }
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
  const canClean=route.onFoot!==false&&route.source!=='recorded';
  const output=canClean?suppressIslands(parts,route,withPhysicalRoads.refs||[],held):parts;
  if(route.onFoot&&!route.corrected&&route.source==='saved match'&&route.independentRoadReferences?.length){
   const raw=(route.recorded||[]).flat().filter(valid);
   for(const line of output)if(line?.length>=4){
    aligned.promoted.push(...CentrelinePromotion.closeRoadInterruptions(line,withPhysicalRoads.refs,raw));
    aligned.promoted.push(...CentrelinePromotion.closePedestrianStreetCorridors(line,withPhysicalRoads.refs,raw));
    if(route.sourceType==='timeline_import'){
     aligned.promoted.push(...CentrelinePromotion.closeOffsetRoadTurns(line,withPhysicalRoads.refs,raw));
     aligned.promoted.push(...CentrelinePromotion.closeKerbsideLoops(line,withPhysicalRoads.refs,raw));
    }
    if(route.sourceType==='timeline_import'&&raw.length&&line===output.at(-1))
     aligned.promoted.push(...CentrelinePromotion.closeTerminalPavement(line,withPhysicalRoads.refs,raw));
   }
  }
  return {parts:output,held,centrelinePromotions:aligned.promoted,roadEvidence:screening,
   links:endpointLinks(output,route)};
 }
 function supportedLeg(route,a,b){
  if(!valid(a)||!valid(b))return false;
  const length=distance(a,b);
  if(length<=500)return true;
  // A sparse overview may still contain a long, measured road step. Require
  // the same adjacent vertices in the saved step and cap interval expansion.
  if(length>5000||route.source!=='saved match')return false;
  const same=(p,q)=>p[0]===q[0]&&p[1]===q[1];
  return route.roadSteps?.some(line=>line.some((p,i)=>i>0&&
   (same(line[i-1],a)&&same(p,b)||same(line[i-1],b)&&same(p,a))));
 }
 // A sparse driving match can shortcut a roundabout through a parallel
 // service road. Check the connected, directed public network, bounded by
 // the existing main-road approach and exit inside the same saved part.
 function restoreRoundabout(route){
  const result={parts:route.parts,promoted:[]};
  const refs=route.drivingReferences||[];
  const raw=(route.recorded||[]).flat().filter(valid);
  if(route.corrected||route.source!=='saved match'||route.sourceType!=='timeline_import'||
     !route.roadSteps?.length||raw.length<2||!refs.some(ref=>ref.junction==='roundabout'))
   return result;
  const services=segments(refs,/^service$/);
  if(!services.length)return result;
  const graph=new Map(),major=[];
  for(const ref of refs){
   if(!/^(primary|secondary|tertiary)(?:_link)?$/.test(ref.kind))continue;
   for(let i=1;i<ref.nodes.length;i++){
    let from=ref.nodes[i-1],to=ref.nodes[i],a=ref.coords[i-1],b=ref.coords[i];
    if(ref.oneway==='-1')[from,to,a,b]=[to,from,b,a];
    const ring=ref.junction==='roundabout';
    for(const edge of [{from,to,a,b,ring},...(ring||['yes','1','-1','true'].includes(ref.oneway)?
     []:[{from:to,to:from,a:b,b:a,ring}])]){
     const outgoing=graph.get(edge.from)||[];
     outgoing.push(edge);
     graph.set(edge.from,outgoing);
     major.push(edge);
    }
   }
  }
  function shortest(from,to,limit){
   const queue=[{node:from,metres:0,edges:[]}],seen=new Set();
   while(queue.length){
    queue.sort((a,b)=>b.metres-a.metres);
    const next=queue.pop();
    if(seen.has(next.node))continue;
    if(next.node===to)return next;
    seen.add(next.node);
    for(const edge of graph.get(next.node)||[]){
     const metres=next.metres+distance(edge.a,edge.b);
     if(metres<=limit&&!seen.has(edge.to))
      queue.push({node:edge.to,metres,edges:next.edges.concat(edge)});
    }
   }
   return null;
  }
  function anchor(line,at,direction){
   let walked=0;
   for(let i=at;i>0&&i<line.length-1&&walked<=35;i+=direction){
    const p=line[i],q=line[i+direction];
    if(distance(p,q)>.5){
     const candidates=major.filter(edge=>!edge.ring&&
      distance(p,direction<0?edge.b:edge.a)<=.45&&
      projection(q,edge.a,edge.b).offset<=.75);
     const nodes=new Set(candidates.map(edge=>direction<0?edge.to:edge.from));
     if(nodes.size===1)return {at:i,node:[...nodes][0]};
    }
    walked+=distance(p,q);
   }
   return null;
  }
  result.parts=(route.parts||[]).map(line=>{
   const output=[];
   let kept=0;
   const onService=(a,b)=>distance(a,b)<40&&[a,point(a,b,.5),b]
    .every(p=>nearest(p,services)<=2);
   for(let i=1;i<line.length;){
    if(!onService(line[i-1],line[i])){i++;continue;}
    const start=i-1,service=[];
    let metres=0;
    do {
     const a=line[i-1],b=line[i++];
     service.push([a,b]);
     metres+=distance(a,b);
    }while(i<line.length&&onService(line[i-1],line[i]));
    if(metres<35||metres>160||raw.some(fix=>nearest(fix,service)<=12))continue;
    const entry=anchor(line,start,-1),exit=anchor(line,i-1,1);
    if(!entry||!exit||entry.at<kept||entry.node===exit.node)continue;
    const path=shortest(entry.node,exit.node,Math.min(250,metres*2.5));
    if(!path?.edges.some(edge=>edge.ring))continue;
    output.push(...line.slice(kept,entry.at),path.edges[0].a,...path.edges.map(edge=>edge.b));
    kept=exit.at+1;
    result.promoted.push({kind:'driving-roundabout',road:'Mapped roundabout',
     metres:Number(path.metres.toFixed(1)),segments:exit.at-entry.at});
   }
   return kept?output.concat(line.slice(kept)):line;
  });
  return result;
 }
 function* sourceIntervals(a,b){
  const length=distance(a,b);
  if(length<.5)return;
  const n=Math.ceil(length/STEP);
  for(let k=0;k<n;k++){
   const x=point(a,b,k/n),y=point(a,b,(k+1)/n);
   yield {id:key(x,y),a:x,b:y,type:'source',source:'saved-match'};
  }
 }
 function intervals(route){
  const result=new Map();
  for(const line of route?.parts||[])for(let i=1;i<(line?.length||0);i++){
   const a=line[i-1],b=line[i];
   if(supportedLeg(route,a,b))for(const part of sourceIntervals(a,b))
    if(!result.has(part.id))result.set(part.id,part);
  }
  return [...result.values()];
 }
 function createLedger(history){
  const sources=new Set(history?.sources||history),roads=new Map(history?.roads);
  return {sources,roads,get size(){return sources.size+roads.size;},
   clear(){sources.clear();roads.clear();}};
 }
 function pinnedEdges(refs){
  const edges=new Map();
  for(const ref of refs||[]){
   if(!(road.test(ref.kind)||ref.kind==='pedestrian'&&ref.name||ref.sharedCrossing)||
      ref.nodes?.length!==ref.coords?.length)continue;
   for(let i=1;i<ref.coords.length;i++){
    let a=ref.coords[i-1],b=ref.coords[i],from=ref.nodes[i-1],to=ref.nodes[i];
    if(!valid(a)||!valid(b)||from===to||distance(a,b)<.1)continue;
    if(from>to)[a,b,from,to]=[b,a,to,from];
    const id='road:'+from+':'+to;
    edges.set(id,{id,a,b,west:Math.min(a[0],b[0]),east:Math.max(a[0],b[0]),
     south:Math.min(a[1],b[1]),north:Math.max(a[1],b[1])});
   }
  }
  return [...edges.values()];
 }
 function fraction(p,a,b){
  const scale=Math.cos(a[1]*D),x=(b[0]-a[0])*scale,y=b[1]-a[1];
  return ((p[0]-a[0])*scale*x+(p[1]-a[1])*y)/(x*x+y*y);
 }
 function roadSpans(a,b,edges){
  const margin=.4/M,lonMargin=margin/Math.max(.1,Math.cos(a[1]*D));
  const west=Math.min(a[0],b[0])-lonMargin,east=Math.max(a[0],b[0])+lonMargin;
  const south=Math.min(a[1],b[1])-margin,north=Math.max(a[1],b[1])+margin;
  const spans=[];
  for(const edge of edges){
   if(edge.west>east||edge.east<west||edge.south>north||edge.north<south||
      cosine(a,b,edge.a,edge.b)<.98)continue;
   const x=fraction(edge.a,a,b),y=fraction(edge.b,a,b);
   const start=Math.max(0,Math.min(x,y)),end=Math.min(1,Math.max(x,y));
   if(end-start<1e-7)continue;
   const u=point(a,b,start),v=point(a,b,end);
   const from=projection(u,edge.a,edge.b),to=projection(v,edge.a,edge.b);
   if(from.offset>.35||to.offset>.35||Math.abs(to.t-from.t)<1e-7)continue;
   spans.push({edge,start,end,from:from.t,to:to.t});
  }
  spans.sort((x,y)=>x.start-y.start);
  return spans.some((span,i)=>i&&span.start<spans[i-1].end-1e-6)?[]:spans;
 }
 function attributeRoad({edge,from,to},previous,next,novel,repeat){
  const lo=Math.min(from,to),hi=Math.max(from,to),epsilon=1e-7;
  const travelled=previous.get(edge.id)||[];
  const parts=[];
  let start=lo;
  for(const [a,b] of travelled){
   if(b<=lo+epsilon)continue;
   if(a>=hi-epsilon)break;
   const end=Math.min(b,hi);
   if(a>start+epsilon)parts.push([start,a,false]);
   if(end>start+epsilon)parts.push([Math.max(start,a),end,true]);
   start=Math.max(start,end);
  }
  if(start<hi-epsilon)parts.push([start,hi,false]);
  for(const [a,b,seen] of from<to?parts:parts.toReversed()){
   const id=edge.id+':'+a.toFixed(7)+':'+b.toFixed(7);
   const p=point(edge.a,edge.b,from<to?a:b),q=point(edge.a,edge.b,from<to?b:a);
   // A journey can double back without promoting itself to repeated coverage.
   const target=seen?repeat:novel;
   target.set(id,{id,a:p,b:q,type:'source',source:'saved-match'});
  }
  const united=[];
  for(const interval of [...(next.get(edge.id)||[]),[lo,hi]].sort((a,b)=>a[0]-b[0])){
   const last=united.at(-1);
   if(last&&last[1]>=interval[0]-epsilon)last[1]=Math.max(last[1],interval[1]);
   else united.push(interval.slice());
  }
  next.set(edge.id,united);
 }
 function accumulate(history,route){
  const next=createLedger(history),novel=new Map(),repeat=new Map();
  const previousSources=history?.sources||history,previousRoads=history?.roads||new Map();
  const prepared=cleanup(route);
  const edges=pinnedEdges(route.independentRoadReferences);
  const addSource=(a,b)=>{
   for(const part of sourceIntervals(a,b)){
    const target=previousSources?.has(part.id)?repeat:novel;
    if(!target.has(part.id))target.set(part.id,part);
    next.sources.add(part.id);
   }
  };
  for(const line of prepared.parts||[])for(let i=1;i<(line?.length||0);i++){
   const a=line[i-1],b=line[i];
   if(!supportedLeg(route,a,b))continue;
   let at=0;
   for(const span of roadSpans(a,b,edges)){
    if(span.start>at)addSource(point(a,b,at),point(a,b,span.start));
    attributeRoad(span,previousRoads,next.roads,novel,repeat);
    at=span.end;
   }
   if(at<1)addSource(point(a,b,at),b);
  }
  return {novel:[...novel.values()],repeat:[...repeat.values()],next,held:prepared.held,
   centrelinePromotions:prepared.centrelinePromotions,roadEvidence:prepared.roadEvidence,links:prepared.links,
   sourceSegments:(route?.parts||[]).reduce((sum,line)=>sum+Math.max(0,(line?.length||0)-1),0)};
 }
 function evidence(route){
  const referenceRoute=route.independentRoadReferences?.length?
   {...route,refs:PublicRoadSource.merge(route.refs,route.independentRoadReferences)}:route;
  return CentrelinePromotion.evidence(referenceRoute);
 }
 return {intervals,accumulate,cleanup,evidence,key,createLedger};
})();
if(typeof module!=='undefined')module.exports=RouteLedCoverage;
