/* LAB-only route-led cumulative experiment.
 * Accepted, manually corrected, RouteFirst reconstructed geometry is primary.
 * No extra network match, inferred gap, connector or branch is manufactured.
 */
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
  for(const part of intervals(route)){
   if(next.has(part.id))repeat.push(part);
   else{novel.push(part);next.add(part.id)}
  }
  return {novel,repeat,next,sourceSegments:(route?.parts||[]).reduce((sum,line)=>sum+Math.max(0,(line?.length||0)-1),0)};
 }
 return {intervals,accumulate,key};
})();
if(typeof module!=='undefined')module.exports=RouteLedCoverage;
