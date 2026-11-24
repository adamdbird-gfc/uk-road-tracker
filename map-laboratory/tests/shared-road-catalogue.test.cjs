const assert=require('node:assert/strict'),{test}=require('node:test');
const Source=require('../shared-road-catalogue.js'),RouteLed=require('../route-led-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const reference=(kind,coords,name='')=>({type:'Feature',properties:{reference_kind:kind,name},geometry:{type:'LineString',coordinates:coords}});
const record=features=>({journey:{processing_result:{road_geojson:{type:'FeatureCollection',features}}}});
test('road centreline from a later journey restores an earlier reference-poor walking route',async()=>{
 const earlier={parts:[[p(0,5),p(40,5),p(80,5),p(120,5),p(160,5),p(200,5)]],
  refs:[{kind:'footway',coords:[p(0,5),p(200,5)]}]};
 assert(Source.needsSupplement(earlier),'sustained unreferenced road-looking segment merits catalogue search');
 const catalog=await Source.fromZip({journeys:[1,2,3],read:async i=>
  i===2?record([reference('unclassified',[p(0),p(200)],'A road')]):record([])});
 const refs=Source.nearby(earlier,catalog);
 assert.equal(refs.length,1);
 const original=JSON.stringify(earlier);
 const baseline=RouteLed.accumulate(new Set(),earlier);
 assert(baseline.novel.some(x=>Math.abs(x.a[1])>0));
 const rebuilt=RouteLed.accumulate(new Set(),{...earlier,sharedRoadReferences:refs});
 assert(rebuilt.novel.length>0&&rebuilt.novel.every(x=>Math.abs(x.a[1])<1e-9&&Math.abs(x.b[1])<1e-9),
  'reconstructed cumulative intervals follow physical centreline from existing ZIP');
 assert.equal(JSON.stringify(earlier),original,'no mutation of original saved route or history');
});
test('same road exported in reverse or under another kind never becomes competing alternatives',()=>{
 const a=reference('unclassified',[p(0),p(200)]);
 const b=reference('residential',[p(200),p(0)]);
 const map=Source.collect(record([a,b]),new Map());
 assert.equal(map.size,1);
 assert.equal(Source.merge([...map.values()],[{kind:'footway',coords:[p(0),p(200)]}]).length,2,
  'independent footway evidence remains separate from physical road catalogue');
});
test('independent pedestrian shortcut remains intact even when full road catalogue exists',()=>{
 const line=[p(0),p(20),p(30,8),p(40,16),p(50,8),p(60),p(100)];
 const route={parts:[line],refs:[{kind:'footway',coords:[p(20),p(30,8),p(40,16),p(50,8),p(60)]}],
  sharedRoadReferences:[{kind:'residential',coords:[p(0),p(20)]},{kind:'residential',coords:[p(60),p(100)]}]};
 const reconstructed=RouteLed.cleanup(route);
 assert.deepEqual(reconstructed.parts,[line],'true shortcut is not snapped to a nearby road');
});
test('a separate square path does not shift to a road added from elsewhere',()=>{
 const line=[p(0,18),p(10,18),p(20,28),p(35,28),p(45,20),p(55,20)];
 const route={parts:[line],refs:[{kind:'footway',coords:line}],
  sharedRoadReferences:[{kind:'unclassified',coords:[p(0),p(55)]}]};
 assert.deepEqual(RouteLed.cleanup(route).parts,[line]);
});
test('different-location shared road geometry cannot change the local journey',()=>{
 const line=[p(0,5),p(40,5),p(80,5),p(120,5)];
 const route={parts:[line],refs:[]};
 const catalog=[{kind:'residential',coords:[p(500),p(650)]}];
 assert.equal(Source.nearby(route,catalog).length,0);
 assert.deepEqual(RouteLed.cleanup(route).parts,[line]);
});
test('an incomplete source reference scan does not fetch or rematch externally',async()=>{
 let read=0,progress=0;
 const catalog=await Source.fromZip({journeys:[1,2],read:async()=>{
  read++;return record([reference('unclassified',[p(0),p(60)])]);
 }},()=>progress++);
 assert.equal(read,2);
 assert(progress>=1);
 assert.equal(catalog.length,1,'same physical road is deduplicated');
});
test('LAB r451 scan is visible and historical credit is rebuilt after discovering static network',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.includes('shared-road-catalogue.js'));
 assert(page.includes('SharedRoadCatalogue.needsSupplement(route)'));
 assert(page.includes('SharedRoadCatalogue.fromZip(diagnosticsZip'));
 assert(page.includes('index=-1;continue;'),'original chronological replay restarts after catalogue enrichment');
 assert(page.includes('zipRoadCatalogue=null;currentCoverageAudit=null;'));
 assert(page.includes('LAB r451')&&page.includes('>PR #451</a>'));
});
