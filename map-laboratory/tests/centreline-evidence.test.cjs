const assert=require('node:assert/strict'),{test}=require('node:test');
const Centreline=require('../roadside-centreline.js');
const RouteLed=require('../route-led-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const road=(coords,name='Physical road')=>({kind:'residential',name,coords});

test('real-world evidence reports missing physical network instead of claiming promotion',()=>{
 const source={parts:[[p(0,5),p(40,5),p(80,5),p(120,5)]],refs:[
  {kind:'footway',coords:[p(0,5),p(120,5)]}]};
 const proof=Centreline.evidence(source);
 assert.equal(proof.references,0);
 assert(proof.reasons.some(x=>x.reason==='no physical road references'&&x.metres>=115));
 assert.equal(Centreline.align(source).promoted.length,0);
});

test('near but too distant parallel geometry produces a measurable rejection',()=>{
 const source={parts:[[p(0,20),p(35,20),p(70,20),p(105,20)]],
  refs:[road([p(0),p(120)],'Main physical road')]};
 const proof=Centreline.evidence(source);
 assert.equal(proof.references,1);
 assert(proof.reasons.some(x=>x.reason==='physical road farther than 15 m'));
 assert.equal(Centreline.align(source).promoted.length,0);
});

test('the same physical corridor with actual road network can move source and report qualifying legs',()=>{
 const source={parts:[[p(0,10),p(35,10),p(70,10),p(105,10)]],
  refs:[road([p(0),p(120)],'Main physical road')]};
 const evidence=Centreline.evidence(source),result=Centreline.align(source);
 assert(evidence.reasons.some(x=>x.reason==='segment passes individual eligibility checks'));
 assert.equal(result.promoted.length,1);
 assert(result.parts[0].every(v=>Math.abs(v[1])<1e-10));
 assert.equal(evidence.roads[0].name,'Main physical road');
});

test('genuine separate pedestrian shortcut remains untouched and evidence does not imply permission to snap',()=>{
 const path=[p(0),p(20),p(30,10),p(40,18),p(50,10),p(60),p(100)];
 const source={parts:[path],refs:[road([p(0),p(100)]),
  {kind:'footway',coords:path}]};
 const original=JSON.stringify(source);
 assert.deepEqual(RouteLed.cleanup(source).parts,[path]);
 assert.equal(JSON.stringify(source),original);
});

test('historical cumulative replay does not eagerly analyse thousands of journey references',()=>{
 const route={parts:[[p(0,10),p(35,10),p(70,10),p(105,10)]],
  refs:[road([p(0),p(120)])]};
 const background=RouteLed.accumulate(new Set(),route);
 assert.equal(background.roadEvidence,null);
 const inspected=RouteLed.accumulate(new Set(),{...route,diagnosticsRequested:true});
 assert(inspected.roadEvidence?.references>0);
 assert.deepEqual(RouteLed.evidence(route).reasons,inspected.roadEvidence.reasons);
});
test('evidence summary is present in visible current journey status without a new button or cross-journey input',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(html.includes('LAB r460')&&html.includes('>PR #460</a>'));
 assert(html.includes('metres aligned to road centrelines'));
 assert(html.includes('roadEvidence:routed.roadEvidence'));
 assert(html.includes("diagnosticRoute.source+' · centreline '+shifted+'m'"));
 assert(html.includes('road reference ways screened'));
 assert(html.includes('networkStatus:route.networkStatus'));
 assert(!html.includes('SharedRoadCatalogue')&&!html.includes('sharedRoadReferences'));
 assert(!html.includes('id="diagnosticButton"'));
});
