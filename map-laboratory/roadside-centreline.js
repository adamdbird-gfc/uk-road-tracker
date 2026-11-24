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
 function network(refs){
  const ways=[];
  for(const ref of refs||[]){
   if(!road.test(ref?.kind)||!Array.isArray(ref.coords)||ref.coords.length<2)continue;
   const coords=ref.coords;
   if(!coords.every(valid))continue;
   const lengths=[0];for(let i=1;i<coords.length;i++)
    lengths.push(lengths[i-1]+metres(coords[i-1],coords[i]));
   if(lengths.at(-1)<30)continue;
   const minX=Math.min(...coords.map(p=>p[0])),maxX=Math.max(...coords.map(p=>p[0]));
   const minY=Math.min(...coords.map(p=>p[1])),maxY=Math.max(...coords.map(p=>p[1]));
   ways.push({coords,lengths,minX,maxX,minY,maxY});
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
 function potential(line,way,index){
  const a=line[index],b=line[index+1],length=metres(a,b);
  if(length<1||length>120)return null;
  const x=snap(a,way),y=snap(b,way);
  if(x.offset>8||y.offset>8)return null;
  const along=y.along-x.along;
  if(Math.abs(along)<length*.72)return null;
  const r0=way.coords[x.edge-1],r1=way.coords[x.edge];
  const alignment=Math.abs(direction(a,b,r0,r1));
  if(alignment<.94)return null;
  return {x,y,length,sign:Math.sign(along),offset:(x.offset+y.offset)/2};
 }
 // Sparse imported routes often encode a long road-parallel passage as
 // ONE matched leg. Confirm its *interior* independently against the
 // reference, not merely two endpoints near a distant or curving road.
 function supportedLongLeg(a,b,way){
  const length=metres(a,b);
  if(length<32||length>120)return false;
  const first=snap(a,way),last=snap(b,way);
  if(first.offset>8||last.offset>8)return false;
  const sign=Math.sign(last.along-first.along);
  if(!sign||Math.abs(last.along-first.along)<length*.85)return false;
  let previous=first;
  for(const t of [.25,.5,.75,1]){
   const current=t===1?last:snap(interpolate(a,b,t),way);
   if(current.offset>8||sign*(current.along-previous.along)<length*.12)return false;
   previous=current;
  }
  return true;
 }
 function align(route){
  const roads=network(route?.refs),parts=[],promoted=[];
  for(const original of route?.parts||[]){
   if(!Array.isArray(original)||original.length<2||!roads.length){
    parts.push(original);continue;
   }
   const line=original.slice(),ranges=[];
   for(let w=0;w<roads.length;w++){
    const way=roads[w];
    if(line.every(p=>!valid(p)||p[0]<way.minX-.00012||p[0]>way.maxX+.00012||
     p[1]<way.minY-.00012||p[1]>way.maxY+.00012))continue;
    let start=-1,last=null,length=0,offset=0,legs=0;
    const flush=()=>{
     const independentlySupported=legs===1&&start>=0&&
      supportedLongLeg(line[start],line[start+1],way);
     if(start>=0&&(legs>=2||independentlySupported)&&length>=32&&length/legs>=2){
      const end=start+legs,averageOffset=offset/legs;
      // Do not change geometry already on the road centreline.
      if(averageOffset>=1.6)ranges.push({start,end,length,offset:averageOffset,w});
     }
     start=-1;last=null;length=0;offset=0;legs=0;
    };
    for(let i=0;i<line.length-1;i++){
     const next=valid(line[i])&&valid(line[i+1])?potential(line,way,i):null;
     if(!next||last&&next.sign!==last.sign){
      flush();if(!next)continue;
     }
     if(start<0)start=i;
     last=next;length+=next.length;offset+=next.offset;legs++;
    }
    flush();
   }
   // Do not arbitrate closely competing physical roads by arbitrary order.
   ranges.sort((a,b)=>b.length-a.length||a.offset-b.offset);
   const used=new Set();
   for(const run of ranges){
    if([...Array(run.end-run.start+1)].some((_,j)=>used.has(run.start+j)))continue;
    const rivals=ranges.filter(x=>x!==run&&x.w!==run.w&&
     Math.max(x.start,run.start)<=Math.min(x.end,run.end)-1&&
     Math.abs(x.offset-run.offset)<1.5);
    if(rivals.length)continue;
    const way=roads[run.w],snapped=[];
    for(let j=run.start;j<=run.end;j++)snapped.push(snap(original[j],way));
    const monotone=snapped.every((x,j)=>!j||Math.abs(x.along-snapped[j-1].along)>.25&&
     Math.sign(x.along-snapped[j-1].along)===Math.sign(snapped.at(-1).along-snapped[0].along));
    if(!monotone)continue;
    // An independent path leading away from a road or through a square is
    // not a sustained, parallel, directionally ordered road corridor.
    for(let j=run.start;j<=run.end;j++){
     line[j]=snapped[j].position;used.add(j);
    }
    promoted.push({metres:Number(run.length.toFixed(1)),offset:Number(run.offset.toFixed(1)),segments:run.end-run.start});
   }
   parts.push(line);
  }
  return {parts,promoted};
 }
 return {align};
})();
if(typeof module!=='undefined')module.exports=RoadsideCentreline;
