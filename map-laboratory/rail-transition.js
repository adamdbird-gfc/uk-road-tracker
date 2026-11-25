/* A rail record supplies timing context only. Walking geometry comes from
 * the selected journey plus the explicit public station entrance graph. */
const RailTransition = (() => {
  const M = 111320;
  const valid = p => Array.isArray(p) && p.length === 2 && p.every(Number.isFinite);
  const distance = (a,b) => Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*Math.PI/360),(a[1]-b[1])*M);
  const lerp = (a,b,t) => [a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t];

  function offset(p,a,b) {
    const scale = Math.cos(p[1]*Math.PI/180);
    const x=(p[0]-a[0])*scale,y=p[1]-a[1],u=(b[0]-a[0])*scale,v=b[1]-a[1];
    const t=Math.max(0,Math.min(1,(x*u+y*v)/(u*u+v*v||1)));
    return distance(p,lerp(a,b,t));
  }

  function follows(a,b,edges) {
    const samples = Math.max(1,Math.ceil(distance(a,b)/3));
    for(let k=0;k<=samples;k++) {
      const p=lerp(a,b,k/samples);
      if(!edges.some(edge=>offset(p,edge.a,edge.b)<=1))return false;
    }
    return true;
  }

  function entranceOnApproach(line,entrances,refs,arrival) {
    const indoor=[],outside=[];
    for(const ref of refs) {
      if(!ref.nodes||!ref.coords?.every(valid))continue;
      if(ref.indoor!=='yes'&&(ref.tunnel==='yes'||Number(ref.layer)<0))continue;
      const edges=ref.indoor==='yes'?indoor:outside;
      for(let i=1;i<ref.coords.length;i++)
        edges.push({a:ref.coords[i-1],b:ref.coords[i],nodes:[ref.nodes[i-1],ref.nodes[i]]});
    }
    if(!indoor.length)return null;
    const candidates=[];
    for(const entrance of entrances) {
      if((entrance.exitOnly&&!arrival)||distance(line.at(-1),entrance.point)>150)continue;
      const enters=indoor.filter(edge=>edge.nodes.includes(entrance.id));
      const approaches=outside.filter(edge=>edge.nodes.includes(entrance.id));
      if(!enters.length||!approaches.length)continue;
      for(let i=1;i<line.length-2;i++) {
        if(distance(line[i],entrance.point)>.75||!follows(line[i-1],line[i],approaches)||
           !follows(line[i],line[i+1],enters))continue;
        let walked=0;
        for(let j=i+1;j<line.length&&walked<12;j++){
          if(!follows(line[j-1],line[j],indoor))break;
          walked+=distance(line[j-1],line[j]);
        }
        if(walked>=12)candidates.push({point:entrance.point,index:i});
      }
    }
    return candidates.length===1?candidates[0]:null;
  }

  function arrivalRetrace(line,raw,entrances,refs) {
    if(raw?.length<4||!raw.slice(0,3).every(valid)||
       distance(raw[0],raw[2])>4||distance(raw[0],raw[1])<50||
       distance(raw[0],line[0])>5)return null;
    const indoorNodes=new Set(refs.filter(ref=>ref.indoor==='yes').flatMap(ref=>ref.nodes||[]));
    const stationDoors=entrances.filter(door=>indoorNodes.has(door.id));
    for(let end=Math.min(120,line.length-3);end>=10;end--){
      if(distance(line[end],raw[2])>5)continue;
      const mid=Math.floor(end/2);
      const prefix=line.slice(0,end+1);
      if(prefix.some((p,i)=>distance(p,prefix[end-i])>5)||
         distance(line[mid],raw[1])>30||
         !stationDoors.some(door=>prefix.some(p=>distance(p,door.point)<1)))continue;
      return {index:end,point:line[end]};
    }
    return null;
  }

  function apply(route,context,entrances) {
    if(!route.onFoot||route.corrected||route.source!=='saved match'||
       route.parts.length!==1||!entrances.length)return route;
    const [line]=route.parts,refs=route.independentRoadReferences||[];
    if(line.length<4||!line.every(valid))return route;
    const out=context.railAfter?entranceOnApproach(line,entrances,refs,false):null;
    const back=context.railBefore?entranceOnApproach(line.toReversed(),entrances,refs,true):null;
    const retrace=context.railBefore&&!back&&route.sourceType==='timeline_import'?
      arrivalRetrace(line,route.recorded?.[0],entrances,refs):null;
    const first=back?line.length-1-back.index:(retrace?.index??0),last=out?out.index:line.length-1;
    if((!out&&!back&&!retrace)||last<=first)return route;
    const surface=line.slice(first,last+1);
    if(back)surface[0]=back.point;
    if(out)surface[surface.length-1]=out.point;
    const marks=[];
    if(back)marks.push({point:back.point,label:'Likely transfer from rail'});
    if(retrace)marks.push({point:retrace.point,label:'Likely transfer from rail',kind:'arrival-retrace'});
    if(out)marks.push({point:out.point,label:'Likely transfer to rail'});
    return {...route,surfaceParts:[surface],railTransitions:marks};
  }

  function create(request=fetch,url='./reference-network/v1/station-entrances.json') {
    let cached;
    return async (route,context)=>{
      if(!route.onFoot||route.corrected||!route.independentRoadReferences?.some(ref=>ref.indoor==='yes')||
         !context||!context.railBefore&&!context.railAfter)return route;
      cached??=request(url,{signal:AbortSignal.timeout(5000)}).then(async response=>{
        if(!response.ok)return [];
        const data=await response.json();
        return data.schema===1&&Array.isArray(data.entrances)?data.entrances.filter(e=>
          Number.isSafeInteger(e.id)&&valid(e.point)):[];
      }).catch(()=>[]);
      return apply(route,context,await cached);
    };
  }
  return {apply,create};
})();
if(typeof module!=='undefined')module.exports=RailTransition;
