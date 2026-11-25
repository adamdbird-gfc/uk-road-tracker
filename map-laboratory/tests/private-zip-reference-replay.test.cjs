const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createHash}=require('node:crypto');
const {test}=require('node:test');
const Bulk=require('../bulk-diagnostics.js');
const RouteFirst=require('../route-first.js');
const Network=require('../independent-road-network.js');
const RouteLed=require('../route-led-coverage.js');
const RailTransition=require('../rail-transition.js');

const archive=process.env.ROADPRINTS_DIAGNOSTICS_ZIP;
const root=path.join(__dirname,'..');
const request=async url=>new Response(fs.readFileSync(path.join(root,url)));
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const metres=(a,b)=>Math.hypot((a[0]-b[0])*111320*Math.cos(a[1]*Math.PI/180),(a[1]-b[1])*111320);

function projection(point,a,b){
 const scale=Math.cos(point[1]*Math.PI/180),x=(point[0]-a[0])*scale,y=point[1]-a[1];
 const u=(b[0]-a[0])*scale,v=b[1]-a[1];
 const t=Math.max(0,Math.min(1,(x*u+y*v)/(u*u+v*v)));
 return Math.hypot(x-u*t,y-v*t)*111320;
}

function coveredMetres(lines,refs,name){
 const edges=refs.filter(ref=>ref.name===name&&Network.isRoad(ref.kind)).flatMap(ref=>
  ref.coords.slice(1).map((b,i)=>[ref.coords[i],b]).filter(([a,b])=>metres(a,b)>.01));
 let total=0;
 for(const line of lines)for(let i=1;i<line.length;i++){
  const a=line[i-1],b=line[i],d=metres(a,b),n=Math.max(1,Math.ceil(d/2));
  for(let k=0;k<n;k++){
   const t=(k+.5)/n,p=[a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t];
   if(edges.some(([u,v])=>projection(p,u,v)<.5))total+=d/n;
  }
 }
 return total;
}

function containsLine(parts,expected){
 return parts.some(line=>line.some((_,i)=>
  expected.every((p,j)=>line[i+j]?.[0]===p[0]&&line[i+j]?.[1]===p[1])));
}

test('owner ZIP journey 25 keeps both station approach turns and the Prince of Orange alignment',{skip:!archive},async()=>{
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const index=zip.journeys.findIndex(item=>item.archiveOrdinal===25);
 const {journey}=await zip.read(index);
 const reconstructed=RouteFirst.reconstruct(journey);
 const lookup=await Network.create(request).lookup(reconstructed);
 const corrected=RouteLed.cleanup({...reconstructed,independentRoadReferences:lookup.refs});
 assert.equal(corrected.parts.length,1);
 assert(containsLine(corrected.parts,reconstructed.parts[0].slice(91)),
  'junction exit and station frontage retain the connected saved footway');
 assert(corrected.centrelinePromotions.some(run=>run.kind==='junction'&&
  run.road==='Devonshire Road / Old Road East / Windmill Street'));
});

test('owner ZIP Journey 7 restores road corridors and retains pedestrian sections', {skip:!archive},async()=>{
 const original=fs.readFileSync(archive);
 const zip=await Bulk.open(new Blob([original]));
 const realNetwork=Network.create(request);
 const index=6;
 assert.equal(zip.journeys.length,1714,'fixture is the original complete owner archive');
 const {journey}=await zip.read(index);
 const before=JSON.stringify(journey);
 const reconstructed=RouteFirst.reconstruct(journey);
 const sourceBefore=JSON.stringify(reconstructed);
 const lookup=await realNetwork.lookup(reconstructed);
 assert.match(lookup.status,/36 pinned OSM road references/);
 assert.equal(RouteLed.cleanup(reconstructed).centrelinePromotions.length,0);
 const route={...reconstructed,independentRoadReferences:lookup.refs};
 const corrected=RouteLed.cleanup(route);
 const output=corrected.parts;
 const source=reconstructed.parts[0];
 const measurements=[];
 for(const [road,min] of [['Old Road East',20],['Stone Street',73],['Clive Road',145]]){
  const gain=coveredMetres(output,lookup.refs,road)-coveredMetres(reconstructed.parts,lookup.refs,road);
  assert(gain>=min,road+' has independently measured centreline improvement');
  measurements.push(road+' '+gain.toFixed(1)+'m');
 }
 assert(corrected.centrelinePromotions.some(run=>run.kind==='junction'&&
  run.road==='Devonshire Road / Old Road East / Windmill Street'&&run.metres>=45));
 assert(corrected.centrelinePromotions.some(run=>run.kind==='junction'&&
  run.road==='Stone Street / Clive Road'&&run.metres>=40),'continuous road bridge and turn via Railway Place');
 assert(!output[0].some(p=>p[0]===source[122][0]&&p[1]===source[122][1]),'dense pavement continues on Clive Road');
 // Keep exact Community Square and saved vehicle-entrance tail geometry.
 assert(containsLine(output,source.slice(75,90)),'Community Square keeps its ordered source geometry');
 assert(containsLine(output,source.slice(132)),'car park approach keeps the saved tail and endpoint');
 assert(corrected.centrelinePromotions.some(run=>run.kind==='terminal-access'&&run.road==='Clive Road'));
 const service=reconstructed.refs.find(ref=>ref.kind==='service'&&containsLine([ref.coords],[source.at(-1)]));
 assert(service,'service road is in the selected journey’s own references');
 assert(containsLine(output,[service.coords[0],...source.slice(132)]),'use the mapped road-to-car-park turn');
 assert(!containsLine(output,[source[128],source[129]]),'frontage diversion is removed');
 const withoutFix=RouteLed.cleanup({...route,recorded:[]});
 assert(containsLine(withoutFix.parts,source.slice(125)),'keep frontage when endpoint evidence is absent');
 assert.equal(output.length,1,'the corrected journey remains one connected line');
 const alone=RouteLed.accumulate(new Set(),route);
 let history=new Set();
 for(let i=0;i<index;i++){
  const earlier=RouteFirst.reconstruct((await zip.read(i)).journey);
  const pinned=earlier.onFoot&&earlier.source==='saved match'?await realNetwork.lookup(earlier):{refs:[]};
  history=RouteLed.accumulate(history,{...earlier,independentRoadReferences:pinned.refs}).next;
 }
 const cumulative=RouteLed.accumulate(history,route);
 const ordered=result=>[...result.novel,...result.repeat].sort((a,b)=>a.id.localeCompare(b.id));
 assert.deepEqual(ordered(alone),ordered(cumulative),'prior journeys change repeat classification only');
 assert.equal(JSON.stringify(journey),before);
 assert.equal(JSON.stringify(reconstructed),sourceBefore);
 assert.equal(hash(fs.readFileSync(archive)),hash(original));
 const outage=await Network.create(async()=>new Response('',{status:503})).lookup(reconstructed);
 assert.match(outage.status,/index unavailable/);
 assert.deepEqual(RouteLed.cleanup({...reconstructed,independentRoadReferences:outage.refs}).parts,reconstructed.parts);
 console.log('Real centreline gain: '+measurements.join(', ')+'. Community Square and the car park access tail exact.');
});

test('owner ZIP Journey 9 stays on its established carriageway and loses three small returns', {skip:!archive},async()=>{
 const zip=await Bulk.open(new Blob([fs.readFileSync(archive)]));
 const replay=Bulk.roadReplay(zip);
 assert.equal(replay.railHidden,203);
 assert.equal(replay.journeys.length,1511);
 assert.equal(replay.journeys[7].archiveOrdinal,9);
 const {journey}=await replay.read(7);
 const original=JSON.stringify(journey);
 const route=RouteFirst.reconstruct(journey);
 const pinned=await Network.create(request).lookup(route);
 const result=RouteLed.cleanup({...route,independentRoadReferences:pinned.refs});
 assert.equal(result.parts.length,1);
 assert(containsLine(result.parts,route.parts[0].slice(5,22)),'Strand carriageway is retained');
 assert(containsLine(result.parts,route.parts[0].slice(32,55)),'through pedestrian branch is preserved');
 assert(containsLine(result.parts,route.parts[0].slice(78,89)),'Temple Bar geometry is retained');
 assert(result.centrelinePromotions.some(run=>run.kind==='divided-road'&&run.road==='Strand'&&run.metres>=60));
 const through=pinned.refs.find(ref=>ref.id===1456278171).coords.toReversed();
 assert(containsLine(result.parts,through),'follow the pinned southern carriageway to the mapped merge');
 assert(!containsLine(result.parts,route.parts[0].slice(71,75)),'do not switch to the parallel branch before the merge');

 assert(!containsLine(result.parts,route.parts[0].slice(139,145)),'Fleet Street reversal does not survive');
 assert.deepEqual(result.held.filter(item=>item.reason==='short-junction-return').map(item=>item.metres),[8.4,10.2,12.4]);
 assert.equal(JSON.stringify(journey),original);
});

test('owner ZIP Journey 10 cleans ordinary corners and keeps the supported Old Bailey return',{skip:!archive},async()=>{
 const zip=await Bulk.open(new Blob([fs.readFileSync(archive)]));
 const {journey}=await zip.read(9);
 const before=JSON.stringify(journey);
 const route=RouteFirst.reconstruct(journey);
 const pinned=await Network.create(request).lookup(route);
 const result=RouteLed.cleanup({...route,independentRoadReferences:pinned.refs});
 const [[...line]]=result.parts;
 const node=(way,id)=>{
  const ref=pinned.refs.find(r=>r.id===way);
  return ref.coords[ref.nodes.indexOf(id)];
 };
 const labels=result.centrelinePromotions.map(r=>[r.road,r.kind]);
 assert(labels.some(([road,kind])=>road==='Salisbury Court / Fleet Street'&&kind==='junction'));
 assert(labels.some(([road,kind])=>road==='Fleet Street'&&kind==='junction'));
 assert(labels.some(([road,kind])=>road==='Ludgate Hill / Old Bailey'&&kind==='road-turnaround'));
 const oldBailey=node(39276071,107685);
 assert.equal(line.filter(p=>p[0]===oldBailey[0]&&p[1]===oldBailey[1]).length,2);
 assert.deepEqual(line.at(-1),node(30881920,3972566716),'finish at the mapped Fleet Street junction');
 assert(result.held.some(r=>r.reason==='unverified-junction-endcap'&&r.metres===7.3));
 assert.equal(result.parts.length,1);
 assert.equal(JSON.stringify(journey),before);
});

test('City Thameslink street replay reaches the mapped entrance without drawing the inferred platform walk',{skip:!archive},async()=>{
 const zip=await Bulk.open(new Blob([fs.readFileSync(archive)]));
 const row=Bulk.roadReplay(zip).journeys.find(j=>j.archiveOrdinal===11);
 assert.equal(row.railAfter,true);
 const {journey}=await zip.read(10);
 const reconstructed=RouteFirst.reconstruct(journey);
 const pinned=await Network.create(request).lookup(reconstructed);
 const route={...reconstructed,independentRoadReferences:pinned.refs};
 const guarded=await RailTransition.create(request)(route,row);
 const door=pinned.refs.find(ref=>ref.id===815099762).coords[0];
 assert.deepEqual(guarded.railTransitions,[{point:door,label:'Likely transfer to rail'}]);
 assert.deepEqual(guarded.surfaceParts[0].at(-1),door);
 assert.strictEqual(guarded.parts,route.parts);
 assert(guarded.surfaceParts[0].length<route.parts[0].length);
 const output=RouteLed.cleanup({...guarded,parts:guarded.surfaceParts});
 assert.deepEqual(output.parts[0].at(-1),door);
 const circus=pinned.refs.find(ref=>ref.id===317900060).coords.toReversed();
 assert(containsLine(output.parts,circus),'follow the public road through Ludgate Circus');
 assert(output.centrelinePromotions.some(run=>run.kind==='station-approach'));
 const tail=guarded.surfaceParts[0].slice(-5);
 assert(containsLine(output.parts,tail),'retain the mapped approach into the station');
 const {journey:earlier}=await zip.read(9);
 const previous=RouteFirst.reconstruct(earlier),local=await Network.create(request).lookup(previous);
 const first=RouteLed.accumulate(new Set(),{...previous,independentRoadReferences:local.refs});
 const next=RouteLed.accumulate(first.next,{...guarded,parts:guarded.surfaceParts});
 assert(next.repeat.some(leg=>leg.a[0]===circus[0][0]&&leg.a[1]===circus[0][1]),
  'the shared road interval overlays previous coverage');

});

test('owner ZIP Journey 13 aligns Pentonville Road and retains the station approach',{skip:!archive},async()=>{
 const bytes=fs.readFileSync(archive);
 const replay=Bulk.roadReplay(await Bulk.open(new Blob([bytes])));
 const index=10, row=replay.journeys[index];
 assert.equal(row.archiveOrdinal,13);
 const {journey}=await replay.read(index);
 const before=JSON.stringify(journey);
 const route=RouteFirst.reconstruct(journey);
 assert.equal(route.recorded.flat().length,2);
 const pinned=await Network.create(request).lookup(route);
 const withRoads={...route,independentRoadReferences:pinned.refs};
 const transfer=RailTransition.create(request);
 assert.strictEqual(await transfer(withRoads,row),withRoads,'no immediate rail transition');
 const result=RouteLed.cleanup(withRoads);
 const road='Pentonville Road';
 const gain=coveredMetres(result.parts,pinned.refs,road)-coveredMetres(route.parts,pinned.refs,road);
 assert.equal(result.parts.length,1);
 assert(gain>=1100,'measure the change against the physical road');
 assert(coveredMetres(result.parts,pinned.refs,'Islington High Street')>=90,'connect the Angel turn to the road');
 const through=result.centrelinePromotions.find(run=>run.kind==='continuous-road-corridor');
 assert(through?.metres>=1250);
 assert.equal(through.road,'Upper Street / Islington High Street / Pentonville Road');
 const pentonville=pinned.refs.filter(ref=>ref.name===road&&Network.isRoad(ref.kind));
 const neighbourhoods=[-0.1112,-0.1169,-0.1183,-0.1209];
 for(const lon of neighbourhoods){
  const junction=result.parts[0].filter(([x])=>Math.abs(x-lon)<.00025);
  assert(junction.length>0,'the route keeps its mapped junction vertices');
  assert(junction.every(p=>pentonville.some(ref=>ref.coords.slice(1).some((b,i)=>
   metres(ref.coords[i],b)>.01&&projection(p,ref.coords[i],b)<.5))),
   'no pavement return inside the established road corridor');
 }
 assert(containsLine(result.parts,route.parts[0].slice(-20)),'keep the station-side pedestrian approach');
 const outside=await Network.create(async url=>request(url.replace('r3-index.json','index.json'))).lookup(route);
 assert.deepEqual(outside.refs,[]);
 assert.deepEqual(RouteLed.cleanup(route).parts,route.parts);
 assert.equal(JSON.stringify(journey),before);
 assert.equal(hash(fs.readFileSync(archive)),hash(bytes));
 console.log('Pentonville Road centreline gain: '+gain.toFixed(1)+' m. Station-side pedestrian approach exact.');
});

test('station return repeats Windmill Street and keeps its distinct square route',{skip:!archive},async()=>{
 const zip=await Bulk.open(new Blob([fs.readFileSync(archive)]));
 const provider=Network.create(request);
 const load=async index=>{
  const {journey}=await zip.read(index);
  const route=RouteFirst.reconstruct(journey);
  return {...route,independentRoadReferences:(await provider.lookup(route)).refs};
 };
 const morning=RouteLed.accumulate(RouteLed.createLedger(),await load(6));
 const home=await load(14),original=JSON.stringify(home);
 const result=RouteLed.cleanup(home);
 const street='Windmill Street';
 const sameRoad=result.centrelinePromotions.filter(run=>run.kind==='road-interruption'&&run.road===street);
 assert.equal(sameRoad.length,3,'return uses the through road at all three interruptions');
 assert(containsLine(result.parts,home.parts[0].slice(0,17)),'the distinct square route remains visible');
 assert.equal(result.links.length,1);
 const connector=home.independentRoadReferences.find(ref=>ref.id===987392190).coords.slice(0,5);
 assert.deepEqual(result.links[0],connector,'draw the mapped square exit up to the Windmill Street node');
 assert.equal(RouteLed.cleanup({...home,recorded:[]}).links.length,0);
 const returnCoverage=RouteLed.accumulate(morning.next,home);
 const lines=parts=>parts.map(({a,b})=>[a,b]);
 assert(coveredMetres(lines(returnCoverage.repeat),home.independentRoadReferences,street)>860);
 assert(coveredMetres(lines(returnCoverage.novel),home.independentRoadReferences,street)<.1);
 assert.deepEqual(returnCoverage.links,result.links);
 assert(returnCoverage.centrelinePromotions.some(run=>run.kind==='junction'&&
  run.road==='Windmill Street / Old Road East / Devonshire Road'));
 assert(returnCoverage.novel.some(leg=>leg.id.startsWith('saved:')),'distinct geometry remains new');
 assert.deepEqual(RouteLed.cleanup(home).parts,result.parts,'earlier journeys only affect classification');
 assert.equal(JSON.stringify(home),original);
});
