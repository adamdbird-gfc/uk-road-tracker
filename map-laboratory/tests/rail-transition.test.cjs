const assert=require('node:assert/strict');
const {test}=require('node:test');
const {apply,create}=require('../rail-transition.js');
const {roadReplay}=require('../bulk-diagnostics.js');

const p=(x,y=0)=>[x/111320,y/111320];
const entrance={id:2,point:p(20)};
const surface={coords:[p(0),p(20)],nodes:[1,2]};
const indoor={coords:[p(20),p(22,8),p(22,17)],nodes:[2,3,4],indoor:'yes'};
const line=[p(0),p(20),p(22,8),p(22,17),p(35,15)];
const route={onFoot:true,source:'saved match',parts:[line],independentRoadReferences:[surface,indoor]};

test('a timed rail transition clips only the display at the connected mapped door',()=>{
 const result=apply(route,{railAfter:true},[entrance]);
 assert.deepEqual(result.surfaceParts,[[p(0),p(20)]]);
 assert.equal(result.railTransitions[0].label,'Likely transfer to rail');
 assert.strictEqual(result.parts,route.parts);
 const arriving={...route,parts:[[...line].reverse()]};
 assert.deepEqual(apply(arriving,{railBefore:true},[entrance]).surfaceParts,[[p(20),p(0)]]);
});

test('surface passers-by, ambiguous doors, corrections and disconnected topology keep their route',()=>{
 assert.strictEqual(apply(route,{},[entrance]),route);
 assert.strictEqual(apply(route,{railAfter:true},[entrance,entrance]),route);
 const corrected={...route,corrected:true};
 assert.strictEqual(apply(corrected,{railAfter:true},[entrance]),corrected);
 const disconnected={...route,independentRoadReferences:[surface,{...indoor,nodes:[99,3,4]}]};
 assert.strictEqual(apply(disconnected,{railAfter:true},[entrance]),disconnected);
 const passing={...route,parts:[[p(0),p(20),p(35),p(50)]]};
 assert.strictEqual(apply(passing,{railAfter:true},[entrance]),passing);
});

test('a mirrored arrival excursion with a repeated Timeline observation can be left unresolved',()=>{
 const out=[p(0),p(10),p(20),p(35),p(45),p(60)];
 const parts=[out.concat(out.slice(0,-1).toReversed(),[p(-15),p(-35)])];
 const individual={...route,parts,recorded:[[p(0),p(60),p(0),p(-35)]],
  sourceType:'timeline_import'};
 const replay=apply(individual,{railBefore:true},[entrance]);
 assert.deepEqual(replay.surfaceParts,[[p(0),p(-15),p(-35)]]);
 assert.equal(replay.railTransitions[0].kind,'arrival-retrace');
 assert.strictEqual(apply(individual,{},[entrance]),individual);
 assert.strictEqual(apply({...individual,recorded:[[p(0),p(60),p(15),p(-35)]]},
  {railBefore:true},[entrance]).surfaceParts,undefined);
});

test('the replay uses adjoining rail mode and time without reading its track',()=>{
 const at=(minute,seconds='00')=>'2026-01-01T12:'+minute+':'+seconds+'Z';
 const archive={journeys:[
  {mode:'walking',started_at:at('00'),ended_at:at('04')},
  {mode:'train',started_at:at('04','30'),ended_at:at('25')},
  {mode:'walking',started_at:at('26'),ended_at:at('30')},
  {mode:'train',started_at:at('43'),ended_at:at('51')},
  {mode:'walking',started_at:at('55'),ended_at:at('59')},
 ],read(){assert.fail('private track used as context');}};
 const view=roadReplay(archive);
 assert.deepEqual(view.journeys.map(j=>[j.archiveOrdinal,j.railBefore,j.railAfter]),
  [[1,false,true],[3,true,false],[5,false,false]]);
});

test('entrance lookup is cached and unavailable data leaves the route as it was',async()=>{
 let calls=0;
 const transition=create(async()=>{calls++;return new Response('',{status:503});});
 assert.strictEqual(await transition(route,{railAfter:true}),route);
 assert.strictEqual(await transition(route,{railBefore:true}),route);
 assert.equal(calls,1);
});
