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
 function roadGraph(refs,pedestrianStreets=false){
  const nodes=new Map(),edges=[];
  const vertex=(key,p)=>{
   if(!nodes.has(key))nodes.set(key,{p,edges:[]});
   return nodes.get(key);
  };
  for(const ref of refs){
   if(!ref.nodes||!(road.test(ref.kind)||pedestrianStreets&&
      (ref.kind==='pedestrian'&&ref.name||ref.sharedCrossing)))continue;
   for(let i=1;i<ref.coords.length;i++){
    const a=vertex(ref.nodes[i-1],ref.coords[i-1]),b=vertex(ref.nodes[i],ref.coords[i]);
    if(a===b)continue;
    const edge={a,b,name:ref.name||'',kind:ref.kind,structure:structure(ref)};
    a.edges.push(edge);b.edges.push(edge);edges.push(edge);
   }
  }
  return edges;
 }
 function roadAnchors(from,to,edges,atStart,tolerance=1.25){
  if(metres(from,to)<.5)return [];
  const p=atStart?to:from;
  const options=[];
  for(const edge of edges){
   const {a,b}=edge,at=projected(p,a.p,b.p);
   if(at.offset>tolerance||Math.abs(direction(from,to,a.p,b.p))<.85)continue;
   const other=projected(atStart?from:to,a.p,b.p);
   // Accept an approaching/departing trace close to the road before its
   // vertex, even when it spans more than one OSM edge.
   if(other.offset>Math.max(3,tolerance)&&other.t>0&&other.t<1)continue;
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
 // Match a sustained surface corridor as one ordered road trace. A side
 // street crossing can interrupt the pavement without interrupting the road.
 function continuousCorridor(line,graph,protectedPaths,recorded){
  const major=edge=>edge.name&&/^(trunk|primary|secondary|tertiary)$/.test(edge.kind)&&!edge.structure;
  const edges=graph.filter(major);
  if(!edges.length||line.length<6)return null;
  const guide=measured(line);
  if(guide.lengths.at(-1)<180||guide.lengths.at(-1)>10000)return null;
  const samples=[{p:line[0],at:0}];
  for(let i=1;i<line.length;i++){
   const a=line[i-1],b=line[i],length=guide.lengths[i]-guide.lengths[i-1];
   if(length>500)return null;
   const count=Math.max(1,Math.ceil(length/8));
   for(let j=1;j<=count;j++)samples.push({p:interpolate(a,b,j/count),at:j===count?i:null});
  }
  const candidates=(point,before,after)=>{
   const lonPad=.00017/Math.max(.1,Math.cos(point[1]*D));
   const nearby=[];
   for(const edge of edges){
    const {a,b}=edge;
    if(point[0]<Math.min(a.p[0],b.p[0])-lonPad||point[0]>Math.max(a.p[0],b.p[0])+lonPad||
       point[1]<Math.min(a.p[1],b.p[1])-.00018||point[1]>Math.max(a.p[1],b.p[1])+.00018)continue;
    const hit=projected(point,a.p,b.p),heading=direction(before,after,a.p,b.p);
    if(hit.offset>18||Math.abs(heading)<.55)continue;
    const forward=heading>0;
    nearby.push({edge,p:hit.position,start:forward?a:b,end:forward?b:a,
     cost:hit.offset+4*(1-Math.abs(heading))});
   }
   return nearby;
  };
  const advance=(from,to,limit,sourceA,sourceB)=>{
   if(from.edge===to.edge){
    const length=metres(from.p,to.p);
    if(from.end!==to.end||length>limit||direction(from.p,to.p,from.start.p,from.end.p)<0)return null;
    return {length,via:[]};
   }
   let best=null;
   const exit=metres(to.start.p,to.p);
   const visited=new Map();
   const visit=(node,length,via)=>{
    if(length+exit>limit||best&&length+exit>=best.length||length>=(visited.get(node)??Infinity))return;
    visited.set(node,length);
    if(node===to.start){
     best={length:length+exit,via};
     return;
    }
    for(const edge of node.edges){
     if(edge===from.edge||edge===to.edge||!major(edge))continue;
     const next=edge.a===node?edge.b:edge.a;
     if(projected(next.p,sourceA,sourceB).offset>19)continue;
     visit(next,length+metres(node.p,next.p),via.concat([next.p]));
    }
   };
   visit(from.end,metres(from.p,from.end.p),[from.end.p]);
   return best;
  };
  let states=[],best=null;
  function consider(state){
   const start=samples[state.first].at,end=samples[state.last].at;
   if(start===null||end===null)return;
   const length=guide.lengths[end]-guide.lengths[start];
   if(length<180||best&&length<=best.length)return;
   best={state,length};
  }
  for(let i=0;i<samples.length;i++){
   const {p,at}=samples[i];
   const before=samples[Math.max(0,i-2)].p,after=samples[Math.min(samples.length-1,i+2)].p;
   const previous=samples[Math.max(0,i-1)].p,step=metres(previous,p);
   const next=candidates(p,before,after).map(candidate=>{
    let choice=null;
    for(const state of states){
     const transition=advance(state,candidate,step*1.65+8,previous,p);
     if(!transition)continue;
     const cost=state.cost+candidate.cost+Math.abs(step-transition.length)*.6;
     if(!choice||state.first<choice.first||state.first===choice.first&&cost<choice.cost)
      choice={...candidate,first:state.first,last:i,cost,previous:state,via:transition.via};
    }
   return choice||{...candidate,first:i,last:i,cost:candidate.cost,previous:null,via:[]};
   });
   if(at!==null){
    const longest=next.filter(state=>samples[state.first].at!==null)
     .sort((a,b)=>a.first-b.first||a.cost-b.cost);
    if(longest.length)consider(longest[0]);
   }
   // A new trace starts at an existing source vertex, so splicing cannot
   // remove part of a genuine path outside the matched span.
   states=next.filter(state=>samples[state.first].at!==null);
  }
  if(!best)return null;
  let statesInOrder=[];
  for(let state=best.state;state;state=state.previous)statesInOrder.push(state);
  statesInOrder.reverse();
  // Adjacent road stubs can catch projections close to a terminal turn.
  // Keep those terminals in the source unless travel along the road is sustained.
  const first=statesInOrder[0],last=statesInOrder.at(-1);
  const firstChange=statesInOrder.findIndex(state=>state.edge.name!==first.edge.name);
  const lastChange=statesInOrder.findLastIndex(state=>state.edge.name!==last.edge.name);
  const startOffset=firstChange>0&&metres(first.p,statesInOrder[firstChange-1].p)<22?firstChange:0;
  const endOffset=lastChange>=0&&metres(last.p,statesInOrder[lastChange+1].p)<22?
   lastChange+1:statesInOrder.length;
  statesInOrder=statesInOrder.slice(startOffset,endOffset);
  const firstVertex=statesInOrder.findIndex(state=>samples[state.last].at!==null);
  const lastVertex=statesInOrder.findLastIndex(state=>samples[state.last].at!==null);
  if(firstVertex<0||lastVertex<=firstVertex)return null;
  statesInOrder=statesInOrder.slice(firstVertex,lastVertex+1);
  const start=samples[statesInOrder[0].last].at,end=samples[statesInOrder.at(-1).last].at;
  const length=guide.lengths[end]-guide.lengths[start];
  if(length<180)return null;
  const coords=[statesInOrder[0].p],names=new Set([statesInOrder[0].edge.name]);
  for(const state of statesInOrder.slice(1)){
   if(state.via.length)coords.push(...state.via);
   names.add(state.edge.name);
  }
  coords.push(statesInOrder.at(-1).p);
  const geometry=coords.filter((point,i)=>!i||metres(point,coords[i-1])>.01);
  if(geometry.length<2)return null;
  const source=line.slice(start,end+1),path=measured(geometry),span=measured(source);
  if(path.lengths.at(-1)<length*.8||path.lengths.at(-1)>length*1.1||
     !orderedSupport(source,path,18)||!closeToWay(geometry,span,18))return null;
  if(recorded.some(fix=>snap(fix,span).offset<=3&&snap(fix,path).offset>13))return null;
  if(usesIndependentPath(source,path,protectedPaths))return null;
  return {start,end,length,geometry,names};
 }
 function usesIndependentPath(source,path,protectedPaths){
  let dedicatedLength=0;
  for(let i=1;i<source.length;i++){
   const a=source[i-1],b=source[i],mid=interpolate(a,b,.5);
   const used=protectedPaths.filter(({path:foot})=>{
    const hit=snap(mid,foot);
    return hit.offset<=1.25&&Math.abs(direction(a,b,foot.coords[hit.edge-1],foot.coords[hit.edge]))>=.85;
   });
   if(used.some(walk=>walk.structural))return true;
   if(used.some(walk=>!walk.crossing))dedicatedLength+=metres(a,b);
   if(dedicatedLength>15)return true;
   if(used.some(walk=>walk.crossing)){
    // Keep a crossing of the selected road. A crossing of a side road
    // on the same pavement is part of the through corridor.
    const road=snap(mid,path),u=path.coords[road.edge-1],v=path.coords[road.edge];
    if(Math.abs(direction(a,b,u,v))<.6&&!sampled([a,b],p=>projected(p,u,v).offset>2))return true;
   }
  }
  return false;
 }
 function junctionTrace(source,starts,ends,protectedPaths,corridor,bounds={}){
  const guide=measured(source),length=guide.lengths.at(-1);
  const options=[],limit=Math.min(bounds.max??(corridor?85:65),length*1.22);
  const tolerance=bounds.tolerance??(corridor?14:9);
  let visits=0;
  function accept(pathPoints,edges,travel){
   if(travel<length*(bounds.minRatio??.72)||travel>limit)return;
   const coords=pathPoints.filter((p,i)=>!i||metres(p,pathPoints[i-1])>.01);
   if(coords.length<2||!orderedSupport(coords,guide,tolerance)||
     !orderedSupport(source,measured(coords),tolerance))return;
   if(protectedPaths.some(({path,grade})=>{
    const used=[];
    for(let i=1;i<source.length;i++){
     const a=source[i-1],b=source[i],middle=interpolate(a,b,.5),hit=snap(middle,path);
     if(metres(a,b)>1&&hit.offset<=1.25&&Math.abs(direction(a,b,path.coords[hit.edge-1],path.coords[hit.edge]))>.85)
      used.push(middle);
    }
    if(!used.length)return false;
    // A pavement on the same mapped bridge may follow the road centreline.
    // Check the actual clipped spans selected by the graph, at the same grade.
    return !grade||used.some(p=>!edges.some((edge,i)=>edge.structure===grade&&
     projected(p,pathPoints[i],pathPoints[i+1]).offset<=8));
   }))return;
   const labels=edges.map(edge=>edge.name);
   if(!options.some(other=>orderedSupport(coords,measured(other.coords),1.25)&&
      orderedSupport(other.coords,measured(coords),1.25)))options.push({coords,labels,
       pedestrian:edges.some(edge=>edge.kind==='pedestrian')});
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
    const structural=!!grade||ref.indoor==='yes'||ref.kind==='steps';
    return dedicated||grade?[{path:measured(ref.coords),grade:dedicated?'':grade,structural,
     crossing:!structural&&ref.footway==='crossing'}]:[];
   });
 }
 function joinJunctions(line,edges,protectedPaths,raw,allowPavementJoin,wideTurns=false){
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
    if(length>(wideTurns?110:65))break;
    const ends=getAnchors(j,false);
    if(!ends.length){offRoad=true;continue;}
    if(length<8||!offRoad)break;
    const span=line.slice(i,j+1);
    let joined=wideTurns?null:junctionTrace(span,starts,ends,protectedPaths);
    if(!joined&&allowPavementJoin){
     const entry=starts[0].edge,exit=ends[0].edge;
     const same=edge=>[entry.name,exit.name].includes(edge.name)&&edge.structure===entry.structure;
     const corner=Math.abs(direction(line[i-1],line[i],line[j],line[j+1]))<.55;
     // An unobserved pavement offset can follow the connected junction
     // when the road continues or makes the same distinct turn.
     if(wideTurns){
      if(!entry.name||!exit.name||entry.name===exit.name||!corner||length<45||
         entry.structure||exit.structure)break;
      const namedSurface=edge=>edge.name&&!edge.structure;
      const candidate=junctionTrace(span,starts,ends,protectedPaths.filter(w=>w.structural),namedSurface,
       {max:110,tolerance:16});
      if(!candidate)break;
      const path=measured(candidate.coords),guide=measured(span);
      if(raw.some(p=>snap(p,guide).offset<=4&&snap(p,path).offset>13))break;
      joined=candidate;
     }else{
      if(!entry.name||!exit.name||entry.name!==exit.name&&!corner||length>45||
         !starts.every(anchor=>same(anchor.edge))||!ends.every(anchor=>same(anchor.edge)))break;
      const candidate=junctionTrace(span,starts,ends,protectedPaths.filter(w=>w.structural),same);
      if(!candidate)break;
      const path=measured(candidate.coords),guide=measured(span);
      if(!closeToWay(span,path,8)||raw.some(p=>snap(p,guide).offset<=3&&snap(p,path).offset>6))break;
      joined=candidate;
     }
    }
    if(!joined)break;
    output.splice(output.length-1,1,...joined.coords);
    joins.push({road:[...new Set(joined.labels.filter(Boolean))].join(' / '),
     metres:Number(length.toFixed(1)),segments:j-i,kind:wideTurns?'offset-road-turn':'junction'});
    i=j;
    break;
   }
  }
  output.push(line.at(-1));
  return {line:output,joins};
 }
 // A pavement can leave a centreline at one side street and rejoin it at
 // the next. Both approaches must already establish the same through road.
 function closeRoadInterruptions(line,refs,recorded){
  const edges=roadGraph(refs).filter(edge=>edge.name&&!edge.structure);
  const protectedPaths=protectedWalks(refs);
  const joins=[];
  for(let i=1;i<line.length-2;i++){
   const starts=roadAnchors(line[i-1],line[i],edges,true);
   if(!starts.length)continue;
   const name=starts[0].edge.name;
   if(starts.some(start=>start.edge.name!==name))continue;
   const same=edge=>edge.name===name&&!edge.structure;
   if(roadAnchors(line[i],line[i+1],edges,true).some(anchor=>same(anchor.edge)))continue;
   let length=0;
   for(let j=i+1;j<line.length-1;j++){
    length+=metres(line[j-1],line[j]);
    if(length>145)break;
    const ends=roadAnchors(line[j],line[j+1],edges,false).filter(anchor=>same(anchor.edge));
    if(!ends.length)continue;
    const source=line.slice(i,j+1),guide=measured(source);
    const replacement=junctionTrace(source,starts,ends,[],same,{max:145,tolerance:10.5});
    if(!replacement)continue;
    const path=measured(replacement.coords);
    if(path.lengths.at(-1)<5||!source.some(p=>snap(p,path).offset>1.5)||
       usesIndependentPath(source,path,protectedPaths)||
       recorded.some(p=>snap(p,guide).offset<=4&&snap(p,path).offset>8))continue;
    line.splice(i,j-i+1,...replacement.coords);
    joins.push({road:name,metres:Number(length.toFixed(1)),segments:j-i,kind:'road-interruption'});
    i+=replacement.coords.length-1;
    break;
   }
  }
  return joins;
 }
 // A kerbside loop can leave a road at a junction, use a nearby
 // crossing, and return to the through road a few metres away.
 function closeKerbsideLoops(line,refs,recorded){
  const edges=roadGraph(refs).filter(edge=>edge.name&&!edge.structure);
  const protectedPaths=protectedWalks(refs);
  const crossings=refs.filter(ref=>ref.kind==='footway'&&ref.nodes&&ref.coords.every(valid)&&!structure(ref)&&ref.indoor!=='yes')
   .flatMap(ref=>ref.coords.slice(1).map((b,i)=>[ref.coords[i],b]))
   .filter(([a,b])=>metres(a,b)<=12);
  const approachAnchors=(from,to,entering)=>{
   const anchor=entering?to:from,beyond=entering?from:to;
   const approach=interpolate(anchor,beyond,Math.min(1,2/Math.max(.1,metres(anchor,beyond))));
   return roadAnchors(from,to,edges,entering,.4).filter(({edge})=>edges.some(segment=>
    segment.name===edge.name&&projected(approach,segment.a.p,segment.b.p).offset<.75));
  };
  const joins=[];
  for(let i=1;i<line.length-3;i++){
   const starts=approachAnchors(line[i-1],line[i],true);
   if(!starts.length)continue;
   let length=0;
   for(let j=i+1;j<line.length-1;j++){
    length+=metres(line[j-1],line[j]);
    if(length>33)break;
    if(j<i+2||metres(line[i],line[j])>9||
       direction(line[i-1],line[i],line[j],line[j+1])<.88)continue;
    const ends=approachAnchors(line[j],line[j+1],false);
    if(!ends.length)continue;
    const span=line.slice(i,j+1),guide=measured(span);
    const candidates=[];
    for(const start of starts)for(const end of ends){
     if(start.edge.kind!==end.edge.kind)continue;
     const same=edge=>[start.edge.name,end.edge.name].includes(edge.name)&&
      edge.kind===start.edge.kind&&!edge.structure;
     const replacement=junctionTrace(span,[start],[end],[],same,
      {max:10,tolerance:14,minRatio:0});
     if(!replacement)continue;
     const path=measured(replacement.coords),spanLength=path.lengths.at(-1);
     if(spanLength<2||length<spanLength+6||
        direction(line[i-1],line[i],path.coords[0],path.coords.at(-1))<.85||
        usesIndependentPath(span,path,protectedPaths))continue;
     const relevantCrossings=crossings.filter(([a,b])=>metres(a,b)>=3&&
      span.some((p,k)=>k>0&&metres(p,span[k-1])>=2&&
       Math.abs(direction(span[k-1],p,a,b))>.9&&projected(interpolate(span[k-1],p,.5),a,b).offset<.75));
     if(!relevantCrossings.length)continue;
     const sideRoad=edges.filter(edge=>!same(edge)&&(edge.a===start.node||edge.b===start.node));
     const crossesSideRoad=sideRoad.some(edge=>relevantCrossings.some(([a,b])=>
         Math.abs(direction(a,b,edge.a.p,edge.b.p))<.55&&
         [a,b].some(p=>projected(p,edge.a.p,edge.b.p).offset<.25)));
     if(start.edge.name!==end.edge.name&&!crossesSideRoad)continue;
     if(recorded.some(fix=>snap(fix,guide).offset<=5&&snap(fix,path).offset>4&&
       !relevantCrossings.some(([a,b])=>projected(fix,a,b).offset<=4)))continue;
     if(!candidates.some(other=>orderedSupport(path.coords,other.path,.25)&&
       orderedSupport(other.path.coords,path,.25)))candidates.push({path,replacement});
    }
    if(candidates.length!==1)continue;
    const {replacement}=candidates[0];
    line.splice(i,j-i+1,...replacement.coords);
    joins.push({road:[...new Set(replacement.labels)].join(' / '),
     metres:Number(length.toFixed(1)),segments:j-i,kind:'kerbside-loop'});
    i+=replacement.coords.length-1;
    break;
   }
  }
  return joins;
 }
 // Resolve a short walk along the edge of a street between two road
 // intervals already established from the same journey.
 function roadsideTransition(refs,recorded){
  const graph=roadGraph(refs,true);
  const protectedPaths=protectedWalks(refs).filter(path=>!path.crossing);
  const sidewalks=refs.filter(ref=>ref.kind==='footway'&&
   ['sidewalk','crossing'].includes(ref.footway)&&!structure(ref)&&ref.coords?.length>=2&&ref.coords.every(valid))
   .map(ref=>measured(ref.coords));
  return (name,source,before,after)=>{
   const same=edge=>edge.name===name&&!edge.structure;
   const edges=graph.filter(same);
   const starts=roadAnchors(...before,edges,true,.4);
   const ends=roadAnchors(...after,edges,false,.4);
   if(!starts.length||!ends.length)return null;
   const legs=source.slice(1).map((b,i)=>[source[i],b]);
   const pavementMetres=legs.reduce((total,[a,b])=>total+(sidewalks.some(path=>{
    const hit=snap(interpolate(a,b,.5),path);
    return hit.offset<=1.25&&Math.abs(direction(a,b,path.coords[hit.edge-1],path.coords[hit.edge]))>.85;
   })?metres(a,b):0),0);
   if(pavementMetres<3)return null;
   const guide=measured(source);
   const from=before[1],to=after[0];
   const span=source.slice();
   span[0]=from;span[span.length-1]=to;
   const replacement=junctionTrace(span,starts,ends,protectedPaths,same,{max:38,tolerance:13,minRatio:.4});
   if(!replacement)return null;
   const path=measured(replacement.coords);
   const length=path.lengths.at(-1);
   if(length<3||!closeToWay(source,path,13)||usesIndependentPath(source,path,protectedPaths)||
      recorded.some(p=>snap(p,guide).offset<=4&&snap(p,path).offset>13))return null;
   // Keep an actual crossing of the carriageway. These transitions
   // represent walking around a kerb on just one side of the street.
   const sides=new Set();
   for(const p of source){
    const hit=snap(p,path);
    if(hit.offset<=2)continue;
    const [a,b]=path.coords.slice(hit.edge-1,hit.edge+1);
    sides.add(Math.sign((b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0])));
   }
   if(sides.size>1)return null;
   return replacement.coords;
  };
 }
 function closeOffsetRoadTurns(line,refs,recorded){
  const repaired=joinJunctions(line,roadGraph(refs),protectedWalks(refs),recorded,true,true);
  if(repaired.joins.length)line.splice(0,line.length,...repaired.line);
  return repaired.joins;
 }
 // A terminal pavement can be reached by a short mapped crossing from
 // the continuing road. Preserve the destination and its last approach.
 function closeTerminalPavement(line,refs,recorded){
  const paths=refs.filter(ref=>ref.nodes&&ref.kind==='footway'&&!structure(ref)&&ref.indoor!=='yes');
  const candidates=[];
  for(const pavement of paths.filter(ref=>ref.footway==='sidewalk'&&ref.name)){
   const foot=measured(pavement.coords);
   if(snap(line.at(-1),foot).offset>1.25)continue;
   for(const crossing of paths.filter(ref=>ref.footway==='crossing')){
    for(const nodes of [crossing.nodes,crossing.nodes.toReversed()]){
     const coords=nodes===crossing.nodes?crossing.coords:crossing.coords.toReversed();
     if(measured(coords).lengths.at(-1)>14)continue;
     const pavementNode=nodes.at(-1);
     const link=paths.find(ref=>ref.footway==='sidewalk'&&
      ref.nodes.includes(pavementNode)&&ref.nodes.length===2&&metres(...ref.coords)<4);
     const tailNode=pavement.nodes.includes(pavementNode)?pavementNode:
      link?.nodes.find(node=>node!==pavementNode&&pavement.nodes.includes(node));
     if(tailNode===undefined)continue;
     const tailPoint=pavement.coords[pavement.nodes.indexOf(tailNode)];
     const after=line.findLastIndex(p=>metres(p,tailPoint)<.45);
     if(after<3||after>=line.length-2||measured(line.slice(after)).lengths.at(-1)>50)continue;
     const crossingRef={kind:'unclassified',name:pavement.name,
      nodes:tailNode===pavementNode?nodes:nodes.concat(tailNode),
      coords:tailNode===pavementNode?coords:coords.concat([tailPoint])};
     const edges=roadGraph(refs.filter(ref=>road.test(ref.kind)&&ref.name===pavement.name&&!structure(ref))
      .concat(crossingRef));
     const crossingEdges=edges.slice(1-crossingRef.nodes.length);
     const roadEdges=edges.slice(0,1-crossingRef.nodes.length);
     const last=crossingEdges.at(-1);
     const ends=[{edge:last,node:last.a,p:last.b.p,sign:1}];
     if(!ends.length)continue;
     let length=0;
     for(let i=after-1;i>=1;i--){
      length+=metres(line[i],line[i+1]);
      if(length>65)break;
      if(length<25)continue;
      const starts=roadAnchors(line[i-1],line[i],roadEdges,true);
      if(!starts.length)continue;
      const source=line.slice(i,after+1),guide=measured(source);
      const joined=junctionTrace(source,starts,ends,[],edge=>edge.name===pavement.name&&!edge.structure,
       {max:65,tolerance:14});
      if(!joined)continue;
      const replacement=joined.coords;
      const path=measured(replacement);
      if(!orderedSupport(source,path,14)||!orderedSupport(replacement,guide,14)||
         recorded.some(p=>snap(p,guide).offset<=4&&snap(p,path).offset>7))continue;
      candidates.push({i,after,replacement,road:pavement.name,length});
      break;
     }
    }
   }
  }
  if(candidates.length!==1)return [];
  const {i,after,replacement,road:name,length}=candidates[0];
  line.splice(i,after-i+1,...replacement);
  return [{road:name,metres:Number(length.toFixed(1)),segments:after-i,kind:'terminal-pavement-crossing'}];
 }
 function closePedestrianStreetCorridors(line,refs,recorded){
  if(!refs.some(ref=>ref.kind==='pedestrian'&&ref.name))return [];
  const graph=roadGraph(refs,true).filter(edge=>!edge.structure);
  const roads=graph.filter(edge=>road.test(edge.kind)&&edge.name);
  const names=new Set(graph.filter(edge=>edge.kind==='pedestrian').map(edge=>edge.name));
  const protectedPaths=protectedWalks(refs).filter(path=>path.structural);
  const joins=[];
  for(let i=1;i<line.length-2;i++){
   const starts=roadAnchors(line[i-1],line[i],roads,true).filter(a=>names.has(a.edge.name));
   const name=starts[0]?.edge.name;
   if(!name||starts.some(a=>a.edge.name!==name)||
      roadAnchors(line[i],line[i+1],roads,true).some(a=>a.edge.name===name))continue;
   const allowed=edge=>!edge.structure&&(edge.name===name||edge.kind==='cycleway');
   let length=0;
   for(let j=i+1;j<line.length-1;j++){
    length+=metres(line[j-1],line[j]);
    if(length>330)break;
    const ends=roadAnchors(line[j],line[j+1],roads,false).filter(a=>a.edge.name===name);
    if(!ends.length)continue;
    if(length<50)break;
    const source=line.slice(i,j+1),guide=measured(source);
    const joined=junctionTrace(source,starts,ends,protectedPaths,allowed,{max:330,tolerance:21});
    if(!joined?.pedestrian)break;
    const path=measured(joined.coords);
    if(recorded.some(p=>snap(p,guide).offset<=4&&snap(p,path).offset>13))break;
    line.splice(i,j-i+1,...joined.coords);
    joins.push({road:name,metres:Number(length.toFixed(1)),segments:j-i,kind:'pedestrian-street-corridor'});
    i+=joined.coords.length-1;
    break;
   }
  }
  return joins;
 }
 // Preserve a corroborated out-and-back, but lay both directions on
 // the same connected road turn when the saved match follows a pavement.
 function alignTurnarounds(line,edges,protectedPaths,raw){
  if(!raw.length||!edges.length)return [];
  const joins=[];
  for(let tip=3;tip<line.length-3;tip++){
   if(metres(line[tip-1],line[tip+1])>.3||metres(line[tip-1],line[tip])<3||
      !raw.some(fix=>metres(line[tip],fix)<=6))continue;
   const bearing=interpolate(line[tip-1],line[tip],2);
   const ends=roadAnchors(line[tip],bearing,edges,false,4.5);
   if(!ends.length)continue;
   let length=0;
   for(let k=1;k<=12&&tip-k>0&&tip+k<line.length-1;k++){
    const i=tip-k,j=tip+k;
    if(metres(line[i],line[j])>.5)break;
    length+=metres(line[i],line[i+1]);
    if(length>45)break;
    if(length<14)continue;
    const starts=roadAnchors(line[i-1],line[i],edges,true);
    if(!starts.length)continue;
    const span=line.slice(i,tip+1),entry=starts[0].edge,exit=ends[0].edge;
    const allowed=edge=>[entry.name,exit.name].includes(edge.name)&&edge.structure===entry.structure;
    if(!entry.name||!exit.name||entry.name===exit.name||
       !starts.every(anchor=>allowed(anchor.edge))||!ends.every(anchor=>allowed(anchor.edge)))continue;
    const joined=junctionTrace(span,starts,ends,protectedPaths.filter(walk=>walk.structural),allowed);
    if(!joined||joined.coords.length<3||!closeToWay(joined.coords,measured(span),11)||
       !closeToWay(span,measured(joined.coords),11))continue;
    const end=joined.coords.at(-1),source=measured(span),path=measured(joined.coords);
    if(!raw.some(fix=>metres(line[tip],fix)<=6&&metres(end,fix)<=7)||
       raw.some(fix=>snap(fix,source).offset<=3&&snap(fix,path).offset>9))continue;
    const geometry=joined.coords.concat(joined.coords.slice(0,-1).reverse());
    line.splice(i,j-i+1,...geometry);
    joins.push({road:[...new Set(joined.labels.filter(Boolean))].join(' / '),
     metres:Number((2*length).toFixed(1)),segments:2*k,kind:'road-turnaround'});
    tip=i+geometry.length-1;
    break;
   }
  }
  return joins;
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
 // Keep a station forecourt as a short spur from the road already
 // established by this walk. Use only the connected public road graph.
 function stationApproach(line,edges,protectedPaths,raw){
  if(line.length<6)return null;
  let tail=0;
  for(let turn=line.length-2;turn>=3;turn--){
   const pivot=line[turn],previous=line[turn-1],entrance=line.at(-1);
   tail+=metres(pivot,line[turn+1]);
   if(tail>22)break;
   if(tail<4||metres(previous,pivot)<4||Math.abs(direction(previous,pivot,pivot,entrance))>.35)continue;
   const forward=interpolate(previous,pivot,2);
   const nearby=roadAnchors(pivot,forward,edges,false,13).filter(a=>a.edge.name);
   if(!nearby.length)continue;
   const nearest=Math.min(...nearby.map(a=>metres(a.p,pivot)));
   if(nearest<2||nearest+tail>28)continue;
   const ends=nearby.filter(a=>metres(a.p,pivot)<=nearest+.15).sort((a,b)=>
    direction(pivot,a.p,pivot,entrance)-direction(pivot,b.p,pivot,entrance));
   let walked=0;
   for(let i=turn-1;i>=1;i--){
    walked+=metres(line[i],line[i+1]);
    if(walked>125)break;
    if(walked<22)continue;
    const starts=roadAnchors(line[i-1],line[i],edges,true);
    if(!starts.length||starts.some(a=>!a.edge.name))continue;
    const span=line.slice(i,turn+1),guide=measured(span);
    const grade=starts[0].edge.structure,names=new Set([...starts,...ends].map(a=>a.edge.name));
    const allowed=edge=>names.has(edge.name)&&edge.structure===grade;
    if(!starts.every(anchor=>allowed(anchor.edge))||!ends.every(anchor=>allowed(anchor.edge)))continue;
    const joined=junctionTrace(span,starts,ends,protectedPaths.filter(walk=>walk.structural),allowed,
     {max:125,tolerance:15});
    if(!joined)continue;
    const path=measured(joined.coords);
    if(raw.some(fix=>snap(fix,guide).offset<=3&&snap(fix,path).offset>7))continue;
    return {line:line.slice(0,i).concat(joined.coords,line.slice(turn)),
     promotion:{road:[...new Set(joined.labels)].join(' / '),metres:Number(walked.toFixed(1)),
      segments:turn-i,kind:'station-approach'}};
   }
  }
  return null;
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
 // A road-parallel span can be the middle of a connected pedestrian turn
 // at both ends. Snapping only its middle creates unmapped joins.
 function wouldBreakPedestrianTurns(line,start,end,geometry,paths) {
  if(!paths.length||start<2||end+2>=line.length||geometry.length<2)return false;
  const entry=[line[start-2],line[start-1]],exit=[line[end],line[end+1]];
  if([...entry,...exit].some(p=>!valid(p))||
     metres(...entry)<2||metres(...exit)<2)return false;
  const first=[geometry[0],geometry[1]],last=[geometry.at(-2),geometry.at(-1)];
  if(Math.abs(direction(...entry,...first))>.60||Math.abs(direction(...exit,...last))>.60||
     metres(line[start],geometry[0])<3||metres(line[end],geometry.at(-1))<3)return false;
  const mapped=p=>paths.some(path=>snap(p,path).offset<1.5);
  return sampled(line.slice(start-2,end+3),mapped)&&
   !sampled([line[start],geometry[0]],mapped)&&!sampled([line[end],geometry.at(-1)],mapped);
 }
 function align(route){
  const refs=route?.refs||[],roads=network(refs),parts=[],promoted=[];
  const graph=roadGraph(refs),protectedPaths=protectedWalks(refs);
  const walkingPaths=route.onFoot&&route.sourceType==='timeline_import'&&!route.corrected?
   refs.filter(ref=>ref.nodes&&ref.kind==='footway'&&ref.coords?.length>=2&&
    ref.coords.every(valid)).map(ref=>measured(ref.coords)):[];
  for(const original of route?.parts||[]){
   if(!Array.isArray(original)||original.length<2||!roads.length){
    parts.push(original);continue;
   }
   const ranges=[];
   const line=original;
   const raw=route.onFoot&&!route.corrected?(route.recorded||[]).flat().filter(valid):[];
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
    const sharedTurn=other=>{
     if(other.w!==run.w||other.sign===run.sign)return false;
     const tip=run.start===other.end?run.start:run.end===other.start?run.end:-1;
     return tip>0&&tip+1<line.length&&metres(line[tip-1],line[tip+1])<.3;
    };
    if(chosen.some(other=>run.start<=other.end&&other.start<=run.end&&!sharedTurn(other)))continue;
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
    if(wouldBreakPedestrianTurns(line,start.at,end.at,geometry,walkingPaths))continue;
    // Preserve the exact pavement vertex at the end of the transfer. A
    // diagonal to the next access vertex would cut the mapped entrance.
    if(start.extra&&start.at>0)geometry.unshift(line[start.at]);
    if(end.extra&&end.at<line.length-1)geometry.push(line[end.at]);
    const promotion={metres:Number((run.length+start.extra+end.extra).toFixed(1)),
     offset:Number(run.offset.toFixed(1)),segments:end.at-start.at,
     road:way.name};
    chosen.push({...run,start:start.at,end:end.at,sign,entryAnchor:!!start.extra&&start.at>0,
     exitAnchor:!!end.extra&&end.at<line.length-1,geometry,promotion});
   }
   // Let an established road guide the repair. The graph may connect
   // interruptions, but it must retain the physical carriageway of each run.
   const byRoad=new Map();
   for(const run of chosen){
    const name=run.promotion.road;
    if(name)byRoad.set(name,(byRoad.get(name)||0)+1);
   }
   if(route.onFoot&&!route.corrected&&route.independentRoadReferences?.length&&
      !route.railTransitions?.some(mark=>mark.kind!=='arrival-retrace')&&
      [...byRoad.values()].some(count=>count>=3)&&line.every(valid)){
    const corridor=continuousCorridor(line,graph,protectedPaths,raw);
    if(corridor){
     const anchors=chosen.filter(run=>run.start<=corridor.end&&run.end>=corridor.start);
     const path=measured(corridor.geometry);
     if([...corridor.names].some(name=>anchors.filter(run=>run.promotion.road===name).length>=3)&&
        anchors.every(run=>run.start>=corridor.start&&run.end<=corridor.end&&
         closeToWay(run.geometry.slice(run.entryAnchor?1:0,
          run.exitAnchor?-1:undefined),path,2.5))){
      const retained=chosen.filter(run=>!anchors.includes(run));
      const promotion={road:[...corridor.names].join(' / '),metres:Number(corridor.length.toFixed(1)),
       segments:corridor.end-corridor.start,kind:'continuous-road-corridor'};
      chosen.splice(0,chosen.length,...retained,{...corridor,promotion});
     }
    }
   }
   promoted.push(...chosen.map(run=>run.promotion));
   const output=[],seams=[];
   chosen.sort((a,b)=>a.start-b.start);
   const last=chosen.at(-1);
   const access=last?.w!==undefined&&route.onFoot&&!route.corrected&&original===route.parts.at(-1)?
    terminalAccess(line,last,roads[last.w],refs,route):null;
   let at=0;
   for(const run of chosen){
    output.push(...line.slice(at,run.start));
    const sharedVertex=run.start===at-1&&metres(output.at(-1),run.geometry[0])<.01;
    output.push(...run.geometry.slice(sharedVertex?1:0));
    if(run.w!==undefined&&(run!==last||!access))
     seams.push({at:output.length-(run.exitAnchor?2:1),name:roads[run.w].name});
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
    promoted.push(...joinDividedRoads(output,seams,graph,refs,raw));
   }
   if(route.onFoot&&!route.corrected)promoted.push(...alignTurnarounds(output,graph,protectedPaths,raw));
   const joined=joinJunctions(output,graph,protectedPaths,raw,route.onFoot&&!route.corrected);
   let displayed=joined.line;
   for(const mark of route.railTransitions||[]){
    const reverse=metres(displayed[0],mark.point)<.75;
    if(!reverse&&metres(displayed.at(-1),mark.point)>=.75)continue;
    const approach=stationApproach(reverse?displayed.toReversed():displayed,graph,protectedPaths,raw);
    if(!approach)continue;
    displayed=reverse?approach.line.toReversed():approach.line;
    promoted.push(approach.promotion);
   }
   parts.push(displayed);
   promoted.push(...joined.joins);
  }
  return {parts,promoted};
 }
 return {align,evidence,roadsideTransition,closeRoadInterruptions,closeKerbsideLoops,closePedestrianStreetCorridors,closeOffsetRoadTurns,closeTerminalPavement};
})();
if(typeof module!=='undefined')module.exports=RoadsideCentreline;
