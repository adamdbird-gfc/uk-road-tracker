const assert=require('node:assert/strict');
const {test}=require('node:test');
const RouteLed=require('../route-led-coverage.js');
const RouteFirst=require('../route-first.js');
const p=(x,y=0)=>[x/111320,y/111320];

function parkImport(){
 const entrance=p(0,40),interior=p(45,80),exit=p(70),returnGate=p(30);
 const approach=[entrance,interior,p(70,30),exit];
 const street=[exit,p(45),p(45,-24),p(45),returnGate];
 const parkReturn=[returnGate,p(30,35),entrance];
 const feature=(kind,coordinates)=>({properties:{reference_kind:kind},
  geometry:{type:'LineString',coordinates}});
 const journey={mode:'walking',source:{type:'timeline_import'},
  route_geometry:{type:'LineString',coordinates:[entrance,interior,entrance]},
  processing_result:{
   geojson:{features:[feature('',approach.concat(street.slice(1),parkReturn.slice(1)))]},
   road_geojson:{features:[feature('footway',approach),feature('residential',[exit,returnGate]),
    feature('residential',[p(45),p(45,-24)]),feature('path',parkReturn)]}
  }};
 return {journey,approach,street,parkReturn};
}

test('sparse Timeline loop retains independent paths and withholds unsupported street coverage',()=>{
 const {journey,approach,parkReturn}=parkImport();
 const original=JSON.stringify(journey);
 const route=RouteFirst.reconstruct(journey);
 const result=RouteLed.accumulate(RouteLed.createLedger(),route);
 assert.deepEqual(RouteLed.cleanup(route).parts,[approach,parkReturn]);
 const endpointsOnly={...route,recorded:[[approach[0],parkReturn.at(-1)]]};
 assert.deepEqual(RouteLed.cleanup(endpointsOnly).parts,[approach,parkReturn]);
 assert.deepEqual(result.held,[{reason:'unverified-road-excursion-between-pedestrian-observations',metres:88}]);
 assert(result.novel.every(({a,b})=>a[1]>=0&&b[1]>=0&&(a[1]>0||b[1]>0)));
 assert.deepEqual(result.links,[],'no connector is drawn across the withheld excursion');
 const laterStreet=RouteLed.accumulate(result.next,{parts:[[p(45),p(45,-24)]]});
 assert(laterStreet.novel.length>0&&laterStreet.repeat.length===0,'withheld street never enters the ledger');
 assert.equal(JSON.stringify(journey),original);
});

test('a genuine exit and an observed street excursion retain their coverage',()=>{
 const {journey,approach}=parkImport();
 const streetFix=p(45,-24);
 const loop=RouteFirst.reconstruct(journey);
 const observed={...loop,recorded:[[approach[0],approach[1],streetFix,approach[0]]]};
 assert.deepEqual(RouteLed.cleanup(observed).parts,loop.parts);
 const through={...loop,parts:[approach.concat(p(45),[streetFix])],
  recorded:[[approach[0],approach[1],streetFix]]};
 assert.deepEqual(RouteLed.cleanup(through).parts,through.parts);
 const withoutStreetObservation={...through,recorded:[[approach[0],approach[1]]]};
 assert.deepEqual(RouteLed.cleanup(withoutStreetObservation).parts,through.parts);
});

test('local pedestrian observations isolate an unsupported mirrored detour after a genuine road approach',()=>{
 const approach=[p(-100,80),p(-50,80)];
 const pathIn=[approach.at(-1),p(0,45),p(0)];
 const outbound=[p(0),p(60),p(100)];
 const spur=[p(100),p(100,-35),p(100)];
 const pathOut=[p(0),p(10,45),p(70,45)];
 const line=approach.concat(pathIn.slice(1),outbound.slice(1),spur.slice(1),
  outbound.toReversed().slice(1),pathOut.slice(1));
 const route={onFoot:true,source:'saved match',sourceType:'timeline_import',parts:[line],
  recorded:[[approach[0],pathIn[1],pathOut.at(-1)]],
  refs:[{kind:'tertiary',coords:approach},{kind:'footway',coords:pathIn},
   {kind:'tertiary',coords:outbound},{kind:'footway',coords:spur},{kind:'path',coords:pathOut}]};
 const result=RouteLed.cleanup(route);
 assert.deepEqual(result.parts,[approach.concat(pathIn.slice(1)),pathOut]);
 assert.deepEqual(result.held,[{
  reason:'unverified-road-excursion-between-pedestrian-observations',metres:270
 }]);
 const withVisit={...route,recorded:[[approach[0],pathIn[1],spur[1],pathOut.at(-1)]]};
 assert.deepEqual(RouteLed.cleanup(withVisit).parts,[line]);
});

test('loop rule requires independent path anchors and leaves corrections and native captures alone',()=>{
 const {journey}=parkImport();
 const route=RouteFirst.reconstruct(journey);
 for(const changed of [
  {...route,refs:route.refs.filter(ref=>ref.kind==='residential')},
  {...route,recorded:[]},
  {...route,sourceType:'android_activity_capture'},
  {...route,corrected:true}
 ]) assert.deepEqual(RouteLed.cleanup(changed).parts,route.parts);
});
test('ordered road-to-pedestrian-shortcut-to-road geometry is all retained',()=>{
 const parts=[[p(0),p(16),p(20,4),p(24,8),p(28,4),p(32),p(48)]];
 const original=JSON.stringify(parts);
 const coverage=RouteLed.accumulate(new Set(),{parts});
 assert(coverage.novel.length>=8,'all legs of the short source route are represented');
 assert(coverage.novel.some(x=>x.a[1]>0||x.b[1]>0),
  'short zig-zag pedestrian section is not silently filtered');
 assert(coverage.novel.some(x=>x.a[0]<p(10)[0]));
 assert(coverage.novel.some(x=>x.b[0]>p(40)[0]));
 assert.equal(JSON.stringify(parts),original,'saved geometry remains untouched');
});
test('bidirectional revisit remains one cumulative source identity',()=>{
 const forwards={parts:[[p(0),p(8),p(16)]]};
 const backwards={parts:[[p(16),p(8),p(0)]]};
 const first=RouteLed.accumulate(new Set(),forwards);
 const second=RouteLed.accumulate(first.next,backwards);
 assert.equal(second.novel.length,0);
 assert.equal(second.repeat.length,first.novel.length);
});
test('physical road overlap is independent of direction, vertices and partial coverage',()=>{
 const ref={kind:'primary',nodes:[5,2,7],coords:[p(0),p(25),p(50)]};
 const route=parts=>({parts,independentRoadReferences:[ref]});
 const morning=RouteLed.accumulate(RouteLed.createLedger(),route([[p(10),p(32),p(37)]]));
 const evening=RouteLed.accumulate(morning.next,route([[p(45),p(31),p(26),p(11),p(5)]]));
 const length=parts=>parts.reduce((total,{a,b})=>total+111320*Math.abs(b[0]-a[0]),0);
 assert(Math.abs(length(evening.repeat)-27)<.01,'only the actually shared distance repeats');
 assert(Math.abs(length(evening.novel)-13)<.01,'the two real extensions remain new');
 assert(evening.repeat.every(({a,b})=>a[0]>=b[0]),'preserve the direction of the current walk');
 const near=RouteLed.accumulate(morning.next,route([[p(37,2),p(10,2)]]));
 assert.equal(near.repeat.length,0,'a parallel path outside the reference is not merged');
 assert(Math.abs(length(near.novel)-27)<.01);
 assert(Math.abs(length(RouteLed.accumulate(evening.next,route([[p(6),p(43)]])).repeat)-37)<.01);
 assert(Math.abs(length(RouteLed.accumulate(morning.next,route([[p(6),p(43)]])).repeat)-27)<.01,
  'processing a later walk does not alter prior history');
});
test('distinct branches are not deduplicated just because they share a junction',()=>{
 const first=RouteLed.accumulate(new Set(),{parts:[[p(0),p(20)]]});
 const second=RouteLed.accumulate(first.next,{parts:[[p(20),p(20,20)]]});
 assert(second.novel.length>0);
});
test('manual break remains two disconnected source parts',()=>{
 const parts=[[p(0),p(8)],[p(24),p(32)]];
 const intervals=RouteLed.intervals({parts});
 assert(!intervals.some(x=>x.a[0]<p(24)[0]&&x.b[0]>p(8)[0]));
 assert(intervals.length>=2,'both separated parts remain present even with floating-point boundary splits');
});
test('long unsupported straight-line gap cannot earn source-led coverage',()=>{
 const attributed=RouteLed.accumulate(new Set(),{parts:[[p(0),p(700)]]});
 assert.equal(attributed.novel.length,0);
});

test('sparse driving replay repairs a service-road shortcut using directed roundabout edges',()=>{
 const a=p(0),b=p(80),ring=[p(15,20),p(30,37),p(50,37),p(66,20)];
 const service=[a,p(20,-20),p(43,-22),p(63,-17),b];
 const saved=[p(-20),...service,p(100)];
 const ref=(kind,coords,nodes,junction)=>({kind,coords,nodes,junction,oneway:'yes'});
 const refs=[
  ref('secondary',[p(-20),a,ring[0]],[1,2,3]),
  ref('primary',ring,[3,4,5,6],'roundabout'),
  ref('primary',[ring[3],b,p(100)],[6,7,8]),
  ref('service',service,[2,9,10,11,7]),
 ];
 const route={onFoot:false,source:'saved match',sourceType:'timeline_import',
  roadSteps:[service],recorded:[[p(-20),p(100)]],parts:[saved],drivingReferences:refs};
 const replay=RouteLed.cleanup(route);
 assert.deepEqual(replay.parts,[[p(-20),a,...ring,b,p(100)]]);
 assert.equal(replay.centrelinePromotions[0].kind,'driving-roundabout');
 assert.deepEqual(RouteLed.cleanup({...route,recorded:[[p(-20),p(43,-22),p(100)]]}).parts,[saved]);
 assert.deepEqual(RouteLed.cleanup({...route,corrected:true}).parts,[saved]);
 const wrongWay=ref('primary',ring.toReversed(),[6,5,4,3],'roundabout');
 assert.deepEqual(RouteLed.cleanup({...route,drivingReferences:[refs[0],wrongWay,...refs.slice(2)]}).parts,[saved]);
});

test('a redundant walking start hook can stop at the original fix projected onto the same road',()=>{
 const road={kind:'residential',coords:[p(0),p(100)],nodes:[1,2]};
 const line=[p(25,6),p(12,6),p(0,6),p(0),p(10),p(25),p(50),p(100)];
 const route={onFoot:true,source:'saved match',sourceType:'timeline_import',parts:[line],
  recorded:[[p(25,6),p(100)]],independentRoadReferences:[road],refs:[]};
 const output=RouteLed.cleanup(route);
 assert.deepEqual(output.parts,[[p(25),p(50),p(100)]]);
 assert.equal(output.held[0].reason,'redundant-pedestrian-start-hook');
 assert.deepEqual(RouteLed.cleanup({...route,recorded:[[p(25,6),p(0,6),p(100)]]}).parts,[line]);
 assert.deepEqual(RouteLed.cleanup({...route,recorded:[[p(25,12),p(100)]]}).parts,[line]);
});

test('saved driving step restores a long matched edge and leaves split or corrected gaps open',()=>{
 const feature=(coordinates,distance_m)=>({geometry:{type:'LineString',coordinates},
  properties:{distance_m}});
 const journey={mode:'driving',processing_result:{
  geojson:{features:[feature([p(0),p(20)],20),feature([p(4000),p(4566),p(4590)],590)]},
  road_geojson:{features:[feature([p(4566),p(4000)],566)]}
 }};
 const route=RouteFirst.reconstruct(journey);
 const result=RouteLed.accumulate(new Set(),route);
 const metres=result.novel.reduce((sum,{a,b})=>sum+(b[0]-a[0])*111320,0);
 assert(Math.abs(metres-610)<.01);
 assert.equal(RouteLed.intervals(route).length,result.novel.length);
 assert(result.novel.every(({a,b})=>b[0]<=p(20)[0]||a[0]>=p(4000)[0]));
 const measure=record=>RouteLed.accumulate(new Set(),RouteFirst.reconstruct(record)).novel
  .reduce((sum,{a,b})=>sum+(b[0]-a[0])*111320,0);
 assert(Math.abs(measure({...journey,journey_corrections:{removed_matched_segments:[1]}})-44)<.01);
 assert(Math.abs(measure({...journey,mode:'walking'})-44)<.01);
 for(const step of [feature([p(4000),p(4566)],0),feature([p(4010),p(4566)],556)])
  assert(Math.abs(measure({...journey,processing_result:{...journey.processing_result,
   road_geojson:{features:[step]}}})-44)<.01);
});

test('source-backed road-parallel out-and-back is consolidated without deleting the through road',()=>{
 const points=[p(120,-6),p(120),p(90),p(60),p(40),p(40,-5),p(60,-5),
  p(60.3,-5),p(60,-5),p(40,-5),p(40),p(20),p(0)];
 const route={parts:[points],recorded:[[p(150,15),p(60,-5),p(0)]],
  refs:[{kind:'tertiary',coords:[p(0),p(120)]},
   {kind:'residential',coords:[p(120,-6),p(120)]},
   {kind:'footway',coords:[p(40,-5),p(60,-5)]}]};
 const before=JSON.stringify(route);
 const prepared=RouteLed.cleanup(route);
 assert(prepared.held.some(x=>x.reason==='parallel-roadside-return'));
 assert(prepared.held.some(x=>x.reason==='unverified-short-road-entrance'));
 assert(prepared.parts[0].every(x=>Math.abs(x[1])<1e-10),
  'no redundant roadside return or unverifiable entrance earns separate physical geometry');
 assert.equal(JSON.stringify(route),before,'original matched geometry and sources never changed');
});
test('genuine road-to-shortcut-to-road journey stays intact even with road refs',()=>{
 const points=[p(0),p(18),p(24,5),p(30,9),p(36,5),p(42),p(60)];
 const route={parts:[points],recorded:[[p(0),p(30,9),p(60)]],
  refs:[{kind:'residential',coords:[p(0),p(18)]},
   {kind:'footway',coords:[p(18),p(24,5),p(30,9),p(36,5),p(42)]},
   {kind:'residential',coords:[p(42),p(60)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,route.parts);
 const attributed=RouteLed.accumulate(new Set(),route);
 assert(attributed.novel.some(x=>x.a[1]>0||x.b[1]>0),'genuine independent footway is credited');
});
test('real out-and-back on an independent perpendicular footpath is not treated as redundant road',()=>{
 const points=[p(0),p(20),p(20,20),p(20),p(40)];
 const route={parts:[points],recorded:[[p(0),p(20,20),p(40)]],
  refs:[{kind:'residential',coords:[p(0),p(40)]},
   {kind:'footway',coords:[p(20),p(20,20)]}]};
 assert.equal(RouteLed.cleanup(route).held.length,0);
});
test('confirmed journey start off the road is retained even if a short entrance',()=>{
 const points=[p(120,-6),p(120),p(90),p(60),p(30)];
 const route={parts:[points],recorded:[[p(120,-6),p(90),p(30)]],
  refs:[{kind:'tertiary',coords:[p(0),p(120)]},
   {kind:'residential',coords:[p(120,-6),p(120)]}]};
 assert.equal(RouteLed.cleanup(route).held.length,0,'recorded GPS at side-street entrance overrides cleanup');
});
test('missing reference geometry cannot justify trimming saved route',()=>{
 const route={parts:[[p(120,-6),p(120),p(90),p(60)]],recorded:[[p(160,20)]],refs:[]};
 assert.equal(RouteLed.cleanup(route).held.length,0);
});


test('sparse destination GPS fixes main-road approach, not unsupported side-road end nib',()=>{
 const line=[p(0),p(20),p(45),p(67),p(80),p(80,2),p(80,7),p(80,14)];
 const route={parts:[line],recorded:[[p(0),p(30),p(50),p(67)]],
  refs:[{kind:'tertiary',coords:[p(0),p(80)]},
   {kind:'residential',coords:[p(80),p(80,14)]}]};
 const original=JSON.stringify(route),result=RouteLed.cleanup(route);
 assert(result.held.some(x=>x.reason==='unverified-terminal-road-turn'));
 assert.deepEqual(result.parts[0].at(-1),p(67),'the last recorded location, not a junction, is the credited endpoint');
 assert.equal(result.parts[0].length,4,'the unsupported approach extension and side-road tail are both withheld');
 assert(result.held[0].metres>20,'withheld distance includes unsupported approach after the final fix');
 assert.equal(JSON.stringify(route),original,'original matched history never altered');
});
test('last fix between matched road vertices ends partway along that road',()=>{
 const line=[p(0),p(20),p(45),p(67),p(80),p(80,2),p(80,7),p(80,14)];
 const route={parts:[line],recorded:[[p(0),p(30),p(50),p(70,1)]],
  refs:[{kind:'tertiary',coords:[p(0),p(80)]},
   {kind:'residential',coords:[p(80),p(80,14)]}]};
 const result=RouteLed.cleanup(route);
 assert(result.held.some(x=>x.reason==='unverified-terminal-road-turn'));
 const end=result.parts[0].at(-1);
 assert(Math.abs(end[0]-p(70)[0])<1e-7&&Math.abs(end[1])<1e-9,
  'endpoint projected onto physical road where last position was recorded');
 assert(end[0]<p(80)[0],'no automatic extension to the nearest junction');
});
test('no credible last fix on approach means do not fabricate a shorter route',()=>{
 const line=[p(0),p(20),p(45),p(67),p(80),p(80,2),p(80,7),p(80,14)];
 const route={parts:[line],recorded:[[p(0),p(30),p(50),p(68,9)]],
  refs:[{kind:'tertiary',coords:[p(0),p(80)]},
   {kind:'residential',coords:[p(80),p(80,14)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,[line],'ambiguous GPS cannot establish a new endpoint');
});
test('destination fix on side road prevents terminal trim',()=>{
 const line=[p(0),p(20),p(45),p(67),p(80),p(80,2),p(80,7),p(80,14)];
 const route={parts:[line],recorded:[[p(0),p(67),p(80,14)]],
  refs:[{kind:'tertiary',coords:[p(0),p(80)]},
   {kind:'residential',coords:[p(80),p(80,14)]}]};
 assert.equal(RouteLed.cleanup(route).held.length,0);
});
test('continuous straight road ending beyond GPS fix is not blindly truncated',()=>{
 const line=[p(0),p(20),p(45),p(67),p(80),p(95)];
 const route={parts:[line],recorded:[[p(0),p(67)]],
  refs:[{kind:'tertiary',coords:[p(0),p(100)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,[line]);
});
test('real path turn to a separate pedestrian route is not cut as an end nib',()=>{
 const line=[p(0),p(20),p(45),p(67),p(80),p(80,5),p(80,10),p(80,14)];
 const route={parts:[line],recorded:[[p(0),p(67)]],
  refs:[{kind:'tertiary',coords:[p(0),p(80)]},
   {kind:'footway',coords:[p(80),p(80,14)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,[line]);
});

test('ZIP UI keeps canonical and route-led models isolated',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.includes('route-led-coverage.js'));
 assert(page.includes('RouteLedCoverage.accumulate(zipRouteLedLedger,route.surfaceParts?{...route,parts:route.surfaceParts}:route)'));
 assert(page.includes("mode==='routeLed'?zipRouteLedCache.get(n):zipCoverageCache.get(n)"));
 assert(page.includes("zipRouteLedCache.clear();zipRouteLedLedger.clear()"));
 assert(page.includes('LAB r472')&&page.includes('>PR #472</a>'));
 assert(page.includes('id="zipCompare"')&&page.includes('id="zipModeControls" hidden')&&page.includes("mode=zip?'routeLed':'center'"));
});

test('detached roadside duplicate needs a continuing travelled road and no endpoint or crossing evidence',()=>{
 const before=[p(0),p(20)],after=[p(20),p(40)],island=[p(14,6),p(26,6)];
 const road={kind:'residential',coords:[p(0),p(40)]};
 const path={kind:'footway',coords:island};
 const source={parts:[before,island,after],refs:[road,path]};
 const cleaned=RouteLed.cleanup(source);
 assert.deepEqual(cleaned.parts,[before,after]);
 assert.equal(cleaned.held[0].reason,'detached-roadside-duplicate');
 assert.deepEqual(RouteLed.cleanup({...source,recorded:[[p(14,6),p(26,6)]]}).parts,source.parts);
 assert.deepEqual(RouteLed.cleanup({...source,corrected:true}).parts,source.parts);
 assert.deepEqual(RouteLed.cleanup({...source,refs:[road,{...path,footway:'crossing'}]}).parts,source.parts);
 assert.deepEqual(RouteLed.cleanup({...source,refs:[path]}).parts,source.parts);
});

test('short junction reversal is withheld unless supported by a fix or saved mapped crossing',()=>{
 const line=[p(0),p(20),p(20,5),p(20),p(35),p(50)];
 const road={kind:'residential',coords:[p(0),p(50)]};
 const base={onFoot:true,source:'saved match',parts:[line],refs:[road]};
 const result=RouteLed.cleanup(base);
 assert.deepEqual(result.parts,[[p(0),p(20),p(35),p(50)]]);
 assert.equal(result.held[0].reason,'short-junction-return');
 const recorded=[[p(0),p(20,5),p(50)]];
 assert.deepEqual(RouteLed.cleanup({...base,recorded}).parts,[line]);
 const crossing={kind:'footway',footway:'crossing',coords:[p(20),p(20,8)]};
 assert.deepEqual(RouteLed.cleanup({...base,refs:[road,crossing]}).parts,[line]);
 assert.deepEqual(RouteLed.cleanup({...base,corrected:true}).parts,[line]);
});

test('a weak final path nib at a previously travelled road junction is withheld',()=>{
 const road={kind:'tertiary',coords:[p(0),p(100)]};
 const path={kind:'cycleway',coords:[p(35),p(35,-30)]};
 const parts=[[p(0),p(35),p(75),p(60),p(35),p(35,-7)]];
 const route={onFoot:true,source:'saved match',parts,refs:[road,path],recorded:[[p(0),p(75),p(30,-7)]]};
 assert.deepEqual(RouteLed.cleanup(route).parts[0].at(-1),p(35));
 assert.deepEqual(RouteLed.cleanup({...route,recorded:[[p(0),p(75),p(35,-7)]]}).parts,parts);
 assert.deepEqual(RouteLed.cleanup({...route,corrected:true}).parts,parts);
 const noRevisit=[[p(80),p(60),p(50),p(35),p(35,-7)]];
 assert.deepEqual(RouteLed.cleanup({...route,parts:noRevisit}).parts,noRevisit);
});
