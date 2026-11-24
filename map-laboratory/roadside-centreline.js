/* General source-backed pedestrian-to-road centreline equivalence.
 * Works only on already accepted ordered saved-route geometry and matching
 * physical-road references. No GPS rematching or fabricated missing roads.
 * Runs are selected from sustained directional/proximity correspondence,
 * not individual nearest points (which would distort squares and shortcuts).
 */
const RoadsideCentreline=(()=>{
 const M=111320,D=Math.PI/180;
 const road=/^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(?:_link)?$/;
 const valid=p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
 const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*D/2),(a[1]-b[1])*M);
 const interpolate=(a,b,t)=>[a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t];
 function projected(p,a,b){
  const scale=Math.cos(p[1]*D),dx=(b[0]-a[0])*M*scale,dy=(b[1]-a[1])*M;
  const px=(p[0]-a[0])*M*scale,py=(p[1]-a[1])*M;
  const d=dx*dx+dy*dy,t=d>.01?Math.max(0,Math.min(1,(px*dx+py*dy)/d)):0;
  const position=interpolate(a,b,t);
  return {position,offset:metres(p,position),t};
 }
 // OSM may split a continuous road at a tag or junction. Join only the
 // unambiguous continuation of a named road at exact shared vertices.
 function corridors(refs){
  const groups=new Map(),paths=[];
  for(const ref of refs||[]){
   if(!road.test(ref?.kind)||!Array.isArray(ref.coords)||ref.coords.length<2||
      !ref.coords.every(valid))continue;
   if(!ref.nodes||!ref.name||metres(ref.coords[0],ref.coords.at(-1))<.1){
    paths.push(ref);continue;
   }
   const key=JSON.stringify([ref.name,ref.kind,ref.layer||'0',ref.bridge||'',ref.tunnel||'']);
   const group=groups.get(key)||[];
   group.push(ref);groups.set(key,group);
  }
  for(const group of groups.values()){
   const adjacency=new Map(),unused=new Set(group);
   for(const ref of group)for(const k of [ref.nodes[0],ref.nodes.at(-1)]){
    const options=adjacency.get(k)||[];
    options.push(ref);adjacency.set(k,options);
   }
   for(const first of group){
    if(!unused.delete(first))continue;
    let coords=first.coords,nodes=first.nodes;
    for(const backwards of [true,false]){
     if(backwards){coords=coords.toReversed();nodes=nodes.toReversed();}
     for(;;){
      const at=nodes.at(-1),options=adjacency.get(at);
      if(options?.length!==2)break;
      const next=options.find(ref=>unused.has(ref));
      if(!next)break;
      unused.delete(next);
      const forward=next.nodes[0]===at;
      const following=forward?next.coords:next.coords.toReversed();
      coords=coords.concat(following.slice(1));
      nodes=nodes.concat((forward?next.nodes:next.nodes.toReversed()).slice(1));
     }
     if(backwards){coords.reverse();nodes.reverse();}
    }
    paths.push({...first,coords,nodes});
   }
  }
  return paths;
 }
 function network(refs){
  const ways=[];
  for(const ref of corridors(refs)){
   const coords=ref.coords;
   if(!coords.every(valid))continue;
   const lengths=[0];for(let i=1;i<coords.length;i++)
    lengths.push(lengths[i-1]+metres(coords[i-1],coords[i]));
   if(lengths.at(-1)<30)continue;
   const minX=Math.min(...coords.map(p=>p[0])),maxX=Math.max(...coords.map(p=>p[0]));
   const minY=Math.min(...coords.map(p=>p[1])),maxY=Math.max(...coords.map(p=>p[1]));
   ways.push({coords,lengths,minX,maxX,minY,maxY,name:ref.name||'',id:ref.id});
  }
  return ways;
 }
 function snap(point,way){
  let best=null;
  for(let i=1;i<way.coords.length;i++){
   const p=projected(point,way.coords[i-1],way.coords[i]);
   if(!best||p.offset<best.offset)
    best={...p,along:way.lengths[i-1]+p.t*(way.lengths[i]-way.lengths[i-1]),edge:i};
  }
  return best;
 }
 function direction(a,b,c,d){
  const x=(b[0]-a[0])*M*Math.cos(a[1]*D),y=(b[1]-a[1])*M;
  const u=(d[0]-c[0])*M*Math.cos(c[1]*D),v=(d[1]-c[1])*M;
  return (x*u+y*v)/(Math.hypot(x,y)*Math.hypot(u,v)||1);
 }
 function potential(line,way,index,minLength=1){
  const a=line[index],b=line[index+1],length=metres(a,b);
  if(length<minLength||length>120)return null;
  const x=snap(a,way),y=snap(b,way);
  const wide=Math.max(x.offset,y.offset)>8;
  // Pavements beside broad urban roads can be >8 m from the OSM
  // centreline. Permit them ONLY as long, strongly parallel corridors.
  if(x.offset>15||y.offset>15)return null;
  const along=y.along-x.along;
  if(Math.abs(along)<length*(wide?.90:.72))return null;
  const r0=way.coords[x.edge-1],r1=way.coords[x.edge];
  const alignment=Math.abs(direction(a,b,r0,r1));
  if(alignment<(wide?.97:.94))return null;
  return {x,y,length,wide,sign:Math.sign(along),offset:(x.offset+y.offset)/2};
 }
 // Sparse imported routes often encode a long road-parallel passage as
 // ONE matched leg. Confirm its *interior* independently against the
 // reference, not merely two endpoints near a distant or curving road.
 function supportedLongLeg(a,b,way){
  const length=metres(a,b);
  if(length<32||length>120)return false;
  const first=snap(a,way),last=snap(b,way);
  const wide=Math.max(first.offset,last.offset)>8;
  if(first.offset>15||last.offset>15)return false;
  if(wide&&length<50)return false;
  const sign=Math.sign(last.along-first.along);
  if(!sign||Math.abs(last.along-first.along)<length*.85)return false;
  let previous=first;
  for(const t of [.25,.5,.75,1]){
   const current=t===1?last:snap(interpolate(a,b,t),way);
   if(current.offset>(wide?15:8)||sign*(current.along-previous.along)<length*.12)return false;
   previous=current;
  }
  return true;
 }
 // Auditable screening of the *actual* journey against the actual
 // reference lines. These are per-source-leg decisions, not independent
 // proof of travel and not a substitute for the run-level selection.
 function evidence(route){
  const ways=network(route?.refs),reasons=new Map(),nearby=new Map();
  let validLegs=0,sourceMetres=0;
  const add=(key,len)=>reasons.set(key,(reasons.get(key)||0)+len);
  for(const line of route?.parts||[])for(let i=1;i<line.length;i++){
   const a=line[i-1],b=line[i],length=valid(a)&&valid(b)?metres(a,b):0;
   if(length<1||length>120)continue;
   validLegs++;sourceMetres+=length;
   if(!ways.length){add('no physical road references',length);continue;}
   let best=null;
   for(let w=0;w<ways.length;w++){
    const way=ways[w],x=snap(a,way),y=snap(b,way);
    if(!x||!y)continue;
    const offset=Math.max(x.offset,y.offset),progress=Math.abs(y.along-x.along)/length,
     r0=way.coords[x.edge-1],r1=way.coords[x.edge],
     heading=Math.abs(direction(a,b,r0,r1));
    // Score viable proximity first, without declaring them accepted.
    const score=offset+Math.max(0,.97-heading)*70+Math.max(0,.90-progress)*20;
    if(!best||score<best.score)best={wayIndex:w,offset,progress,heading,score};
   }
   if(!best){add('no physical road references',length);continue;}
   const key=best.wayIndex;
   const stat=nearby.get(key)||{metres:0,maxOffset:0,heading:0,progress:0};
   stat.metres+=length;stat.maxOffset=Math.max(stat.maxOffset,best.offset);
   stat.heading+=best.heading*length;stat.progress+=best.progress*length;
   nearby.set(key,stat);
   if(best.offset>15)add('physical road farther than 15 m',length);
   else if(best.heading<(best.offset>8?.97:.94))add('direction not parallel',length);
   else if(best.progress<(best.offset>8?.90:.72))add('insufficient forward road overlap',length);
   else if(best.offset<1.6)add('already near physical centreline',length);
   else add('segment passes individual eligibility checks',length);
  }
  const roadBreakdown=[...nearby].map(([index,stat])=>({
   name:ways[index].name||'Unnamed road',
   sampledMetres:Math.round(stat.metres),
   maxOffsetMetres:Number(stat.maxOffset.toFixed(1)),
   meanHeading:Number((stat.heading/stat.metres).toFixed(2)),
   meanProgress:Number((stat.progress/stat.metres).toFixed(2)),
   index
  }));
  return {
   references:ways.length,sourceLegs:validLegs,sourceMetres:Math.round(sourceMetres),
   reasons:[...reasons].map(([reason,metres])=>({reason,metres:Math.round(metres)}))
     .sort((a,b)=>b.metres-a.metres),
   roads:roadBreakdown.sort((a,b)=>b.sampledMetres-a.sampledMetres).slice(0,6)
  };
 }
 function trace(way,from,to){
  if(to.along<from.along)return trace(way,to,from).reverse();
  const interior=way.coords.slice(from.edge,to.edge)
   .filter((_,i)=>way.lengths[from.edge+i]>from.along+.01&&way.lengths[from.edge+i]<to.along-.01);
  return [from.position,...interior,to.position];
 }
 function closeToWay(line,way,tolerance){
  for(let i=1;i<line.length;i++){
   const a=line[i-1],b=line[i],count=Math.ceil(metres(a,b)/4);
   for(let n=0;n<=count;n++)if(snap(interpolate(a,b,count?n/count:0),way).offset>tolerance)return false;
  }
  return true;
 }
 function roadGraph(refs){
  const nodes=new Map(),edges=[];
  const vertex=(key,p)=>{
   if(!nodes.has(key))nodes.set(key,{p,edges:[]});
   return nodes.get(key);
  };
  for(const ref of refs){
   if(!ref.nodes||!road.test(ref.kind))continue;
   for(let i=1;i<ref.coords.length;i++){
    const a=vertex(ref.nodes[i-1],ref.coords[i-1]),b=vertex(ref.nodes[i],ref.coords[i]);
    if(a===b)continue;
    const edge={a,b,name:ref.name||'',structure:structure(ref)};
    a.edges.push(edge);b.edges.push(edge);edges.push(edge);
   }
  }
  return edges;
 }
 function roadAnchors(from,to,edges,atStart){
  if(metres(from,to)<.5)return [];
  const p=atStart?to:from;
  const options=[];
  for(const edge of edges){
   const {a,b}=edge,at=projected(p,a.p,b.p);
   if(at.offset>1.25||Math.abs(direction(from,to,a.p,b.p))<.85)continue;
   const other=projected(atStart?from:to,a.p,b.p);
   // Accept an approaching/departing trace close to the road before its
   // vertex, even when it spans more than one OSM edge.
   if(other.offset>3&&other.t>0&&other.t<1)continue;
   const sign=Math.sign(direction(from,to,a.p,b.p));
   const node=sign===(atStart?1:-1)?b:a;
   options.push({edge,node,p:at.position,sign});
  }
  return options;
 }
 function sampled(line,test){
  for(let i=1;i<line.length;i++){
   const a=line[i-1],b=line[i],n=Math.max(1,Math.ceil(metres(a,b)/4));
   for(let k=0;k<n;k++)if(!test(interpolate(a,b,k/n)))return false;
  }
  return test(line.at(-1));
 }
 function orderedSupport(line,reference,tolerance){
  let previous=0;
  const length=reference.lengths.at(-1);
  return sampled(line,p=>{
   const hit=snap(p,reference);
   if(hit.offset>tolerance||hit.along<previous-2)return false;
   previous=Math.max(previous,hit.along);
   return true;
  })&&length-previous<2;
 }
 function measured(coords){
  const lengths=[0];
  for(let i=1;i<coords.length;i++)lengths.push(lengths.at(-1)+metres(coords[i-1],coords[i]));
  return {coords,lengths};
 }
 function junctionTrace(source,starts,ends,protectedPaths,corridor){
  const guide=measured(source),length=guide.lengths.at(-1);
  const options=[],limit=Math.min(corridor?85:65,length*1.22),tolerance=corridor?14:9;
  let visits=0;
  function accept(pathPoints,edges,travel){
   if(travel<length*.72||travel>limit)return;
   const coords=pathPoints.filter((p,i)=>!i||metres(p,pathPoints[i-1])>.01);
   if(coords.length<2||!orderedSupport(coords,guide,tolerance)||
      !orderedSupport(source,measured(coords),tolerance))return;
   if(protectedPaths.some(({path,grade})=>{
    const used=source.slice(1,-1).filter(p=>snap(p,path).offset<=1.25);
    if(!used.length)return false;
    // A pavement on the same mapped bridge may follow the road centreline.
    // Check the actual clipped spans selected by the graph, at the same grade.
    return !grade||used.some(p=>!edges.some((edge,i)=>edge.structure===grade&&
     projected(p,pathPoints[i],pathPoints[i+1]).offset<=8));
   }))return;
   const labels=edges.map(edge=>edge.name);
   if(!options.some(other=>orderedSupport(coords,measured(other.coords),1.25)&&
      orderedSupport(other.coords,measured(coords),1.25)))options.push({coords,labels});
  }
  const fits=(a,b)=>sampled([a,b],p=>snap(p,guide).offset<=tolerance);
  for(const start of starts){
   for(const end of ends){
    if(start.edge===end.edge&&start.sign===end.sign&&
       direction(start.p,end.p,start.edge.a.p,start.edge.b.p)*start.sign>0)
     accept([start.p,end.p],[start.edge],metres(start.p,end.p));
    const walk=(node,travel,path,edges,seen)=>{
     if(++visits>240||travel>limit||options.length>1)return;
     if(node===end.node&&fits(node.p,end.p)){
      accept(path.concat([end.p]),edges.concat(end.edge),travel+metres(node.p,end.p));
      return;
     }
     for(const edge of node.edges){
      if(edge===start.edge||edge===end.edge||corridor&&!corridor(edge))continue;
      const next=edge.a===node?edge.b:edge.a;
      const nextLength=travel+metres(node.p,next.p);
      if(seen.has(next)||nextLength>limit||!fits(node.p,next.p))continue;
      seen.add(next);
      walk(next,nextLength,path.concat([next.p]),edges.concat(edge),seen);
      seen.delete(next);
     }
    };
    const first=metres(start.p,start.node.p);
    if(first<=limit&&fits(start.p,start.node.p))
     walk(start.node,first,[start.p,start.node.p],[start.edge],new Set([start.node]));
    if(visits>240||options.length>1)return null;
   }
  }
  return options[0]||null;
 }
 function structure(ref){
  if(ref.bridge==='yes')return 'bridge:'+ (ref.layer||'0');
  if(ref.tunnel==='yes')return 'tunnel:'+ (ref.layer||'0');
  return '';
 }
 function protectedWalks(refs){
  return refs.filter(ref=>!road.test(ref.kind)&&ref.coords?.length>=2&&ref.coords.every(valid))
   .flatMap(ref=>{
    const dedicated=ref.footway==='crossing'||/^(steps|corridor|cycleway|bridleway)$/.test(ref.kind)||
     ref.indoor==='yes';
    const grade=structure(ref);
    return dedicated||grade?[{path:measured(ref.coords),grade:dedicated?'':grade}]:[];
   });
 }
 function joinJunctions(line,edges,protectedPaths){
  if(!edges.length||line.length<4)return {line,joins:[]};
  const output=[line[0]],joins=[];
  const anchors=new Map();
  const getAnchors=(i,before)=>{
   const key=2*i+Number(before);
   if(!anchors.has(key))anchors.set(key,roadAnchors(
    before?line[i-1]:line[i],before?line[i]:line[i+1],edges,before));
   return anchors.get(key);
  };
  for(let i=1;i<line.length-1;i++){
   output.push(line[i]);
   const starts=getAnchors(i,true);
   if(!starts.length||getAnchors(i+1,true).length)continue;
   let length=0,offRoad=false;
   for(let j=i+1;j<line.length-1;j++){
    length+=metres(line[j-1],line[j]);
    if(length>65)break;
    const ends=getAnchors(j,false);
    if(!ends.length){offRoad=true;continue;}
    if(length<8||!offRoad)break;
    const span=line.slice(i,j+1);
    const joined=junctionTrace(span,starts,ends,protectedPaths);
    if(!joined)break;
    output.splice(output.length-1,1,...joined.coords);
    joins.push({road:[...new Set(joined.labels.filter(Boolean))].join(' / '),
     metres:Number(length.toFixed(1)),segments:j-i,kind:'junction'});
    i=j;
    break;
   }
  }
  output.push(line.at(-1));
  return {line:output,joins};
 }
 // Recover continuity where a pavement promotion left a short zig-zag
 // through the nearby branch of a divided road. The two mapped branches
 // must rejoin, and the selected branch must support the ordered trace.
 function joinDividedRoads(line,seams,edges,refs,recorded){
  const joins=[];
  for(const {at,name} of seams.toReversed()){
   if(!name||at<1||at+3>=line.length)continue;
   const named=edges.filter(edge=>edge.name===name);
   const starts=roadAnchors(line[at-1],line[at],named,true);
   if(!starts.length||roadAnchors(line[at],line[at+1],named,true).length)continue;
   const corridor=edge=>edge.name===name&&starts.every(start=>start.edge.structure===edge.structure);
   let length=0;
   for(let j=at+1;j<line.length-1;j++){
    length+=metres(line[j-1],line[j]);
    if(length>85)break;
    if(length<20)continue;
    const ends=roadAnchors(line[j],line[j+1],named,false).filter(end=>corridor(end.edge)&&
     metres(end.p,end.node.p)<.25&&end.node.edges.filter(corridor).length>=3);
    if(!ends.length)continue;
    const span=line.slice(at,j+1),guide=measured(span);
    const structural=refs.filter(ref=>ref.indoor==='yes'||structure(ref));
    const joined=junctionTrace(span,starts,ends,protectedWalks(structural),corridor);
    if(!joined)continue;
    const path=measured(joined.coords),saved=length-path.lengths.at(-1);
    if(saved<8||saved<length*.12)continue;
    // A displaced segment must include a parallel stretch of another
    // mapped branch, rather than an independent pedestrian shortcut.
    if(!span.slice(1,-1).some((point,k)=>snap(point,path).offset>5&&
     onMappedRoad(point,span[k+2],named)))continue;
    const relevant=recorded.filter(point=>snap(point,guide).offset<6);
    const displaced=relevant.filter(point=>snap(point,path).offset-snap(point,guide).offset>4);
    if(displaced.length>1||relevant.some(point=>snap(point,path).offset>10))continue;
    line.splice(at,j-at+1,...joined.coords);
    joins.push({road:name,metres:Number(length.toFixed(1)),segments:j-at,kind:'divided-road'});
    break;
   }
  }
  return joins;
 }
 // Dense mapped pavement vertices can end a valid run prematurely. Extend
 // only a confirmed run, over a few metres of tightly parallel geometry.
 function extendBoundary(line,way,run,sign,protectedLegs,ranges,side){
  let at=side<0?run.start:run.end,extra=0;
  for(let next=at+side;next>=0&&next<line.length;next=at+side){
   const index=Math.min(at,next);
   if(protectedLegs[index])break;
   const leg=potential(line,way,index,.2);
   if(!leg||leg.length>4||leg.wide||leg.sign!==sign||extra+leg.length>8||
      ranges.some(other=>other!==run&&other.start<=index+1&&other.end>=index))break;
   extra+=leg.length;
   at=next;
  }
  return {at,extra};
 }
 // A saved match can step off a road at the frontage, then continue
 // along the mapped vehicle entrance to the same destination. Prefer the
 // continuous road-to-access trace only when the saved tail and final fix
 // support it, and the two references meet at a real junction.
 function terminalAccess(line,run,roadWay,refs,route){
  const approach=line.slice(run.end);
  if(approach.length<5||approach.length>36)return null;
  const source=measured(approach),length=source.lengths.at(-1);
  if(length<16||length>45)return null;
  const raw=(route.recorded||[]).flat().filter(valid);
  if(!raw.length)return null;
  const fix=raw.at(-1),exit=snap(approach[0],roadWay);
  const candidates=[];
  for(const ref of refs){
   if(ref.kind!=='service'||!ref.coords?.every(valid)||ref.coords.length<2)continue;
   for(const coords of [ref.coords,ref.coords.toReversed()]){
    const junction=snap(coords[0],roadWay);
    const continuation=run.sign*(junction.along-exit.along);
    if(junction.offset>.2||continuation<5||continuation>24)continue;
    const access=measured(coords);
    const snaps=approach.map(p=>snap(p,access));
    let seam=approach.length-1;
    while(seam>0&&snaps[seam-1].offset<=.75&&snaps[seam].along>snaps[seam-1].along)seam--;
    if(seam<2||snaps.at(-1).offset>.75||snaps[seam].along<3||snaps[seam].along>16||
       snaps.at(-1).along-snaps[seam].along<10)continue;
    const bypass=measured(approach.slice(0,seam+1)),tail=measured(approach.slice(seam));
    const tailOffset=snap(fix,tail).offset;
    if(bypass.lengths.at(-1)>30||tailOffset>13||
       snap(fix,bypass).offset<tailOffset+5||
       raw.some(p=>snap(p,bypass).offset<7))continue;
    const independent=refs.some(path=>(path.footway==='crossing'||path.indoor==='yes'||structure(path))&&
     !road.test(path.kind)&&path.coords?.length>=2&&path.coords.every(valid)&&
     approach.slice(1,seam).some(p=>snap(p,measured(path.coords)).offset<1.25));
    if(independent)continue;
    const centreline=trace(roadWay,exit,junction);
    const entry=trace(access,{position:coords[0],along:0,edge:1},snaps[seam]);
    // Keep the saved terminal geometry and its endpoint exactly.
    const replacement=centreline.concat(entry.slice(1,-1),approach.slice(seam));
    const mapped=measured(replacement),newLength=mapped.lengths.at(-1);
    if(newLength<length*.75||newLength>length*1.2||
       !orderedSupport(replacement,source,10)||!orderedSupport(approach,mapped,10))continue;
    candidates.push({geometry:replacement,metres:Number(bypass.lengths.at(-1).toFixed(1)),segments:seam,road:roadWay.name,
     kind:'terminal-access'});
   }
  }
  return candidates.length===1?candidates[0]:null;
 }
 // A centreline already present in the source is stronger than a
 // parallel reference on the other side of a divided road.
 function onMappedRoad(a,b,graph){
  if(metres(a,b)<1)return false;
  const lonPad=.65/(M*Math.max(.1,Math.cos(a[1]*D))),latPad=.65/M;
  const west=Math.min(a[0],b[0])-lonPad,east=Math.max(a[0],b[0])+lonPad;
  const south=Math.min(a[1],b[1])-latPad,north=Math.max(a[1],b[1])+latPad;
  const candidates=graph.filter(({a:u,b:v})=>
   Math.max(u.p[0],v.p[0])>=west&&Math.min(u.p[0],v.p[0])<=east&&
   Math.max(u.p[1],v.p[1])>=south&&Math.min(u.p[1],v.p[1])<=north&&
   Math.abs(direction(a,b,u.p,v.p))>=.96);
  return sampled([a,b],p=>candidates.some(({a:u,b:v})=>projected(p,u.p,v.p).offset<=.65));
 }
 function align(route){
  const refs=route?.refs||[],roads=network(refs),parts=[],promoted=[];
  const graph=roadGraph(refs),protectedPaths=protectedWalks(refs);
  for(const original of route?.parts||[]){
   if(!Array.isArray(original)||original.length<2||!roads.length){
    parts.push(original);continue;
   }
   const ranges=[];
   const line=original;
   const protectedLegs=line.slice(1).map((b,i)=>{
    const a=line[i];
    if(!valid(a)||!valid(b)||onMappedRoad(a,b,graph))return true;
    return protectedPaths.some(({path})=>{
     const middle=snap(interpolate(a,b,.5),path);
     return middle.offset<=1.25&&Math.abs(direction(a,b,path.coords[middle.edge-1],path.coords[middle.edge]))>.85;
    });
   });
   for(let w=0;w<roads.length;w++){
    const way=roads[w];
    const midLat=line.find(valid)?.[1]??0;
    const lonPad=.00014/Math.max(.1,Math.cos(midLat*D));
    if(line.every(p=>!valid(p)||p[0]<way.minX-lonPad||p[0]>way.maxX+lonPad||
     p[1]<way.minY-.00014||p[1]>way.maxY+.00014))continue;
    let start=-1,last=null,length=0,offset=0,legs=0,wideRun=false;
    const flush=()=>{
     const independentlySupported=legs===1&&start>=0&&
      supportedLongLeg(line[start],line[start+1],way);
     if(start>=0&&(legs>=2||independentlySupported)&&length>=(wideRun?50:32)&&length/legs>=2){
      const end=start+legs,averageOffset=offset/legs;
      // Do not change geometry already on the road centreline.
      if(averageOffset>=1.6)ranges.push({start,end,length,offset:averageOffset,w});
     }
     start=-1;last=null;length=0;offset=0;legs=0;wideRun=false;
    };
    for(let i=0;i<line.length-1;i++){
     const next=protectedLegs[i]?null:potential(line,way,i);
     if(!next||last&&next.sign!==last.sign){
      flush();if(!next)continue;
     }
     if(start<0)start=i;
     last=next;length+=next.length;offset+=next.offset;legs++;wideRun||=next.wide;
    }
    flush();
   }
   // Do not arbitrate closely competing physical roads by arbitrary order.
   ranges.sort((a,b)=>b.length-a.length||a.offset-b.offset);
   const chosen=[];
   for(const run of ranges){
    if(chosen.some(other=>run.start<=other.end&&other.start<=run.end))continue;
    const way=roads[run.w];
    const snapped=line.slice(run.start,run.end+1).map(p=>snap(p,way));
    const sign=Math.sign(snapped.at(-1).along-snapped[0].along);
    if(!sign||!snapped.every((x,j)=>!j||sign*(x.along-snapped[j-1].along)>.12))continue;
    if(!closeToWay(line.slice(run.start,run.end+1),way,snapped.some(p=>p.offset>8)?15:8))continue;
    const rivals=ranges.filter(other=>other!==run&&other.w!==run.w&&
     Math.max(other.start,run.start)<Math.min(other.end,run.end)&&Math.abs(other.offset-run.offset)<1.5);
    if(rivals.some(other=>{
     const rival=roads[other.w];
     for(let i=Math.max(run.start,other.start);i<=Math.min(run.end,other.end);i++)
      if(metres(snap(line[i],way).position,snap(line[i],rival).position)>1.25)return true;
     return false;
    }))continue;
    const start=extendBoundary(line,way,run,sign,protectedLegs,ranges,-1);
    const end=extendBoundary(line,way,run,sign,protectedLegs,ranges,1);
    const geometry=trace(way,start.extra?snap(line[start.at],way):snapped[0],
     end.extra?snap(line[end.at],way):snapped.at(-1));
    // Preserve the exact pavement vertex at the end of the transfer. A
    // diagonal to the next access vertex would cut the mapped entrance.
    if(start.extra&&start.at>0)geometry.unshift(line[start.at]);
    if(end.extra&&end.at<line.length-1)geometry.push(line[end.at]);
    chosen.push({...run,start:start.at,end:end.at,sign,exitAnchor:!!end.extra&&end.at<line.length-1,geometry});
    promoted.push({metres:Number((run.length+start.extra+end.extra).toFixed(1)),
     offset:Number(run.offset.toFixed(1)),segments:end.at-start.at,
     road:way.name});
   }
   const output=[],seams=[];
   chosen.sort((a,b)=>a.start-b.start);
   const last=chosen.at(-1);
   const access=last&&route.onFoot&&!route.corrected&&original===route.parts.at(-1)?
    terminalAccess(line,last,roads[last.w],refs,route):null;
   let at=0;
   for(const run of chosen){
    output.push(...line.slice(at,run.start),...run.geometry);
    if(run!==last||!access)seams.push({at:output.length-(run.exitAnchor?2:1),name:roads[run.w].name});
    at=run.end+1;
   }
   if(access){
    // Replace the road exit anchor and all of the frontage, retaining the
    // saved rejoin and the rest of the matched service-road tail.
    output.splice(output.length-(last.exitAnchor?2:1),last.exitAnchor?2:1,...access.geometry);
    const {geometry,...receipt}=access;
    promoted.push(receipt);
   }else output.push(...line.slice(at));
   if(route.onFoot&&!route.corrected&&seams.length){
    const raw=(route.recorded||[]).flat().filter(valid);
    promoted.push(...joinDividedRoads(output,seams,graph,refs,raw));
   }
   const joined=joinJunctions(output,graph,protectedPaths);
   parts.push(joined.line);
   promoted.push(...joined.joins);
  }
  return {parts,promoted};
 }
 return {align,evidence};
})();
if(typeof module!=='undefined')module.exports=RoadsideCentreline;
