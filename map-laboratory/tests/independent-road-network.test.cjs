const assert=require('node:assert/strict'),{test}=require('node:test');
const Network=require('../independent-road-network.js');
const Roadside=require('../roadside-centreline.js');
const RouteLed=require('../route-led-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const fs=require('node:fs'),path=require('node:path');
const root=path.join(__dirname,'..');
const tileFile=url=>fs.readFileSync(path.join(root,url));
test('two independent long footway-side corridors are actually moved onto physical centrelines',()=>{
 const lines=[
  [p(0,11),p(35,11),p(70,11),p(105,11),p(140,11)],
  [p(200,12),p(235,12),p(270,12),p(305,12),p(340,12)]];
 const external=[
  {kind:'unclassified',coords:[p(0),p(140)]},
  {kind:'tertiary',coords:[p(200),p(340)]}
 ];
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
test('pinned real OSM network is geographically selected and reused across lookups',async()=>{
 const calls=[];
 const provider=Network.create(async(url,options)=>{
  calls.push(url);
  assert(options.signal);
  assert.match(url,/^\.\/reference-network\/v1\/(r7-index\.json|-?\d+_-?\d+\.json\.gz)$/);
  return new Response(tileFile(url));
 });
 const route={parts:[[[0.36685,51.44165],[0.3675,51.44154]]]};
 const [one,two]=await Promise.all([provider.lookup(route),provider.lookup(route)]);
 assert.deepEqual(one,two);
 assert(one.refs.some(ref=>ref.name==='Clive Road'&&Network.isRoad(ref.kind)&&ref.nodes.length===ref.coords.length));
 assert(one.refs.some(ref=>ref.kind==='footway'));
 assert.equal(calls.filter(url=>url.endsWith('index.json')).length,1);
 assert.equal(calls.length,new Set(calls).size);
 const outside=await provider.lookup({parts:[[[1,52],[1.001,52]]]});
 assert.match(outside.status,/Outside pinned network/);
 assert.deepEqual(outside.refs,[]);
});
test('a corrupt tile retries once, falls back during cooldown and recovers in the same session',async()=>{
 const calls=[];
 let clock=0,healthy=false;
 const provider=Network.create(async (url,options)=>{
  calls.push([url,options.cache]);
  return new Response(url.endsWith('index.json')||healthy?tileFile(url):'broken');
 },undefined,()=>clock);
 const route={parts:[[[0.36685,51.44165],[0.3675,51.44154]]]};
 const first=await provider.lookup(route);
 assert.match(first.status,/tile unavailable/);
 assert.equal(first.unavailable,true);
 assert.deepEqual(first.refs,[]);
 assert.deepEqual(await provider.lookup(route),first);
 assert.equal(calls.length,3);
 assert.equal(calls.at(-1)[1],'reload');
 healthy=true;clock=16000;
 assert((await provider.lookup(route)).refs.length>0);
 assert.equal(calls.length,4);
 const noIndex=await Network.create(async()=>new Response('',{status:503})).lookup(route);
 assert.match(noIndex.status,/index unavailable/);
 assert.deepEqual(noIndex.refs,[]);
});
test('the same tile can be read after a retry when its bytes arrive as decoded JSON',async()=>{
 const {gunzipSync}=require('node:zlib');
 let attempts=0;
 const provider=Network.create(async url=>{
  const file=tileFile(url);
  if(url.endsWith('index.json'))return new Response(file);
  if(++attempts===1)return new Response('',{status:503});
  return new Response(gunzipSync(file));
 });
 const result=await provider.lookup({parts:[[[0.36685,51.44165],[0.3675,51.44154]]]});
 assert(result.refs.some(ref=>ref.name==='Clive Road'));
 assert.equal(attempts,2);
});
test('Pentonville Road references span a pinned tile boundary',async()=>{
 const calls=[];
 const provider=Network.create(async url=>{
  calls.push(url.split('/').at(-1));
  return new Response(tileFile(url));
 });
 const route={parts:[[[-0.121,51.53095],[-0.119,51.53118]]]};
 const result=await provider.lookup(route);
 assert.deepEqual(calls,['r7-index.json','-7_2576.json.gz','-6_2576.json.gz']);
 assert(result.refs.some(ref=>ref.name==='Pentonville Road'&&Network.isRoad(ref.kind)));
 assert(result.refs.some(ref=>ref.kind==='footway'));
 assert.equal(result.refs.length,new Set(result.refs.map(ref=>ref.id)).size);
 assert.deepEqual(await provider.lookup(route),result);
 const broken=Network.create(async url=>url.endsWith('-6_2576.json.gz')?
  new Response('',{status:503}):new Response(tileFile(url)));
 assert.deepEqual(await broken.lookup(route),{
  refs:[],status:'Pinned network tile unavailable · own references',unavailable:true
 });
});
test('merge retains explicit OSM tags and removes reverse duplicates of the same geometry',()=>{
 const external={kind:'footway',footway:'crossing',coords:[p(0),p(5)]};
 assert.deepEqual(Network.merge([{kind:'footway',coords:[p(5),p(0)]}],[external]),[external]);
 assert(!Network.isRoad('service'));
});
test('ZIP source uses the same public reference loader for historical and selected journeys',()=>{
 const page=fs.readFileSync(path.join(root,'index.html'),'utf8');
 assert(page.includes('independent-road-network.js'));
 assert(page.includes('const route=await loadZipRoute(index)'));
 assert(page.includes('metres aligned to road centrelines'));
 assert(!page.includes('SharedRoadCatalogue')&&!page.includes('zipIndependentByJourney'));
});

test('the Whitefriars Street centreline is pinned separately from routable references',async()=>{
 const provider=Network.create(async url=>new Response(tileFile(url)));
 const route={parts:[[[-0.10748,51.51412],[-0.10716,51.51335]]]};
 const result=await provider.lookup(route);
 assert.deepEqual(result.displayRoads.map(ref=>ref.id),[888781926,888781923,901407354]);
 assert(result.displayRoads.every(ref=>ref.access==='no'&&ref.displayOnly));
 assert(result.displayRoads.every(ref=>!result.refs.some(way=>way.id===ref.id)));
 assert.equal(result.refs.find(ref=>ref.id===1202405654).displayPavement,'Whitefriars Street');
});
