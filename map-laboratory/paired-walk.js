/* Reconcile sparse out-and-back Timeline matches using mapped walking ways. */
const PairedWalk = (() => {
 const walking = /^(footway|path|pedestrian|steps|living_street|residential|unclassified)$/;
 const valid = p => Array.isArray(p) && Number.isFinite(p[0]) && Number.isFinite(p[1]);
 const distance = (a,b) => Math.hypot((a[0]-b[0])*Math.cos((a[1]+b[1])*Math.PI/360),a[1]-b[1])*111320;
 const between = (a,b,t) => [a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t];
 function offset(p,a,b){
  const scale=Math.cos(p[1]*Math.PI/180);
  const x=(b[0]-a[0])*scale,y=b[1]-a[1],u=(p[0]-a[0])*scale,v=p[1]-a[1];
  const t=Math.max(0,Math.min(1,(x*u+y*v)/(x*x+y*y||1)));
  return Math.hypot(u-x*t,v-y*t)*111320;
 }
 const legs = line => line.slice(1).map((b,i)=>[line[i],b]);
 function nearby(p,edges){
  let minimum=Infinity;
  for(const [a,b] of edges)minimum=Math.min(minimum,offset(p,a,b));
  return minimum;
 }
 const measured = edges => edges.reduce((sum,[a,b])=>sum+distance(a,b),0);
 function supported(edges,reference,tolerance){
  let metres=0;
  for(const [a,b] of edges){
   const length=distance(a,b),samples=Math.ceil(length/6);
   for(let i=0;i<samples;i++)
    if(nearby(between(a,b,(i+.5)/samples),reference)<=tolerance)metres+=length/samples;
  }
  return metres;
 }
 function eligible(route){
  return route.onFoot&&!route.corrected&&!route.removedRoadIds?.size&&!route.railTransitions?.length&&
   route.source==='saved match'&&route.sourceType==='timeline_import'&&
   route.parts.length===1&&route.parts[0].length>=5&&route.parts[0].every(valid)&&
   route.recorded.flat().length>=2&&route.recorded.flat().length<=3&&
   route.independentRoadReferences?.some(ref=>ref.footway==='crossing');
 }
 function adjacent(earlier,later){
  if(!earlier||!later||earlier.mode!=='walking'||later.mode!=='walking'||
     earlier.archiveOrdinal+1!==later.archiveOrdinal)return false;
  const stop=Date.parse(later.started_at)-Date.parse(earlier.ended_at);
  return stop>=20*60000&&stop<=6*3600000;
 }
 function crossingDirection(line,ref){
  for(const [a,b] of legs(ref.coords)){
   if(distance(a,b)<2)continue;
   const i=line.findIndex(p=>distance(p,a)<.8),j=line.findIndex(p=>distance(p,b)<.8);
   if(i>=0&&j>=0&&Math.abs(i-j)===1)return Math.sign(j-i);
  }
  return 0;
 }
 function walkingPath(line,refs){
  const start=line[0],finish=line.at(-1),source=legs(line);
  const nodes=new Map(),edgesByNode=new Map();
  for(const ref of refs){
   for(let i=1;i<ref.nodes.length;i++){
    const a=ref.coords[i-1],b=ref.coords[i],length=distance(a,b);
    if(length<.01)continue;
    const lateral=nearby(between(a,b,.5),source);
    if(lateral>25||nearby(a,source)>28||nearby(b,source)>28)continue;
    const u=ref.nodes[i-1],v=ref.nodes[i],cost=length*(1+lateral*.12);
    nodes.set(u,a);nodes.set(v,b);
    for(const [from,to] of [[u,v],[v,u]]){
     if(!edgesByNode.has(from))edgesByNode.set(from,[]);
     edgesByNode.get(from).push({to,cost});
    }
   }
  }
  // Use mapped nodes near the better-supported walk's endpoints.
  const costs=new Map(),previous=new Map(),queue=new Set();
  for(const [node,p] of nodes){
   const metres=distance(start,p);
   if(metres>25)continue;
   costs.set(node,metres*3);queue.add(node);
  }
  let end,score=Infinity;
  while(queue.size){
   let node,low=Infinity;
   for(const id of queue)if(costs.get(id)<low){node=id;low=costs.get(id);}
   if(low>=score)break;
   queue.delete(node);
   const terminal=distance(nodes.get(node),finish);
   if(terminal<=12&&low+terminal*3<score){end=node;score=low+terminal*3;}
   for(const {to,cost} of edgesByNode.get(node)||[]){
    if(low+cost>=(costs.get(to)??Infinity))continue;
    costs.set(to,low+cost);previous.set(to,node);queue.add(to);
   }
  }
  if(end===undefined)return null;
  const path=[];
  for(let node=end;node!==undefined;node=previous.get(node))path.push(nodes.get(node));
  return path.reverse();
 }
 function match(first,second){
  if(!eligible(first)||!eligible(second))return null;
  const a=first.parts[0],b=second.parts[0];
  const ends=[distance(a[0],b.at(-1)),distance(a.at(-1),b[0])];
  if(Math.max(...ends)>85||Math.min(...ends)>35)return null;
  const aLegs=legs(a),bLegs=legs(b),aLength=measured(aLegs),bLength=measured(bLegs);
  if(Math.min(aLength,bLength)<120||Math.max(aLength,bLength)>900||
     Math.min(aLength,bLength)/Math.max(aLength,bLength)<.65||
     supported(aLegs,bLegs,18)<aLength*.35||supported(bLegs,aLegs,18)<bLength*.35)return null;
  const refs=[...new Map([...first.independentRoadReferences,...second.independentRoadReferences]
   .filter(ref=>walking.test(ref.kind)&&ref.nodes?.length>=2&&ref.nodes.length===ref.coords?.length)
   .map(ref=>[ref.id,ref])).values()].sort((x,y)=>x.id-y.id);
  const shared=refs.filter(ref=>ref.footway==='crossing'&&
    crossingDirection(a,ref)*crossingDirection(b,ref)===-1);
  if(!shared.length)return null;
  const network=refs.flatMap(ref=>legs(ref.coords));
  const aSupport=supported(aLegs,network,2)/aLength,bSupport=supported(bLegs,network,2)/bLength;
  if(Math.max(aSupport,bSupport)<.82||Math.abs(aSupport-bSupport)<.32)return null;
  const forward=aSupport>bSupport;
  const path=walkingPath(forward?a:b,refs);
  if(!path||!shared.some(ref=>crossingDirection(path,ref)))return null;
  const length=measured(legs(path)),guideLength=forward?aLength:bLength;
  if(length<guideLength*.72||length>guideLength*1.25)return null;
  return {first:forward?path:path.toReversed(),second:forward?path.toReversed():path,refs,length};
 }
 async function forReplay(index,route,rows,load){
  if(!eligible(route))return route;
  const partner=i=>{
   const before=adjacent(rows[i-1],rows[i]),after=adjacent(rows[i],rows[i+1]);
   return before===after?null:before?i-1:i+1;
  };
  const other=partner(index);
  if(other===null||partner(other)!==index)return route;
  const candidate=await load(other);
  if(candidate.networkUnavailable)return {...route,networkUnavailable:true};
  const outward=index<other;
  const pair=outward?match(route,candidate):match(candidate,route);
  return pair?{...route,pairedWalk:{line:outward?pair.first:pair.second,refs:pair.refs,length:pair.length}}:route;
 }
 return {match,forReplay};
})();
if(typeof module!=='undefined')module.exports=PairedWalk;
