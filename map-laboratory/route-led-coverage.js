/* LAB-only route-led cumulative experiment.
 * Accepted, manually corrected, RouteFirst reconstructed geometry is primary.
 * No extra network match, inferred gap, connector or branch is manufactured.
 */
const CentrelinePromotion=typeof module!=='undefined'?require('./roadside-centreline.js'):RoadsideCentreline;
const PublicRoadSource=typeof module!=='undefined'?require('./independent-road-network.js'):IndependentRoadNetwork;
const RouteEdits=typeof module!=='undefined'?require('./route-first.js'):RouteFirst;
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
 // Use a previously traversed pedestrian corridor within this journey
 // when a sparse return takes an unobserved street detour between the
 // same junctions. An unobserved exact retrace into a service road can
 // also be withheld without inferring any replacement geometry.
 function correctSparsePedestrianRetraces(route,held){
  if(!route.onFoot||route.corrected||route.source!=='saved match'||
     route.sourceType!=='timeline_import'||route.parts.length!==1||
     route.recorded?.length!==1||route.railTransitions?.length)return route;
  const line=route.parts[0],raw=route.recorded[0];
  if(raw.length<3||!raw.every(valid)||!line.every(valid)||
     !raw.slice(1).some((fix,i)=>distance(raw[i],fix)>150))return route;

  const edges=line.slice(1).map((b,i)=>[line[i],b]);
  const pathRefs=segments(route.refs?.filter(ref=>!['sidewalk','crossing'].includes(ref.footway)),
   /^(footway|cycleway|path|pedestrian|track)$/);
  const follows=([a,b],network)=>network.some(([u,v])=>cosine(a,b,u,v)>.97&&
   [a,b].every(p=>projection(p,u,v).offset<=1.25));
  if(!pathRefs.length)return route;

  const length=[0];
  for(const edge of edges)length.push(length.at(-1)+distance(...edge));
  const vertices=line.map(p=>p.join(','));
  const visits=new Map();
  for(let i=0;i<vertices.length;i++){
   const indices=visits.get(vertices[i])||[];
   indices.push(i);
   visits.set(vertices[i],indices);
  }
  const streetRefs=(route.refs||[]).filter(ref=>road.test(ref.kind))
   .map(ref=>segments([ref],road));
  const service=segments(route.refs,/^service$/);
  const receipts=[],result=[];
  const hasFix=(span,radius)=>raw.some(fix=>nearest(fix,span)<=radius);
  const pathMatch=new Map();
  const onPath=i=>{
   if(!pathMatch.has(i))pathMatch.set(i,follows(edges[i],pathRefs));
   return pathMatch.get(i);
  };

  for(let i=0;i<line.length;i++){
   result.push(line[i]);
   if(i===0||i>=line.length-2||!onPath(i-1))continue;
   const revisit=visits.get(vertices[i]);
   const loopEnd=revisit.find(j=>j>i&&j<edges.length&&length[j]-length[i]>=80);
   if(loopEnd!==undefined&&length[loopEnd]-length[i]<=400&&(loopEnd-i)%2===0&&
      onPath(loopEnd)&&vertices.slice(i,(i+loopEnd)/2)
       .every((vertex,k)=>vertex===vertices[loopEnd-k])){
    const loop=edges.slice(i,loopEnd);
    const serviceMetres=loop.reduce((sum,edge)=>sum+(follows(edge,service)?distance(...edge):0),0);
    if(serviceMetres>=70&&!hasFix(loop,32)){
     receipts.push({reason:'unobserved-service-retrace',metres:Number((length[loopEnd]-length[i]).toFixed(1))});
     i=loopEnd;
     continue;
    }
   }

   if(!streetRefs.length||i+1>=edges.length)continue;
   // The current trace must be returning along the earlier path before
   // it diverges. The same return must continue after the second junction.
   const candidates=[];
   for(const previous of revisit){
    if(previous>=i-1||vertices[previous+1]!==vertices[i-1]||
       vertices[previous-1]===vertices[i+1])continue;
    for(let earlier=previous-1;earlier>0&&length[previous]-length[earlier]<=1600;earlier--){
     if(!onPath(earlier))break;
     const walked=length[previous]-length[earlier];
     if(walked<150)continue;
     for(const end of visits.get(vertices[earlier])){
      if(end<=i+1||vertices[end+1]!==vertices[earlier-1]||
         vertices[end-1]===vertices[earlier+1])continue;
      const detour=length[end]-length[i];
      if(detour<walked*.65||detour>walked*1.8)continue;
      const span=edges.slice(i,end),alternate=edges.slice(earlier,previous);
      const roadRuns=streetRefs.map(ref=>span.filter(edge=>follows(edge,ref))).filter(run=>run.length);
      const roadEdges=[...new Set(roadRuns.flat())];
      const streetMetres=roadEdges.reduce((sum,edge)=>sum+distance(...edge),0);
      if(roadRuns.length<2||streetMetres<60||!hasFix(alternate,24)||hasFix(roadEdges,28))continue;
      // A recorded point that favours an isolated part of the detour
      // over the returned path is evidence for keeping that detour.
      if(raw.some(fix=>nearest(fix,span)<30&&nearest(fix,alternate)>40))continue;
      candidates.push({earlier,previous,end,detour,walked});
     }
    }
   }
   if(candidates.length!==1)continue;
   const {earlier,previous,end,detour,walked}=candidates[0];
   result.push(...line.slice(earlier,previous).reverse());
   receipts.push({reason:'unobserved-street-detour-from-pedestrian-retrace',
    metres:Number(detour.toFixed(1)),reusedMetres:Number(walked.toFixed(1))});
   i=end;
  }
  held.push(...receipts);
  return receipts.length?{...route,parts:[result]}:route;
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
   // Sparse observations on two paths cannot disprove a direct through
   // street between them. Only withhold a run that actually detours.
   if(!returns&&distance(line[run.start],line[end])>=metres*.85)continue;
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
 function cleanup(route,footways=publicFootways(route),edges=pinnedEdges(route.independentRoadReferences)){
  if(route.pairedWalk)return {parts:[route.pairedWalk.line],held:[],centrelinePromotions:[],links:[]};
  const roads=segments(route?.refs,road),feet=segments(route?.refs,foot);
  // Static road geometry comes only from this journey's own references
  // or the pinned public OSM network, never from other journeys.
  const physical=route.independentRoadReferences?.length?
   {...route,refs:PublicRoadSource.merge(route.refs,route.independentRoadReferences)}:route;
  const parts=[],held=[];
  const retraced=correctSparsePedestrianRetraces(physical,held);
  const withPhysicalRoads=withholdRoadExcursions(retraced,held);
  const screening=route.diagnosticsRequested?CentrelinePromotion.evidence(withPhysicalRoads):null;
  const shadowed=footways.length?legacyFootwayReferences(route.refs,footways,edges):new Set();
  const alignmentRoute=shadowed.size?
   {...withPhysicalRoads,refs:withPhysicalRoads.refs.filter(ref=>!shadowed.has(ref))}:withPhysicalRoads;
  const aligned=route.onFoot===false||route.source==='recorded'?
   restoreRoundabout(route):CentrelinePromotion.align(alignmentRoute);
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
  if(route.onFoot&&route.source==='saved match'&&route.independentRoadReferences?.length){
   const raw=(route.recorded||[]).flat().filter(valid);
   for(const line of output)if(line?.length>=4){
    if(route.corrected){
     if(!route.removedEdges?.length)continue;
     // Work within one retained component. Keep the manual cut points and
     // reject any new coverage that follows an explicitly removed edge.
     const candidate=line.slice(),refs=withPhysicalRoads.refs;
     const repairs=CentrelinePromotion.closePedestrianStreetCorridors(candidate,refs,raw);
     if(!repairs.length||candidate[0]!==line[0]||candidate.at(-1)!==line.at(-1))continue;
     const removed=route.removedEdges;
     const edgeKey=(a,b)=>[a.join(','),b.join(',')].sort().join(';');
     const kept=new Set(line.slice(1).map((b,i)=>edgeKey(line[i],b)));
     const restoresCut=candidate.slice(1).some((b,i)=>{
      const a=candidate[i];
      if(kept.has(edgeKey(a,b)))return false;
      return overlapsRemovedEdge(a,b,removed);
     });
     if(restoresCut)continue;
     line.splice(0,line.length,...candidate);
     aligned.promoted.push(...repairs);
     continue;
    }
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
 function pinnedEdges(refs,footways=false){
  const edges=new Map();
  for(const ref of refs||[]){
   if((!footways&&!(road.test(ref.kind)||ref.kind==='pedestrian'&&ref.name||ref.sharedCrossing))||
      !ref.nodes||!ref.coords||ref.nodes.length!==ref.coords.length)continue;
   for(let i=1;i<ref.coords.length;i++){
    let a=ref.coords[i-1],b=ref.coords[i],from=ref.nodes[i-1],to=ref.nodes[i];
    if(!valid(a)||!valid(b)||from===to||distance(a,b)<.1)continue;
    if(from>to)[a,b,from,to]=[b,a,to,from];
    const id=(footways?'footway:':'road:')+from+':'+to;
    edges.set(id,{id,a,b,from,to,ref,west:Math.min(a[0],b[0]),east:Math.max(a[0],b[0]),
     south:Math.min(a[1],b[1]),north:Math.max(a[1],b[1])});
   }
  }
  return [...edges.values()];
 }
 function fraction(p,a,b){
  const scale=Math.cos(a[1]*D),x=(b[0]-a[0])*scale,y=b[1]-a[1];
  return ((p[0]-a[0])*scale*x+(p[1]-a[1])*y)/(x*x+y*y);
 }
 // Attribute a short split and rejoin on a named local street to one
 // mapped road line. Require two complete, narrow arms in the public graph.
 function shortStreetForks(edges){
  const adjacency=new Map(),aliases=new Map(),merges=new Map();
  for(const edge of edges){
   const {ref}=edge;
   if(!ref.name||!Number.isSafeInteger(ref.id)||
      !/^(tertiary|unclassified|residential)$/.test(ref.kind)||
      ref.bridge==='yes'||ref.tunnel==='yes'||(ref.layer&&ref.layer!=='0'))continue;
   for(const node of [edge.from,edge.to]){
    if(!adjacency.has(node))adjacency.set(node,[]);
    adjacency.get(node).push(edge);
   }
  }
  const same=(edge,ref)=>edge.ref.name===ref.name&&edge.ref.kind===ref.kind;
  const other=(edge,node)=>edge.from===node?edge.to:edge.from;
  const arm=(node,first)=>{
   const path=[],seen=new Set([node]);
   let edge=first,length=0;
   while(edge){
    const forward=edge.from===node;
    path.push({edge,forward,start:forward?edge.a:edge.b,end:forward?edge.b:edge.a});
    length+=distance(edge.a,edge.b);
    node=other(edge,node);
    if(length>115||seen.has(node))return null;
    seen.add(node);
    const next=adjacency.get(node).filter(candidate=>same(candidate,first.ref));
    if(next.length!==2)return next.length===3?{node,path,length}:null;
    edge=next.find(candidate=>candidate!==edge);
   }
   return null;
  };
  for(const [node,incident] of adjacency){
   for(let a=0;a<incident.length;a++){
    const first=incident[a],streets=incident.filter(edge=>same(edge,first.ref));
    if(streets.length!==3)continue;
    for(const second of incident.slice(a+1)){
     if(!same(second,first.ref))continue;
     const left=arm(node,first),right=arm(node,second);
     if(!left||!right||left.node!==right.node||node>=left.node)continue;
     const start=left.path[0].start,end=left.path.at(-1).end,span=distance(start,end);
     if(span<15||Math.max(left.length,right.length)>span*1.16)continue;
     const station=p=>fraction(p,start,end);
     const measured=path=>path.map(item=>({...item,lo:station(item.start),hi:station(item.end)}));
     const x=measured(left.path),y=measured(right.path);
     if([...x,...y].some(item=>item.hi-item.lo<1e-6))continue;
     const close=(path,otherPath)=>path.every(({start,end})=>
      [start,point(start,end,.5),end].every(p=>nearest(p,otherPath)<9));
     if(!close(x,y.map(e=>[e.start,e.end]))||!close(y,x.map(e=>[e.start,e.end])))continue;
     // OSM way identity makes the representative independent of replay order.
     const oldest=path=>Math.min(...path.map(({edge})=>edge.ref.id));
     if(oldest(x)===oldest(y))continue;
     const [target,alternate]=oldest(x)<oldest(y)?[x,y]:[y,x];
     if(merges.has(node)||merges.has(left.node))continue;
     const boundary=[node,left.node];
     const ends=boundary.map((id,index)=>{
      const edgeAt=path=>index?path.at(-1).edge:path[0].edge;
      const forkEdges=[edgeAt(target),edgeAt(alternate)];
      const through=adjacency.get(id).find(edge=>same(edge,first.ref)&&!forkEdges.includes(edge));
      return {forkEdges,through,junction:through.from===id?through.a:through.b,id};
     });
     for(const merge of ends)merges.set(merge.id,merge);
     for(const item of alternate)aliases.set(item.edge.id,{...item,target});
    }
   }
  }
  return {aliases,merges};
 }
 function forkSeamSpans(a,b,merges){
  const length=distance(a,b);
  if(length<2||length>22)return null;
  const candidates=[];
  for(const {forkEdges,through,junction,id} of merges.values()){
   if(distance(a,junction)<1||distance(b,junction)<1||
      projection(junction,a,b).offset>3)continue;
   for(const branch of forkEdges){
    for(const [first,last] of [[branch,through],[through,branch]]){
     const at=projection(a,first.a,first.b),bt=projection(b,last.a,last.b);
     if(at.offset>.45||bt.offset>.45)continue;
     const end=first.from===id?0:1,start=last.from===id?0:1;
     if(Math.abs(end-at.t)*distance(first.a,first.b)<.5||
        Math.abs(start-bt.t)*distance(last.a,last.b)<.5)continue;
     candidates.push([{edge:first,start:0,from:at.t,to:end},
      {edge:last,start:0,from:start,to:bt.t,end:1}]);
    }
   }
  }
  return candidates.length===1?candidates[0]:null;
 }
 function* streetForkSpans(span,aliases){
  const fork=aliases.get(span.edge.id);
  if(!fork){yield span;return;}
  const station=t=>fork.lo+(fork.hi-fork.lo)*(fork.forward?t:1-t);
  const start=station(span.from),end=station(span.to);
  for(const {edge,forward,lo,hi} of start<end?fork.target:fork.target.toReversed()){
   const a=Math.max(lo,Math.min(start,end)),b=Math.min(hi,Math.max(start,end));
   if(b-a<1e-7)continue;
   const fraction=t=>forward?(t-lo)/(hi-lo):(hi-t)/(hi-lo);
   yield {edge,from:fraction(start<end?a:b),to:fraction(start<end?b:a)};
  }
 }
 // The public index can identify two ways as parts of one walking
 // corridor. Map only parallel portions on the same surface street.
 function* walkingCarriagewaySpans(span,edges,removed){
  const {edge,from,to}=span;
  const ids=edge.ref.walkingCarriageway;
  if(!ids||!surface(edge.ref)){yield span;return;}
  const a=point(edge.a,edge.b,from),b=point(edge.a,edge.b,to);
  const candidates=edges.filter(other=>ids.includes(other.ref.id)&&
   other.ref.name===edge.ref.name&&other.ref.kind===edge.ref.kind&&surface(other.ref));
  const mapped=roadSpans(a,b,candidates,15,.95);
  let at=0;
  for(const part of mapped){
   if(overlapsRemovedEdge(point(part.edge.a,part.edge.b,part.from),
    point(part.edge.a,part.edge.b,part.to),removed))continue;
   const original=t=>from+(to-from)*t;
   if((part.start-at)*distance(a,b)>.5)yield {...span,from:original(at),to:original(part.start)};
   yield part;
   at=part.end;
  }
  if(!at||(1-at)*distance(a,b)>.5)yield {...span,from:from+(to-from)*at};
 }
 function overlapsRemovedEdge(a,b,removed){
  return (removed||[]).some(([u,v])=>{
   if(cosine(a,b,u,v)<=.9)return false;
   const x=fraction(u,a,b),y=fraction(v,a,b);
   const lo=Math.max(0,Math.min(x,y)),hi=Math.min(1,Math.max(x,y));
   return (hi-lo)*distance(a,b)>.5&&
    [point(a,b,lo),point(a,b,hi)].every(p=>projection(p,u,v).offset<=.6);
  });
 }
 function surface(ref){
  return (!ref.layer||ref.layer==='0')&&ref.bridge!=='yes'&&ref.tunnel!=='yes'&&ref.indoor!=='yes';
 }
 function pavementEdges(refs){
  const sidewalks=refs.filter(ref=>ref.kind==='footway'&&ref.footway==='sidewalk'&&ref.name&&surface(ref));
  const atNode=new Map();
  const edges=[];
  function append(name,coords,roadWays){
   for(let i=1;i<coords.length;i++)edges.push({name,u:coords[i-1],v:coords[i],roadWays});
  }
  for(const ref of refs){
   if(ref.kind!=='footway'||!surface(ref))continue;
   if(ref.displayPavement)append(ref.displayPavement,ref.coords);
   for(const {name,fromNode,toNode,roadWays} of ref.pavementSections||[]){
    const a=ref.nodes.indexOf(fromNode),b=ref.nodes.indexOf(toNode);
    if(a>=0&&b>=0)append(name,ref.coords.slice(Math.min(a,b),Math.max(a,b)+1),roadWays);
   }
  }
  for(const ref of refs)
   if(ref.kind==='footway'&&ref.footway==='sidewalk'&&!ref.name&&!ref.displayPavement&&surface(ref))
    append('',ref.coords);
  for(const {name,nodes,coords} of sidewalks){
   append(name,coords);
   for(const node of nodes||[]){
    if(!atNode.has(node))atNode.set(node,new Set());
    atNode.get(node).add(name);
   }
  }
  // A tagged crossing can continue the pavement over a side street.
  // Its heading must still agree with the named road at attribution time.
  for(const ref of refs){
   if(ref.kind!=='footway'||ref.footway!=='crossing'||!ref.nodes?.length||!surface(ref))continue;
   const start=atNode.get(ref.nodes[0]),end=atNode.get(ref.nodes.at(-1));
   if(start?.size!==1||end?.size!==1)continue;
   const [name]=start;
   if(end.has(name))append(name,ref.coords);
  }
  return edges;
 }
 // Require a mapped sidewalk or an approved pavement section. An
 // unnamed sidewalk needs one closely parallel, named street.
 function pavementRoadSpans(a,b,pavements,roads,removed,endcap){
  const mid=point(a,b,.5),names=new Set(),sections=new Map();
  let unnamed=false;
  for(const {name,u,v,roadWays} of pavements){
   if(roadWays){
    if(!sections.has(roadWays))sections.set(roadWays,{name,legs:[]});
    sections.get(roadWays).legs.push([u,v]);
    continue;
   }
   const offsets=[a,mid,b].map(p=>projection(p,u,v).offset);
   if(cosine(a,b,u,v)>.93&&(offsets.every(d=>d<=.8)||
      name===endcap&&offsets.every(d=>d<=5)&&Math.min(...offsets)<=.8)){
    if(name)names.add(name);
    else unnamed=true;
   }
  }
  const pinned=[];
  for(const [roadWays,{name,legs}] of sections){
   const candidates=(roads.get(name)||[]).filter(({ref})=>roadWays.includes(ref.id));
   const onRoad=p=>candidates.some(edge=>projection(p,edge.a,edge.b).offset<=.35);
   const nearPath=p=>legs.some(([u,v])=>projection(p,u,v).offset<=.8&&cosine(a,b,u,v)>.88);
   if((nearPath(a)||nearPath(b))&&[a,b].every(p=>nearPath(p)||onRoad(p))&&
      legs.some(([u,v])=>projection(mid,u,v).offset<=6&&cosine(a,b,u,v)>.88)){
    names.add(name);
    pinned.push(...candidates);
   }
  }
  const inferred=unnamed&&!names.size&&!endcap;
  if(inferred)for(const [name,edges] of roads){
   if(edges.some(edge=>cosine(a,b,edge.a,edge.b)>.93&&projection(mid,edge.a,edge.b).offset<4.5))names.add(name);
  }
  if(names.size!==1)return [];
  const [name]=names;
  if(endcap&&name!==endcap)return [];
  const near=pinned.length?[...new Set(pinned)]:roads.get(name)||[];
  const normal=roadSpans(a,b,near,inferred?4.5:12,.88);
  if(!normal.length)return [];
  // A loose pavement projection may stop at a corner or at a fork.
  // Require most of the leg and one connected road trace.
  const connected=normal.every((span,i)=>!i||distance(point(normal[i-1].edge.a,normal[i-1].edge.b,normal[i-1].to),
   point(span.edge.a,span.edge.b,span.from))<1);
  const length=normal.reduce((sum,span)=>sum+span.end-span.start,0);
  if(!connected||length<(endcap ? .75 : .85)||normal.some(({edge,from,to})=>
   overlapsRemovedEdge(point(edge.a,edge.b,from),point(edge.a,edge.b,to),removed)))return [];
  if(endcap&&(normal[0].start+1-normal.at(-1).end)*distance(a,b)>3)return [];
  return normal;
 }
 function roadSpans(a,b,edges,tolerance=.35,heading=.98){
  const margin=(tolerance+.05)/M,lonMargin=margin/Math.max(.1,Math.cos(a[1]*D));
  const west=Math.min(a[0],b[0])-lonMargin,east=Math.max(a[0],b[0])+lonMargin;
  const south=Math.min(a[1],b[1])-margin,north=Math.max(a[1],b[1])+margin;
  const spans=[];
  for(const edge of edges){
   if(edge.west>east||edge.east<west||edge.south>north||edge.north<south||
      cosine(a,b,edge.a,edge.b)<heading)continue;
   const x=fraction(edge.a,a,b),y=fraction(edge.b,a,b);
   const start=Math.max(0,Math.min(x,y)),end=Math.min(1,Math.max(x,y));
   if(end-start<1e-7)continue;
   const u=point(a,b,start),v=point(a,b,end);
   const uv=tolerance>.35?[u,v].map((p,i)=>{
    const t=((i?end:start)-x)/(y-x);
    return {t,offset:distance(p,point(edge.a,edge.b,t))};
   }):[projection(u,edge.a,edge.b),projection(v,edge.a,edge.b)];
   const [{t:from,offset:offsetFrom},{t:to,offset:offsetTo}]=uv;
   if(offsetFrom>tolerance||offsetTo>tolerance||Math.abs(to-from)<1e-7)continue;
   spans.push({edge,start,end,from,to});
  }
  spans.sort((x,y)=>x.start-y.start);
  return spans.some((span,i)=>i&&span.start<spans[i-1].end-1e-6)?[]:spans;
 }
 function publicFootways(route){
  if(!route.onFoot||route.source!=='saved match')return [];
  return pinnedEdges((route.independentRoadReferences||[]).filter(ref=>
   Number.isSafeInteger(ref.id)&&ref.kind==='footway'&&!ref.footway&&!ref.sharedCrossing&&surface(ref)),true);
 }
 function legacyFootwaySpans(a,b,footways,roads){
  const spans=roadSpans(a,b,footways,5,.94);
  const way=spans[0]?.edge.ref.id;
  if(way===undefined||!spans.every(({edge})=>edge.ref.id===way))return [];
  for(const edges of roads.values()){
   if(roadSpans(a,b,edges,5,.94).reduce((sum,span)=>sum+span.end-span.start,0)>.5)return [];
  }
  return spans;
 }
 // Prevent a saved road reference from pulling an already mapped walk
 // off a footway when that legacy reference mostly describes the footway.
 function legacyFootwayReferences(refs,footways,edges){
  const shadowed=new Set(),roads=Map.groupBy(edges,edge=>edge.ref);
  for(const ref of refs||[]){
   if(!ref.name||!road.test(ref.kind)||!surface(ref)||!ref.coords?.every(valid))continue;
   let total=0;
   const overlap=new Map();
   for(let i=1;i<ref.coords.length;i++){
    const a=ref.coords[i-1],b=ref.coords[i],length=distance(a,b);
    total+=length;
    for(const span of legacyFootwaySpans(a,b,footways,roads)){
     const way=span.edge.ref.id;
     overlap.set(way,(overlap.get(way)||0)+(span.end-span.start)*length);
    }
   }
   if([...overlap.values()].some(backed=>backed>=24&&backed>=total*.75))shadowed.add(ref);
  }
  return shadowed;
 }
 // A legacy walking match can still label a pedestrianised section as
 // a road. A close, sustained match to one public surface footway gives
 // both map sources a stable interval identity.
 function sharedFootwaySpans(line,legs,seams,transitions,footways,edges,legacy,removed){
  const roads=Map.groupBy(edges,edge=>edge.ref);
  const candidates=legs.map((leg,i)=>{
   if(!leg||leg.spans.length||seams?.omitted.has(i))return null;
   const a=line[i],b=line[i+1];
   const exact=roadSpans(a,b,footways);
   if(exact.length)return {spans:exact,exact:true};
   if(!legacy.length)return null;
   const mid=point(a,b,.5);
   if(!legacy.some(([u,v])=>cosine(a,b,u,v)>.96&&
      [a,mid,b].every(p=>projection(p,u,v).offset<=.8)))return null;
   const spans=legacyFootwaySpans(a,b,footways,roads);
   if(!spans.length||spans.some(({edge,from,to})=>overlapsRemovedEdge(
       point(edge.a,edge.b,from),point(edge.a,edge.b,to),removed)))return null;
   return {spans,way:spans[0].edge.ref.id};
  });
  for(const [start,{end}] of transitions||[])
   candidates.fill(null,start,end);
  const result=new Map();
  for(let i=0;i<candidates.length;){
   const first=candidates[i];
   if(!first||first.exact){
    if(first?.exact)result.set(i,first.spans);
    i++;
    continue;
   }
   let end=i,metres=0;
   const intervals=new Map();
   while(candidates[end]?.way===first.way){
    for(const {edge,from,to} of candidates[end].spans){
     if(!intervals.has(edge.id))intervals.set(edge.id,{edge,lo:1,hi:0});
     const interval=intervals.get(edge.id);
     interval.lo=Math.min(interval.lo,from,to);
     interval.hi=Math.max(interval.hi,from,to);
    }
    end++;
   }
   for(const {edge,lo,hi} of intervals.values())metres+=(hi-lo)*distance(edge.a,edge.b);
   if(metres>=24){
    for(let j=i;j<end;j++)result.set(j,candidates[j].spans);
    for(let j=i+1;j<end;j++){
     const left=candidates[j-1].spans.at(-1),right=candidates[j].spans[0];
     if(left.edge!==right.edge||left.end!==1||right.start!==0||
        (right.from-left.to)*(right.to-right.from)<=0)continue;
     const a=point(left.edge.a,left.edge.b,left.to),b=point(right.edge.a,right.edge.b,right.from);
     if(distance(a,b)<=.5&&!overlapsRemovedEdge(a,b,removed))right.from=left.to;
    }
   }
   i=end;
  }
  return result;
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
 // Moving a named pavement onto a road can expose a lateral seam
 // at the end of the projected run. Only consider short, continuous
 // transitions that start and finish on the same credited road.
 function pavementSeams(line,legs,refs){
  const omitted=new Set(),links=[];
  const pedestrian=segments(refs.filter(ref=>ref.kind==='steps'||
   ref.kind==='footway'&&!ref.footway||ref.kind==='pedestrian'),foot);
  const endPoint=(span,t)=>point(span.edge.a,span.edge.b,span[t]);
  for(let start=0;start<legs.length;start++){
   if(!legs[start]?.pavement)continue;
   const name=legs[start].spans[0].edge.ref.name;
   let end=start;
   while(end+1<legs.length&&legs[end+1]?.pavement&&
      legs[end+1].spans[0].edge.ref.name===name)end++;
   function join(boundary,step){
    const inner=step<0?boundary:boundary+1;
    const projected=endPoint(step<0?legs[boundary].spans[0]:legs[boundary].spans.at(-1),step<0?'from':'to');
    let length=0;
    for(let i=boundary+step;legs[i]&&!legs[i].pavement;i+=step){
     if(length>18)break;
     const {spans}=legs[i],adjacent=step<0?spans.at(-1):spans[0];
     if(adjacent?.edge.ref.name===name){
      const outer=step<0?i+1:i;
      const anchor=endPoint(adjacent,step<0?'to':'from');
      if(length<1||length>18||distance(line[outer],anchor)>1||
         distance(anchor,projected)>6||distance(line[inner],projected)<2)break;
      const lower=Math.min(inner,outer),upper=Math.max(inner,outer);
      const a=line[lower],b=line[upper];
      if(cosine(a,b,adjacent.edge.a,adjacent.edge.b)>.65||
         line.slice(lower+1,upper).some(p=>projection(p,a,b).offset>2))break;
      for(let j=lower;j<upper;j++)omitted.add(j);
      return outer;
     }
     length+=distance(line[i],line[i+1]);
    }
    return null;
   }
   function keepApproach(outer,boundary,step){
    if(outer===null)return;
    let short=0;
    for(let i=boundary+step;legs[i]&&!legs[i].spans.length;i+=step){
     const a=line[i],b=line[i+1],length=distance(a,b);
     if(length<.5){
      short+=length;
      if(short>2)return;
      continue;
     }
     if(nearest(point(a,b,.5),pedestrian)>2.5)return;
     const inner=step<0?i+1:i;
     const path=line.slice(Math.min(inner,outer),Math.max(inner,outer)+1);
     const metres=path.slice(1).reduce((sum,b,i)=>sum+distance(path[i],b),0);
     if(metres<=40)links.push(path);
     return;
    }
   }
   const before=join(start,-1),after=join(end,1);
   keepApproach(before,end,1);
   keepApproach(after,start,-1);
   start=end;
  }
  return {omitted,links};
 }
 // Keep a mapped crossing between two pavements of the same street
 // visible without adding a second line of street coverage.
 function pavementCrossings(line,legs,refs,omitted){
  const links=[];
  const crossings=segments(refs.filter(ref=>ref.footway==='crossing'&&surface(ref)),foot);
  for(let i=1;i<legs.length-1;i++){
   const prior=legs[i-1]?.pavement&&legs[i-1].spans.at(-1);
   if(!prior||legs[i]?.spans.length)continue;
   let length=0,backed=0,j=i;
   while(legs[j]&&!legs[j].spans.length&&length<=18){
    const a=line[j],b=line[j+1],metres=distance(a,b);
    length+=metres;
    if(crossings.some(([u,v])=>projection(point(a,b,.5),u,v).offset<1&&
       cosine(a,b,u,v)>.9))backed+=metres;
    j++;
   }
   const after=legs[j]?.pavement&&legs[j].spans[0];
   if(!after||after.edge.ref.name!==prior.edge.ref.name||
      distance(point(prior.edge.a,prior.edge.b,prior.to),point(after.edge.a,after.edge.b,after.from))>5)continue;
   const seam=length<=2;
   if(!seam&&(length>18||backed<3||backed<length*.65))continue;
   for(let k=i;k<j;k++)omitted.add(k);
   if(!seam)links.push(line.slice(i,j+1));
   i=j-1;
  }
  return links;
 }
 function shortRoadsideTransitions(line,legs,edges,trace,removed){
  const replacements=new Map();
  const endPoint=(span,t)=>point(span.edge.a,span.edge.b,span[t]);
  for(let i=0;i+2<legs.length;i++){
   const prior=legs[i]?.spans.at(-1);
   const name=prior?.edge.ref.name;
   if(!name||prior.end<.9)continue;
   let walked=0;
   for(let j=i+1;legs[j]&&j<legs.length;j++){
    const after=legs[j].spans[0];
    if(after?.edge.ref.name===name){
     if(j===i+1||walked>38||after.start>.1)break;
     const source=line.slice(i+1,j+1);
     if(source.slice(1).some((p,k)=>overlapsRemovedEdge(source[k],p,removed)))break;
     const before=[endPoint(prior,'from'),endPoint(prior,'to')];
     const following=[endPoint(after,'from'),endPoint(after,'to')];
     const coords=trace(name,source,before,following);
     if(!coords||coords.slice(1).some((b,k)=>overlapsRemovedEdge(coords[k],b,removed)))break;
     const roadLegs=coords.slice(1).map((b,k)=>roadSpans(coords[k],b,edges));
     if(roadLegs.some(spans=>!spans.length||spans[0].start>1e-6||spans.at(-1).end<1-1e-6||
      spans.some((span,k)=>k&&span.start-spans[k-1].end>1e-6)))break;
     replacements.set(i+1,{end:j,spans:roadLegs.flat()});
     i=j-1;
     break;
    }
    walked+=distance(line[j],line[j+1]);
    if(walked>38)break;
   }
  }
  return replacements;
 }
 // Keep the actual junction walk as an access link. Street coverage
 // follows the connected through line, without crediting a crossing arm.
 function junctionCrossings(line,legs,edges,network,trace,transitions,links){
  const paths=segments(network.filter(ref=>ref.kind==='footway'&&surface(ref)),foot);
  const crossings=segments(network.filter(ref=>ref.footway==='crossing'&&surface(ref)),foot);
  if(!paths.length)return;
  const endPoint=(span,t)=>point(span.edge.a,span.edge.b,span[t]);
  for(let i=1;i<legs.length-1;i++){
   const prior=legs[i-1]?.spans.at(-1);
   if(!prior?.edge.ref.name||legs[i]?.spans.length||transitions.has(i))continue;
   let walked=0,footMetres=0,crossingMetres=0,best;
   for(let j=i;legs[j]&&j<legs.length-1;j++){
    if(transitions.has(j))break;
    const a=line[j],b=line[j+1],metres=distance(a,b);
    walked+=metres;
    if(walked>65)break;
    const backed=refs=>refs.some(([u,v])=>projection(point(a,b,.5),u,v).offset<=2.5&&cosine(a,b,u,v)>.8);
    if(backed(paths))footMetres+=metres;
    if(backed(crossings))crossingMetres+=metres;
    const after=legs[j+1]?.spans[0];
    if(!after?.edge.ref.name||best&&crossingMetres<=best.crossingMetres)continue;
    const sameStreet=after.edge.ref.name===prior.edge.ref.name;
    if(sameStreet&&!legs[i-1].pavement&&!legs[j+1].pavement)continue;
    const directionPoint=(span,t,sign)=>point(span.edge.a,span.edge.b,
     span[t]+sign*Math.sign(span.to-span.from)*2/distance(span.edge.a,span.edge.b));
    const before=[directionPoint(prior,'to',-1),endPoint(prior,'to')];
    const following=[endPoint(after,'from'),directionPoint(after,'from',1)];
    if(crossingMetres<5){
     if(walked>8||prior.edge.ref.name!==after.edge.ref.name||distance(before[1],following[0])>6)continue;
    }else if(footMetres<walked*.66||walked-footMetres>13||
      cosine(before[0],before[1],following[0],following[1])<.8||
      fraction(following[1],...before)<=fraction(following[0],...before))continue;
    const source=line.slice(i,j+2);
    const separation=distance(before[1],following[0]);
    if(crossingMetres<5&&separation<=2){
     best={end:j+1,spans:[],source,crossingMetres};
     continue;
    }
    const coords=trace(source,before,following,[prior.edge.ref.name,after.edge.ref.name]);
    if(!coords)continue;
    const roadLegs=coords.slice(1).map((b,k)=>roadSpans(coords[k],b,edges));
    if(!roadLegs.length||roadLegs.some(spans=>!spans.length||spans[0].start>1e-6||
       spans.at(-1).end<1-1e-6||spans.some((span,k)=>k&&span.start-spans[k-1].end>1e-6)))continue;
    best={end:j+1,spans:roadLegs.flat(),source,crossingMetres};
   }
   if(best){
    transitions.set(i,{end:best.end,spans:best.spans});
    // Include the tiny unclipped end of the approach in its dashed trace.
    const entry=legs[best.end];
    if(entry?.pavement){
     const start=entry.spans[0].start,a=line[best.end],b=line[best.end+1];
     if(start>0&&start*distance(a,b)<=1){
      entry.entryLinked=true;
      best.source.push(point(a,b,start));
     }
    }
    links.push(best.source);
    i=best.end-1;
   }
  }
 }
 // A change between an independent footway and a projected
 // pavement can leave a lateral link. Preserve the saved access path
 // without crediting it as another section of the through road.
 function pavementAccessLinks(line,legs,pavements,footways,omitted){
  const sections=pavements.filter(ref=>ref.name);
  const links=[];
  for(let i=1;i<legs.length-1;i++){
   if(!legs[i]||legs[i].spans.length||omitted.has(i))continue;
   const a=line[i],b=line[i+1],length=distance(a,b);
   if(length<2||length>13)continue;
   const roadLeg=(index,section)=>legs[index]?.spans.some(({edge})=>
    section.roadWays?section.roadWays.includes(edge.ref.id):section.name===edge.ref.name);
   const pathLeg=index=>roadSpans(line[index],line[index+1],footways)
    .some(span=>span.end-span.start>.9);
   const link=sections.some(section=>{
    const {u,v,roadWays}=section;
    const atA=projection(a,u,v).offset,atB=projection(b,u,v).offset;
    if(atA>=8||atB>=8||cosine(a,b,u,v)>=.6)return false;
    return roadLeg(i+1,section)&&(roadWays||atA<.8)&&pathLeg(i-1)||
     roadLeg(i-1,section)&&(roadWays||atB<.8)&&pathLeg(i+1);
   });
   if(!link)continue;
   omitted.add(i);
   links.push([a,b]);
  }
  return links;
 }
 // Keep a short, mapped terminal approach with its connection to the
 // through route. Projecting part of it independently can strand the rest.
 function extendTerminalApproaches(line,legs,footways,links,omitted,raw){
  if(raw.length<2)return links;
  return links.map(link=>{
   const first=line.indexOf(link[0]),last=line.indexOf(link.at(-1));
   if(first<0||last<=first)return link;
   for(const start of [true,false]){
    const end=start?0:legs.length,anchor=start?last:first;
    const terminal=legs[start?0:legs.length-1];
    if(Math.min(distance(line[end],raw[0]),distance(line[end],raw.at(-1)))>12)continue;
    const lo=Math.min(end,anchor),hi=Math.max(end,anchor);
    const approach=line.slice(lo,hi+1);
    const length=approach.slice(1).reduce((sum,b,i)=>sum+distance(approach[i],b),0);
    const span=start?terminal?.spans[0]:terminal?.spans.at(-1);
    const offsetPavement=terminal?.pavement&&span&&
     distance(line[end],point(span.edge.a,span.edge.b,start?span.from:span.to))>2;
    if(length>(offsetPavement?43:30))continue;
    const backed=legs.slice(lo,hi).every((leg,j)=>{
     const i=lo+j;
     if(i>=first&&i<last||omitted.has(i))return true;
     if(!leg)return false;
     if(leg.spans.length)return offsetPavement&&leg.pavement;
     return roadSpans(line[i],line[i+1],footways)
      .reduce((sum,span)=>sum+span.end-span.start,0)>.9;
    });
    if(!backed)continue;
    for(let i=lo;i<hi;i++)omitted.add(i);
    return approach;
   }
   return link;
  });
 }
 function accumulate(history,route){
  const removed=ref=>RouteEdits.isRemovedRoad(ref,route.removedRoadIds);
  const denied=[];
  if(route.removedRoadIds?.size){
   // Match only the actual excluded line. A footpath that runs near
   // the edited road is separate evidence and is left intact.
   const pinned=[...(route.independentRoadReferences||[]),...(route.displayRoadReferences||[])];
   const saved=route.removedRoadReferences||[];
   denied.push(...pinned.filter(removed),...saved);
   route={...route,
    independentRoadReferences:route.independentRoadReferences?.filter(ref=>!removed(ref)),
    displayRoadReferences:route.displayRoadReferences?.filter(ref=>!removed(ref)),
    forkContext:route.forkContext?.filter(ref=>!removed(ref)),
    pairedWalk:undefined};
  }
  const excluded=denied.map(ref=>pinnedEdges([{...ref,nodes:ref.nodes||ref.coords.map((_,i)=>i)}],true));
  const next=createLedger(history),novel=new Map(),repeat=new Map();
  const previousSources=history?.sources||history,previousRoads=history?.roads||new Map();
  const paired=route.pairedWalk;
  const edges=pinnedEdges(paired?.refs||route.independentRoadReferences);
  const footways=paired?pinnedEdges(paired.refs.filter(ref=>!road.test(ref.kind)),true):publicFootways(route);
  const prepared=cleanup(route,footways,edges);
  const displayEdges=!paired&&route.onFoot&&route.source==='saved match'?
   pinnedEdges(route.displayRoadReferences):[];
  const legacy=!paired&&footways.length?segments((route.refs||[]).filter(ref=>ref.name&&surface(ref)),road):[];
  const forks=!paired&&route.onFoot&&!route.corrected?shortStreetForks(
   route.forkContext?.length?[...edges,...pinnedEdges(route.forkContext)]:edges):null;
  const pavements=!paired&&route.onFoot&&route.source==='saved match'?
   pavementEdges(route.independentRoadReferences||[]):[];
  const names=new Set(pavements.map(ref=>ref.name));
  // A named pedestrian street also has a centreline. Resolve verified
  // short forks before projecting a pavement across both branches.
  const pavementRoads=Map.groupBy(edges.concat(displayEdges).filter(({id,ref})=>
   ref.name&&(names.has(ref.name)||names.has(''))&&(road.test(ref.kind)||ref.kind==='pedestrian')&&
   surface(ref)&&!forks?.aliases.has(id)),edge=>edge.ref.name);
  const attribute=span=>{
   for(const fork of forks?streetForkSpans(span,forks.aliases):[span])
    for(const mapped of !paired&&route.onFoot&&route.source==='saved match'?
     walkingCarriagewaySpans(fork,edges,route.removedEdges):[fork])
     attributeRoad(mapped,previousRoads,next.roads,novel,repeat);
  };
  const addSource=(a,b)=>{
   if(!excluded.length)return creditSource(a,b);
   const held=excluded.flatMap(edges=>roadSpans(a,b,edges))
    .sort((x,y)=>x.start-y.start);
   let start=0;
   for(const part of [...held,{start:1,end:1}]){
    if(part.start>start)creditSource(point(a,b,start),point(a,b,part.start));
    start=Math.max(start,part.end);
   }
  };
  const creditSource=(a,b)=>{
   for(const part of sourceIntervals(a,b)){
    const target=previousSources?.has(part.id)?repeat:novel;
    if(!target.has(part.id))target.set(part.id,part);
    next.sources.add(part.id);
   }
  };
  const accessLinks=[];
  const raw=route.onFoot&&route.source==='saved match'?(route.recorded||[]).flat().filter(valid):[];
  const trace=raw.length&&pavements.length?
   CentrelinePromotion.roadsideTransition(route.independentRoadReferences,raw):null;
  const junctionTrace=route.onFoot&&!route.corrected&&pavements.length?
   CentrelinePromotion.junctionContinuation(route.independentRoadReferences):null;
  for(const line of prepared.parts||[]){
   const legs=line.slice(1).map((b,i)=>{
    const a=line[i];
    if(!supportedLeg(route,a,b))return null;
    const seam=forks&&forkSeamSpans(a,b,forks.merges);
    const exact=seam||roadSpans(a,b,edges);
    if(!exact.length&&displayEdges.length){
     const display=roadSpans(a,b,displayEdges).filter(({edge,from,to})=>
      !overlapsRemovedEdge(point(edge.a,edge.b,from),point(edge.a,edge.b,to),route.removedEdges));
     if(display.length)return {spans:display};
    }
    if(exact.length||!pavements.length)return {spans:exact,seam};
    const spans=pavementRoadSpans(a,b,pavements,pavementRoads,route.removedEdges);
    return {spans,pavement:spans.length>0};
   });
   if(route.onFoot&&!route.corrected&&pavements.length&&legs.length>2){
    // A short join can change from a pavement to its projected street.
    for(let end=0;end<legs.length;end++){
     const adjacent=end===0?1:end-1;
     const before=legs[end-1]?.spans.at(-1),after=legs[end+1]?.spans[0];
     const inner=end>0&&end<legs.length-1;
     if(inner&&(!before||!after||before.edge.ref.name!==after.edge.ref.name||
        !legs[end-1].pavement&&!legs[end+1].pavement))continue;
     const adjacentSpans=legs[adjacent]?.spans;
     const anchor=end===0?adjacentSpans?.[0]:adjacentSpans?.at(-1);
     const name=anchor?.edge.ref.name;
     if(!name||!legs[end]||legs[end].spans.length||distance(line[end],line[end+1])>14)continue;
     const spans=pavementRoadSpans(line[end],line[end+1],pavements,pavementRoads,route.removedEdges,name);
     const boundary=end===0?spans.at(-1):spans[0];
     if(!boundary)continue;
     const mapped=forks?[...streetForkSpans(anchor,forks.aliases)]:[anchor];
     const join=end===0?mapped[0]:mapped.at(-1);
     if(!join||distance(point(join.edge.a,join.edge.b,end===0?join.from:join.to),
      point(boundary.edge.a,boundary.edge.b,end===0?boundary.to:boundary.from))>4)continue;
     legs[end]={spans,pavement:true,endcap:true};
    }
   }
   // Preserve the original pedestrian approach when its neighbouring
   // frontage pavement credits a street that is temporarily closed.
   const seamLegs=displayEdges.length?legs.map(leg=>leg?.pavement&&
    leg.spans.some(({edge})=>edge.ref.displayOnly)?{spans:[]}:leg):legs;
   const seams=pavements.length?pavementSeams(line,seamLegs,route.independentRoadReferences):null;
   const links=[];
   if(seams){
    links.push(...seams.links,...pavementCrossings(line,legs,route.independentRoadReferences,seams.omitted));
    if(!route.corrected)links.push(...pavementAccessLinks(line,legs,pavements,footways,seams.omitted));
   }
   const transitions=trace?shortRoadsideTransitions(line,legs,edges,trace,route.removedEdges):new Map();
   if(junctionTrace)junctionCrossings(line,legs,edges,route.independentRoadReferences,junctionTrace,transitions,links);
   accessLinks.push(...(!seams||route.corrected?links:extendTerminalApproaches(line,legs,footways,links,seams.omitted,raw)));
   const shared=footways.length?
    sharedFootwaySpans(line,legs,seams,transitions,footways,edges,legacy,route.removedEdges):null;
   for(let i=0;i<legs.length;i++){
    const transition=transitions?.get(i);
    if(transition){
     for(const span of transition.spans)attribute(span);
     i=transition.end-1;
     continue;
    }
    if(!legs[i]||seams?.omitted.has(i))continue;
    const a=line[i],b=line[i+1],{seam,endcap,entryLinked}=legs[i];
    const spans=shared?.get(i)||legs[i].spans;
    let at=0;
    for(const span of spans){
     if(!seam&&!((endcap||entryLinked)&&at===0)&&span.start>at)addSource(point(a,b,at),point(a,b,span.start));
     attribute(span);
     at=seam?1:span.end;
    }
    if(at<1&&!endcap)addSource(point(a,b,at),b);
   }
  }
  return {novel:[...novel.values()],repeat:[...repeat.values()],next,held:prepared.held,
   centrelinePromotions:prepared.centrelinePromotions,roadEvidence:prepared.roadEvidence,links:prepared.links,accessLinks,
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
