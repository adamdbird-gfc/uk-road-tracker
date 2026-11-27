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
const PairedWalk=require('../paired-walk.js');

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

test('owner ZIP journey 27 excludes the station retrace and follows the pedestrianised Strand',{skip:!archive},async()=>{
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const index=zip.journeys.findIndex(item=>item.archiveOrdinal===27);
 const {journey}=await zip.read(index);
 const source=RouteFirst.reconstruct(journey),lookup=await Network.create(request).lookup(source);
 const route={...source,independentRoadReferences:lookup.refs};
 const transition=await RailTransition.create(request)(route,zip.journeys[index]);
 assert.equal(transition.railTransitions[0].kind,'arrival-retrace');
 assert.deepEqual(transition.surfaceParts,[source.parts[0].slice(58)]);
 const result=RouteLed.cleanup({...transition,parts:transition.surfaceParts});
 assert.equal(result.parts.length,1);
 assert(result.centrelinePromotions.some(run=>run.kind==='pedestrian-street-corridor'&&run.road==='Strand'));
 assert(result.centrelinePromotions.some(run=>run.kind==='terminal-pavement-crossing'&&run.road==='Strand'));
 assert(result.parts[0].some(p=>lookup.refs.some(ref=>ref.sharedCrossing&&ref.coords.some(q=>String(p)===String(q)))));
 assert.deepEqual(result.parts[0].at(-1),source.parts[0].at(-1));
});

test('owner ZIP journey 29 uses the shared Bride Lane and Fleet Street junction in both directions',{skip:!archive},async()=>{
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const {journey}=await zip.read(zip.journeys.findIndex(item=>item.archiveOrdinal===29));
 const source=RouteFirst.reconstruct(journey),lookup=await Network.create(request).lookup(source);
 const result=RouteLed.cleanup({...source,independentRoadReferences:lookup.refs});
 const joins=result.centrelinePromotions.filter(run=>run.kind==='offset-road-turn');
 assert.deepEqual(joins.map(run=>run.road),[
  'Ludgate Hill / Fleet Street / Bride Lane','Bride Lane / Fleet Street / Ludgate Hill'
 ]);
 assert.deepEqual(result.parts[0][0],source.parts[0][0]);
 assert.deepEqual(result.parts[0].at(-1),source.parts[0].at(-1));
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
 const outside=await Network.create(async url=>request(url.replace('r9-index.json','index.json'))).lookup(route);
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

test('London and Gravesend approaches stay connected and Rochester walks share one path',{skip:!archive},async()=>{
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const network=Network.create(request),rail=RailTransition.create(request);
 let ledger=RouteLed.createLedger();
 const checked=[];
 let outward;
 const load=async index=>{
  const {journey}=await zip.read(index);
  const route=RouteFirst.reconstruct(journey);
  const pinned=route.onFoot&&route.source==='saved match'?await network.lookup(route):{refs:[]};
  const guarded=await rail({...route,independentRoadReferences:pinned.refs,
   displayRoadReferences:pinned.displayRoads,forkContext:pinned.forkContext},zip.journeys[index]);
  return guarded.surfaceParts?{...guarded,parts:guarded.surfaceParts}:guarded;
 };
 for(const [index,row] of zip.journeys.entries()){
  if(row.archiveOrdinal>122)break;
  const guarded=await load(index);
  const pinned={refs:guarded.independentRoadReferences,forkContext:guarded.forkContext};
  const route=await PairedWalk.forReplay(index,guarded,zip.journeys,load);
  const result=RouteLed.accumulate(ledger,route);
  ledger=result.next;
  if(row.archiveOrdinal===104){
   checked.push(104);
   const crossing=pinned.refs.find(ref=>ref.id===622526038);
   assert(result.accessLinks.some(line=>line.some(p=>crossing.coords.some(q=>metres(p,q)<1))));
   const side=pinned.refs.find(ref=>ref.id===112172868);
   assert.equal(coveredMetres(result.novel.map(({a,b})=>[a,b]),[side],side.name),0);
  }
  if(row.archiveOrdinal===105){
   checked.push(105);
   const crossing=pinned.refs.find(ref=>ref.id===95373719);
   const kerb=pinned.refs.find(ref=>ref.id===1510873254);
   for(const ref of [crossing,kerb])
    assert(result.accessLinks.some(line=>line.some(p=>ref.coords.some(q=>metres(p,q)<1))));
   const side=pinned.refs.find(ref=>ref.id===974786028);
   assert.equal(coveredMetres(result.novel.map(({a,b})=>[a,b]),[side],side.name),0);
  }
  if(row.archiveOrdinal===107){
   checked.push(107);
   const lines=parts=>parts.map(({a,b})=>[a,b]);
   assert(coveredMetres(lines(result.repeat),pinned.refs,'Rathmore Road')>70);
   assert(coveredMetres(lines(result.novel),pinned.refs,'Rathmore Road')<3);
   assert(result.novel.every(({id})=>id.startsWith('road:')));
   assert(result.accessLinks.length>0);
  }
  if(row.archiveOrdinal===108||row.archiveOrdinal===112){
   checked.push(row.archiveOrdinal);
   assert.deepEqual(result.novel,[]);
   const kind=row.archiveOrdinal===108?'kerbside-loop':'offset-road-turn';
   assert(result.centrelinePromotions.some(run=>run.kind===kind));
  }
  if(row.archiveOrdinal===114){
   checked.push(114);
   assert.deepEqual(result.novel,[]);
   const saved=guarded.parts[0],lines=result.repeat.map(({a,b})=>[a,b]);
   assert(coveredMetres(lines,pinned.refs,'Fleet Street')>80);
   for(const end of [saved[0],saved.at(-1)]){
    const approach=result.accessLinks.find(line=>line.some(point=>metres(point,end)<.3));
    assert(approach);
    assert(approach.some(point=>lines.some(([a,b])=>projection(point,a,b)<.5)));
   }
  }
  if(row.archiveOrdinal===115){
   checked.push(115);
   const lines=result.repeat.map(({a,b})=>[a,b]);
   assert(pinned.forkContext.some(ref=>ref.name==='Fleet Street'));
   assert(coveredMetres(lines,pinned.refs,'Fleet Street')>45);
   assert(result.novel.reduce((total,{a,b})=>total+metres(a,b),0)<5);
  }
  if(row.archiveOrdinal===121||row.archiveOrdinal===122){
   checked.push(row.archiveOrdinal);
   const pair=route.pairedWalk;
   assert(pair);
   assert.notDeepEqual(pair.line,guarded.parts[0]);
   assert.equal(guarded.pairedWalk,undefined);
   const edges=pair.refs.flatMap(ref=>ref.coords.slice(1).map((b,i)=>[ref.coords[i],b]));
   const crosses=pair.refs.filter(ref=>ref.footway==='crossing');
   const hasEdge=(list,a,b)=>list.some(([x,y])=>
    x===a&&y===b||x===b&&y===a||metres(x,a)<.1&&metres(y,b)<.1||metres(x,b)<.1&&metres(y,a)<.1);
   const lineEdges=pair.line.slice(1).map((b,i)=>[pair.line[i],b]);
   assert(lineEdges.every(([a,b])=>hasEdge(edges,a,b)));
   assert(crosses.some(ref=>ref.coords.slice(1).some((b,i)=>hasEdge(lineEdges,ref.coords[i],b))));
   assert.equal(coveredMetres(lineEdges,pinned.refs,'Corporation Street'),0);
   if(row.archiveOrdinal===121)outward=pair.line;
   else{
    assert.deepEqual(pair.line,outward.toReversed());
    assert.deepEqual(result.novel,[]);
    assert.equal(result.repeat.length,lineEdges.length);
   }
  }
 }
 assert.deepEqual(checked,[104,105,107,108,112,114,115,121,122]);
});

test('owner ZIP through streets between sparse pedestrian anchors stay connected',{skip:!archive},async()=>{
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const network=Network.create(request);
 for(const archiveOrdinal of [647,793]){
  const index=zip.journeys.findIndex(row=>row.archiveOrdinal===archiveOrdinal);
  const {journey}=await zip.read(index);
  const route=RouteFirst.reconstruct(journey),pinned=await network.lookup(route);
  assert.equal(route.parts.length,1);
  assert(pinned.refs.length>0);
  const result=RouteLed.cleanup({...route,independentRoadReferences:pinned.refs});
  assert.equal(result.parts.length,1,`ZIP ${archiveOrdinal} remains one supported route`);
  assert(!result.held.some(row=>row.reason==='unverified-road-excursion-between-pedestrian-observations'));
 }
});

const editedJourney=process.env.ROADPRINTS_EDITED_JOURNEY;
test('redrawn ZIP journey retains Roadprints road edits and reuses Strand walking coverage',{skip:!archive||!editedJourney},async()=>{
 const {journey:edited}=JSON.parse(fs.readFileSync(editedJourney));
 const original=JSON.stringify(edited);
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const selected=zip.journeys.findIndex(row=>row.started_at===edited.started_at);
 assert(selected>=0);
 const stored=(await zip.read(selected)).journey;
 assert.equal(stored.journey_id,edited.journey_id);
 assert(edited.revision>stored.revision);
 const network=Network.create(request),rail=RailTransition.create(request);
 async function load(index){
  const row=zip.journeys[index];
  const journey=index===selected?edited:(await zip.read(index)).journey;
  const route=RouteFirst.reconstruct(journey);
  const lookup=route.onFoot&&route.source==='saved match'?await network.lookup(route):{refs:[]};
  return rail({...route,independentRoadReferences:lookup.refs,
   displayRoadReferences:lookup.displayRoads,forkContext:lookup.forkContext},row);
 }
 let ledger=RouteLed.createLedger();
 for(let i=0;i<=selected;i++){
  const route=await load(i);
  const surface=route.surfaceParts?{...route,parts:route.surfaceParts}:route;
  const matched=await PairedWalk.forReplay(i,surface,zip.journeys,load);
  const coverage=RouteLed.accumulate(ledger,matched);
  ledger=coverage.next;
  if(i!==selected)continue;
  assert.equal(route.removedRoadIds.size,6);
  assert.deepEqual(route.recorded,[edited.route_geometry.coordinates]);
  const alternate=route.independentRoadReferences.filter(ref=>ref.walkingCarriageway);
  const representative=route.independentRoadReferences.filter(ref=>
   alternate.some(side=>side.walkingCarriageway.includes(ref.id)));
  assert(alternate.length&&representative.length);
  assert.equal(coveredMetres(coverage.novel.map(({a,b})=>[a,b]),alternate,'Strand'),0);
  assert(coveredMetres(coverage.repeat.map(({a,b})=>[a,b]),representative,'Strand')>360);
  assert(coverage.novel.some(edge=>edge.id.startsWith('footway:')),'keep the independent station approach');
  for(const ref of route.independentRoadReferences.filter(ref=>RouteFirst.isRemovedRoad(ref,route.removedRoadIds)))
   assert.equal(coveredMetres(coverage.novel.map(({a,b})=>[a,b]),[ref],ref.name),0);
 }
 assert.equal(JSON.stringify(edited),original);
});

test('batch replay keeps directed driving, station exit and park path evidence',{skip:!archive},async()=>{
 const zip=Bulk.roadReplay(await Bulk.open(new Blob([fs.readFileSync(archive)])));
 const network=Network.create(request);
 const driving=Network.create(request,'./reference-network/driving-v1/',Date.now,true);
 const rail=RailTransition.create(request);
 for(const ordinal of [138,150,152,158]){
  const index=zip.journeys.findIndex(row=>row.archiveOrdinal===ordinal),row=zip.journeys[index];
  assert(index>=0);
  const {journey}=await zip.read(index);
  const route=RouteFirst.reconstruct(journey),original=JSON.stringify(route);
  const pinned=route.onFoot?await network.lookup(route):{refs:[]};
  const directed=route.roadSteps?.length?await driving.lookup(route):null;
  const result=await rail({...route,independentRoadReferences:pinned.refs,
   drivingReferences:directed?.refs,displayRoadReferences:pinned.displayRoads,forkContext:pinned.forkContext},row);
  const surface=result.surfaceParts?{...result,parts:result.surfaceParts}:result;
  const cleaned=RouteLed.cleanup(surface);
  if(ordinal===138){
   assert(directed.refs.some(ref=>ref.name==='St Clements Way'&&ref.oneway==='yes'));
   assert.equal(route.parts.length,2);
   assert.equal(cleaned.parts.length,1);
   assert.deepEqual(cleaned.parts[0][0],route.parts[0][0]);
   assert.deepEqual(cleaned.parts[0].at(-1),route.parts[1].at(-1));
   assert(!containsLine(cleaned.parts,route.parts[0].slice(-4)));
   assert(containsLine(cleaned.parts,route.parts[1].slice(-30)));
   assert.equal(RouteLed.cleanup({...surface,corrected:true}).parts.length,2);
  }
  if(ordinal===150){
   assert(cleaned.centrelinePromotions.some(run=>run.kind==='offset-road-turn'&&
    run.road==="St James's Road / St James's Avenue"));
   assert(metres(cleaned.parts[0].at(-1),route.recorded[0].at(-1))<1);
  }
  if(ordinal===152){
   const [marker]=result.railTransitions;
   assert.equal(marker.kind,'arrival-retrace');
   assert.deepEqual(surface.parts[0][0],marker.point);
   assert.deepEqual(surface.parts[0], [marker.point,...route.parts[0].slice(15)]);
   const withoutRail=await rail({...route,independentRoadReferences:pinned.refs},{...row,railBefore:false});
   assert.equal(withoutRail.surfaceParts,undefined);
  }
  if(ordinal===158){
   assert(pinned.refs.some(ref=>ref.kind==='trunk'&&ref.name==='Great Chertsey Road'));
   assert(pinned.refs.some(ref=>ref.kind==='footway'));
   assert(!cleaned.centrelinePromotions.some(run=>run.road==='Great Chertsey Road'));
   assert.equal(cleaned.parts.length,1);
   assert.deepEqual(cleaned.parts[0][0],route.parts[0][0]);
   assert.deepEqual(cleaned.parts[0].at(-1),route.parts[0].at(-1));
   assert(containsLine(cleaned.parts,route.parts[0].slice(180,215)));
  }
  assert.equal(JSON.stringify(route),original);
 }
});
