/* Roadprints Lab: independent physical-road reference lookup.
 * Only a bounded geographic rectangle is sent to public OSM Overpass.
 * No user journeys, GPS points, titles or archived files are uploaded.
 * Never infer proof of travel from this network: the saved journey remains
 * the sole movement evidence for source-ordered centreline selection.
 */
const IndependentRoadNetwork=(()=>{
 const ROAD=/^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(_link)?$/;
 const M=111320, D=Math.PI/180;
 const valid=p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite)&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;
 const distance=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
 const coordsKey=c=>{const a=c.map(p=>p.map(v=>Number(v).toFixed(7)).join(','));
  return a.join(';')<a.slice().reverse().join(';')?a.join(';'):a.slice().reverse().join(';');};
 function bounds(route){
  const coords=(route?.parts||[]).flat().filter(valid);
  if(coords.length<2)return null;
  const lon=coords.map(p=>p[0]),lat=coords.map(p=>p[1]);
  const south=Math.min(...lat)-.00030,north=Math.max(...lat)+.00030,
    west=Math.min(...lon)-.00030,east=Math.max(...lon)+.00030;
  if(north-south>.035||east-west>.035||north<=south||east<=west)return null;
  return {south,west,north,east};
 }
 function needsNetwork(route){
  if(!route||!(route.parts||[]).length)return false;
  const own=(route.refs||[]).filter(r=>ROAD.test(r.kind||''));
  const existing=[];
  for(const ref of own)for(let i=1;i<(ref.coords||[]).length;i++)
   if(valid(ref.coords[i-1])&&valid(ref.coords[i]))existing.push([ref.coords[i-1],ref.coords[i]]);
  const distPoint=(p,a,b)=>{
   const dx=(b[0]-a[0])*M*Math.cos(p[1]*D),dy=(b[1]-a[1])*M;
   const x=(p[0]-a[0])*M*Math.cos(p[1]*D),y=(p[1]-a[1])*M;
   const d=dx*dx+dy*dy,t=d>.01?Math.max(0,Math.min(1,(x*dx+y*dy)/d)):0;
   return Math.hypot(x-t*dx,y-t*dy);
  };
  let without=0,total=0;
  for(const part of route.parts)for(let i=1;i<part.length;i++){
   const a=part[i-1],b=part[i];if(!valid(a)||!valid(b))continue;
   const length=distance(a,b);if(length<2||length>120)continue;
   const midpoint=[(a[0]+b[0])/2,(a[1]+b[1])/2];total+=length;
   if(!existing.some(([u,v])=>distPoint(midpoint,u,v)<=8))without+=length;
  }
  return total>=150&&without>=75&&!!bounds(route);
 }
 function parse(payload){
  if(!Array.isArray(payload?.elements))throw Error('Unexpected public road-network response');
  const results=[],seen=new Set();
  for(const way of payload.elements){
   if(way.type!=='way'||!ROAD.test(way.tags?.highway||'')||!Array.isArray(way.geometry))continue;
   const coords=way.geometry.map(p=>[p.lon,p.lat]);
   if(coords.length<2||coords.length>2500||!coords.every(valid))continue;
   const key=coordsKey(coords);
   if(seen.has(key))continue;
   seen.add(key);
   results.push({kind:way.tags.highway,name:way.tags.name||'',coords});
   if(results.length>1500)throw Error('Local road network exceeds safe limit');
  }
  return results;
 }
 function merge(local,external){
  const map=new Map();
  for(const ref of [...(local||[]),...(external||[])]){
   if(!Array.isArray(ref?.coords)||ref.coords.length<2||!ref.coords.every(valid))continue;
   const key=(ROAD.test(ref.kind)?'road':ref.kind)+':'+coordsKey(ref.coords);
   if(!map.has(key))map.set(key,ref);
  }
  return [...map.values()];
 }
 async function lookup(route,request=fetch){
  const b=bounds(route);if(!b)throw Error('Journey is outside safe road-network bounds');
  const rect=[b.south,b.west,b.north,b.east].map(n=>n.toFixed(7)).join(',');
  const query='[out:json][timeout:20];way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|road)(_link)?$"]('+rect+');out geom;';
  const url='https://overpass-api.de/api/interpreter?data='+encodeURIComponent(query);
  const response=await request(url,{signal:AbortSignal.timeout(25000)});
  if(!response.ok)throw Error('Independent road-network request returned '+response.status);
  const payload=await response.json();
  return parse(payload);
 }
 return {bounds,needsNetwork,parse,merge,lookup};
})();
if(typeof module!=='undefined')module.exports=IndependentRoadNetwork;
