const assert=require('node:assert/strict'),{test}=require('node:test');
const Network=require('../independent-road-network.js');
const Roadside=require('../roadside-centreline.js');
const RouteLed=require('../route-led-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const publicWay=(id,coords,highway='residential')=>({
 type:'way',id,tags:{highway,name:'Independent road'},
 geometry:coords.map(([lon,lat])=>({lon,lat}))
});
test('two independent long footway-side corridors are actually moved onto physical centrelines',()=>{
 const lines=[
  [p(0,11),p(35,11),p(70,11),p(105,11),p(140,11)],
  [p(200,12),p(235,12),p(270,12),p(305,12),p(340,12)]];
 const external=Network.parse({elements:[
  publicWay(1,[p(0),p(140)],'unclassified'),
  publicWay(2,[p(200),p(340)],'tertiary')
 ]});
 const route={parts:lines,refs:[{kind:'footway',coords:lines[0]},{kind:'footway',coords:lines[1]}]};
 const before=RouteLed.accumulate(new Set(),route);
 assert(before.novel.some(s=>Math.abs(s.a[1])>0),'source is visibly offset before correction');
 const result=RouteLed.accumulate(new Set(),{...route,independentRoadReferences:external});
 const shifted=result.centrelinePromotions.reduce((n,p)=>n+p.metres,0);
 assert(shifted>=260,'both corridor lengths, not just one point, are reassigned');
 assert(result.novel.length>0&&result.novel.every(s=>Math.abs(s.a[1])<1e-10&&Math.abs(s.b[1])<1e-10),
  'the visible cumulative map follows both physical roads');
});
test('a single long parallel leg on a wide road is moved, not rejected by old 8m cap',()=>{
 const route={parts:[[p(0,12),p(100,12)]],refs:[],
  independentRoadReferences:[{kind:'tertiary',coords:[p(0),p(100)]}]};
 const r=Roadside.align({...route,refs:route.independentRoadReferences});
 assert.equal(r.promoted.length,1);
 assert.deepEqual(r.parts[0],[p(0),p(100)]);
});
test('short off-road route near a parallel carriageway is not promoted',()=>{
 const source=[p(0,12),p(20,12),p(38,12)];
 const r=Roadside.align({parts:[source],refs:[{kind:'residential',coords:[p(0),p(80)]}]});
 assert.deepEqual(r.parts,[source]);
 assert.equal(r.promoted.length,0);
});
test('a real diagonal pedestrian shortcut remains separate even with external roads',()=>{
 const path=[p(0),p(20),p(30,8),p(40,16),p(50,8),p(60),p(100)];
 const route={parts:[path],refs:[{kind:'footway',coords:path}],
  independentRoadReferences:[{kind:'residential',coords:[p(0),p(100)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,[path]);
});
test('Community Square path geometry remains unchanged',()=>{
 const path=[p(0,18),p(10,18),p(20,28),p(35,28),p(45,20),p(55,20)];
 const route={parts:[path],refs:[{kind:'footway',coords:path}],
  independentRoadReferences:[{kind:'primary',coords:[p(0),p(55)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,[path]);
});
test('only physical highways from independent network qualify: exclude pubs, service, path',()=>{
 const roads=Network.parse({elements:[
  publicWay(1,[p(0),p(100)],'service'),publicWay(2,[p(0,4),p(100,4)],'footway'),
  publicWay(3,[p(0,11),p(100,11)],'primary'),publicWay(4,[p(0,11),p(100,11)],'residential'),
  {type:'node',lat:0,lon:0,tags:{highway:'tertiary'}}
 ]});
 assert.equal(roads.length,1,'no private service road or footway promoted; same physical line deduplicated');
});
test('independent lookup sends only bounded geographic rectangle, never journey points or ZIP',async()=>{
 const route={parts:[[p(0,12),p(35,12),p(70,12),p(105,12),p(140,12),p(175,12)]],refs:[]};
 assert(Network.needsNetwork(route));
 let called=false;
 const result=await Network.lookup(route,async(url,options)=>{
  called=true;
  assert(url.startsWith('https://overpass-api.de/api/interpreter?data='));
  assert(url.includes('data=')&&decodeURIComponent(url).includes('out geom;'));
  assert(!url.includes('journey_id')&&!url.includes('tracepoints'));
  assert(options.signal,'network call is bounded by a timeout');
  return {ok:true,json:async()=>({elements:[publicWay(1,[p(0),p(140)])]})};
 });
 assert(called&&result.length===1);
});
test('no local journey reference gap means no public query required',()=>{
 const line=[p(0,5),p(40,5),p(80,5),p(120,5),p(160,5)];
 const route={parts:[line],refs:[{kind:'residential',coords:[p(0),p(160)]}]};
 assert.equal(Network.needsNetwork(route),false);
});
test('a long route is bounded and never triggers a massive public map query',()=>{
 const line=[p(0),p(6000)];
 assert.equal(Network.bounds({parts:[line]}),null);
});
test('LAB r452 has no cross-journey reference dependency and exposes moved metres',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 const route=fs.readFileSync(path.join(__dirname,'..','route-led-coverage.js'),'utf8');
 assert(page.includes('independent-road-network.js'));
 assert(page.includes('IndependentRoadNetwork.lookup(source)'));
 assert(page.includes('metres aligned to road centrelines'));
 assert(!page.includes('SharedRoadCatalogue')&&!route.includes('SharedRoadReferenceSource'));
 assert(!page.includes('zipRoadCatalogue')&&!route.includes('sharedRoadReferences'));
 assert(page.includes('LAB r452')&&page.includes('>PR #452</a>'));
});
