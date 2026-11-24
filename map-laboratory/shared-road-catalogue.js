/* Offline physical-road reference catalogue from the owner's local ZIP.
 * Static road references are reusable evidence across journeys even if an
 * individual saved match exports only adjacent pedestrian references.
 * Original matches, GPS traces and credit history are NEVER rewritten.
 */
const SharedRoadCatalogue=(()=>{
 const M=111320,D=Math.PI/180;
 const road=/^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(?:_link)?$/;
 const valid=p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
 const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*D/2),(a[1]-b[1])*M);
 const identity=ref=>{
  const path=ref.coords.map(p=>p.map(v=>v.toFixed(6)).join(','));
  const forward=path.join(';'),reverse=path.slice().reverse().join(';');
  return (road.test(ref.kind)?'physical-road':ref.kind)+':'+(forward<reverse?forward:reverse);
 };
 function lines(geo){
  if(geo?.type==='LineString')return [geo.coordinates];
  if(geo?.type==='MultiLineString')return geo.coordinates;
  return [];
 }
 function collect(record,byId){
  const result=record?.journey?.processing_result||record?.processing_result||{};
  for(const key of ['road_geojson','cycling_geojson','a_road_geojson','motorway_geojson']){
   for(const f of result[key]?.features||[]){
    const kind=f.properties?.reference_kind||f.properties?.highway||'';
    if(!road.test(kind))continue;
    for(const coords of lines(f.geometry)){
     if(!Array.isArray(coords)||coords.length<2||coords.length>10000||!coords.every(valid))continue;
     const ref={kind,name:f.properties?.name||'',coords};
     const id=identity(ref);
     if(!byId.has(id))byId.set(id,ref);
    }
   }
  }
  return byId;
 }
 async function fromZip(zip,progress){
  const all=new Map(),total=zip.journeys.length;
  // A bounded, visible, one-time scan of exported reference geometry:
  // no rematching, network calls, or retention of decoded journey records.
  for(let i=0;i<total;i++){
   const record=await zip.read(i);
   collect(record,all);
   if((i+1)%24===0||i+1===total){
    progress?.(i+1,total,all.size);
    await new Promise(resolve=>setTimeout(resolve,0));
   }
   if(all.size>12000)throw Error('Diagnostic reference catalogue exceeds safe bound');
  }
  return [...all.values()];
 }
 function bounds(route){
  let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity;
  for(const line of route?.parts||[])for(const p of line||[]){
   if(!valid(p))continue;
   minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);
   minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);
  }
  return Number.isFinite(minX)?[minX,maxX,minY,maxY]:null;
 }
 function nearby(route,refs){
  const b=bounds(route);if(!b)return [];
  const margin=.00030;
  return (refs||[]).filter(ref=>ref.coords?.some(p=>
   p[0]>=b[0]-margin&&p[0]<=b[1]+margin&&p[1]>=b[2]-margin&&p[1]<=b[3]+margin));
 }
 function merge(local,extra){
  const byId=new Map();
  for(const ref of [...(local||[]),...(extra||[])])if(ref&&Array.isArray(ref.coords)&&ref.coords.length>1){
   const id=identity(ref);if(!byId.has(id))byId.set(id,ref);
  }
  return [...byId.values()];
 }
 function projected(p,a,b){
  const dx=(b[0]-a[0])*M*Math.cos(p[1]*D),dy=(b[1]-a[1])*M;
  const px=(p[0]-a[0])*M*Math.cos(p[1]*D),py=(p[1]-a[1])*M;
  const d=dx*dx+dy*dy,t=d>0?Math.max(0,Math.min(1,(px*dx+py*dy)/d)):0;
  return metres(p,[a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t]);
 }
 function needsSupplement(route){
  const roadSegments=[];
  for(const ref of route?.refs||[])if(road.test(ref.kind||'')){
   for(let i=1;i<ref.coords.length;i++)roadSegments.push([ref.coords[i-1],ref.coords[i]]);
  }
  let unsupported=0,total=0;
  for(const line of route?.parts||[])for(let i=1;i<line.length;i++){
   const a=line[i-1],b=line[i];if(!valid(a)||!valid(b))continue;
   const length=metres(a,b);if(length<2||length>120)continue;
   total+=length;
   const mid=[(a[0]+b[0])/2,(a[1]+b[1])/2];
   if(!roadSegments.some(([u,v])=>projected(mid,u,v)<9))unsupported+=length;
  }
  // Only pay the full-ZIP scan cost where a substantial road-length source
  // corridor currently lacks a usable physical-road reference. Independent
  // footpaths remain untouched unless *actual* nearby roads are discovered.
  return total>=180&&unsupported>=150;
 }
 return {collect,fromZip,bounds,nearby,merge,needsSupplement};
})();
if(typeof module!=='undefined')module.exports=SharedRoadCatalogue;
