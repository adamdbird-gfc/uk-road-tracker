const assert=require('node:assert/strict'),{test}=require('node:test');
const Roadside=require('../roadside-centreline.js');
const RouteLed=require('../route-led-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const road=(coords,kind='residential')=>({kind,coords});
test('sustained parallel pavement follows physical road centreline in saved journey order',()=>{
 const line=[p(0,5),p(15,5),p(30,5),p(45,5),p(60,5),p(75,5)];
 const route={parts:[line],refs:[road([p(0),p(75)])]};
 const before=JSON.stringify(route);
 const result=Roadside.align(route);
 assert.equal(result.promoted.length,1);
 assert.equal(result.promoted[0].segments,5);
 assert(result.parts[0].every(x=>Math.abs(x[1])<1e-10),'all aligned vertices on physical centreline');
 assert.equal(JSON.stringify(route),before,'saved matched geometry untouched');
 const credited=RouteLed.accumulate(new Set(),route);
 assert(credited.novel.length>0&&credited.novel.every(x=>Math.abs(x.a[1])<1e-10&&Math.abs(x.b[1])<1e-10));
});
test('second separate pedestrian-to-road segment is promoted without naming any street',()=>{
 const lines=[[p(0,4),p(18,4),p(36,4),p(54,4)],
  [p(100,5),p(118,5),p(136,5),p(154,5)]];
 const refs=[road([p(0),p(54)]),road([p(100),p(154)])];
 const result=Roadside.align({parts:lines,refs});
 assert.equal(result.promoted.length,2);
 assert(result.parts.flat().every(x=>Math.abs(x[1])<1e-10));
});
test('a centreline shift must retain mapped pedestrian turns at both ends',()=>{
 const line=[p(0,18),p(0,12),p(0,5),p(20,5),p(40,5),p(60,5),p(80,5),
  p(80,12),p(80,18),p(88,18)];
 const carriageway=road([p(0),p(80)]);
 const route={parts:[line],onFoot:true,sourceType:'timeline_import',refs:[carriageway]};
 assert.equal(Roadside.align(route).promoted.length,1);
 const path={kind:'footway',coords:line,nodes:line.map((_,i)=>i+1)};
 const guarded=Roadside.align({...route,refs:[carriageway,path]});
 assert.deepEqual(guarded.parts,[line]);
 assert.equal(guarded.promoted.length,0);
});
test('single long road-parallel matched leg receives centreline despite sparse vertices',()=>{
 const route={parts:[[p(0,5),p(78,5)]],refs:[road([p(0),p(78)])]};
 const result=Roadside.align(route);
 assert.equal(result.promoted.length,1,'one long leg is not rejected solely because it has one source edge');
 assert.deepEqual(result.parts[0],[p(0),p(78)],'same physical road geometry');
 assert.deepEqual(route.parts,[[p(0,5),p(78,5)]],'raw source untouched');
});
test('single long leg cannot cut across a curving physical road just because endpoints touch',()=>{
 const line=[p(0,5),p(75,5)];
 const refs=[road([p(0),p(22,15),p(48,15),p(75)])];
 const result=Roadside.align({parts:[line],refs});
 assert.equal(result.promoted.length,0,'interior dissimilarity vetoes shortcut to curving road');
 assert.deepEqual(result.parts,[line]);
});
test('single brief parallel path must not be promoted',()=>{
 const line=[p(0,5),p(18,5)];
 const result=Roadside.align({parts:[line],refs:[road([p(0),p(60)])]});
 assert.equal(result.promoted.length,0);
 assert.deepEqual(result.parts,[line]);
});
test('single long diagonal pedestrian shortcut crossing a road does not become that road',()=>{
 const line=[p(0,0),p(75,18)];
 const result=Roadside.align({parts:[line],refs:[road([p(0),p(75)])]});
 assert.equal(result.promoted.length,0);
 assert.deepEqual(result.parts,[line]);
});
test('two parallel physical road references leave a one-leg journey ambiguous',()=>{
 const line=[p(0,4),p(75,4)];
 const result=Roadside.align({parts:[line],refs:[road([p(0),p(75)]),road([p(0,8),p(75,8)])]});
 assert.equal(result.promoted.length,0);
 assert.deepEqual(result.parts,[line]);
});
test('independent shortcut between roads remains off-centreline and continuous',()=>{
 const line=[p(0),p(18),p(24,5),p(30,9),p(36,5),p(42),p(60)];
 const refs=[road([p(0),p(18)]),{kind:'footway',coords:[p(18),p(24,5),p(30,9),p(36,5),p(42)]},road([p(42),p(60)])];
 const result=Roadside.align({parts:[line],refs});
 assert.deepEqual(result.parts,[line]);
 assert.equal(result.promoted.length,0);
});
test('community square pedestrian crossing with real bends is unchanged',()=>{
 const line=[p(0,18),p(10,18),p(20,28),p(35,28),p(45,20),p(55,20)];
 const refs=[road([p(0),p(55)]),{kind:'footway',coords:line}];
 const result=Roadside.align({parts:[line],refs});
 assert.deepEqual(result.parts,[line],'distinct off-road square route remains intact');
});
test('perpendicular footway access and crossings must not be moved to main road',()=>{
 const line=[p(0,5),p(15,5),p(15,18),p(30,18),p(30,5),p(50,5)];
 const result=Roadside.align({parts:[line],refs:[road([p(0),p(50)])]});
 assert.deepEqual(result.parts,[line],'no sustained continuous parallel run');
});
test('two equally supported parallel physical roads are ambiguous and neither is chosen',()=>{
 const line=[p(0,4),p(15,4),p(30,4),p(45,4),p(60,4)];
 const result=Roadside.align({parts:[line],refs:[road([p(0),p(60)]),road([p(0,8),p(60,8)])]});
 assert.equal(result.promoted.length,0);
 assert.deepEqual(result.parts,[line]);
});
test('already centreline-aligned journey does not introduce geometry changes',()=>{
 const line=[p(0),p(15),p(30),p(45),p(60)];
 const result=Roadside.align({parts:[line],refs:[road([p(0),p(60)])]});
 assert.deepEqual(result.parts,[line]);
 assert.equal(result.promoted.length,0);
});
test('a connected road carries three pavement runs through staggered side-street crossings',()=>{
 const centre=Array.from({length:10},(_,i)=>p(i*30));
 const refs=centre.slice(1).map((b,i)=>({
  id:i+1,kind:'primary',name:'Through Road',coords:[centre[i],b],nodes:[i,i+1]
 }));
 const line=[p(0,6),p(35,6),p(65,6),p(67,11),p(74,11),p(76,6),p(108,6),p(142,6),
  p(144,11),p(150,11),p(153,6),p(185,6),p(225,6),p(270,6)];
 const sideStreet={kind:'footway',footway:'crossing',coords:[line[3],line[4]]};
 for(const part of [line,line.toReversed()]){
  const result=Roadside.align({onFoot:true,parts:[part],refs:[...refs,sideStreet],
   independentRoadReferences:refs});
  assert(result.promoted.some(run=>run.kind==='continuous-road-corridor'));
  assert(result.parts[0].every(point=>point[1]===0));
  assert.deepEqual(result.parts[0],part===line?centre:centre.toReversed());
 }
 const protectedCrossing={kind:'steps',coords:[line[3],line[4]]};
 const guarded=Roadside.align({onFoot:true,parts:[line],refs:[...refs,protectedCrossing],
  independentRoadReferences:refs});
 assert(!guarded.promoted.some(run=>run.kind==='continuous-road-corridor'));
 assert(guarded.parts[0].includes(line[3]));

 const opposite=line.map(([x,y],i)=>[x,i<4?y:-y]);
 const across={kind:'footway',footway:'crossing',coords:opposite.slice(3,5)};
 const crossing=Roadside.align({onFoot:true,parts:[opposite],refs:[...refs,across],
  independentRoadReferences:refs});
 assert(!crossing.promoted.some(run=>run.kind==='continuous-road-corridor'));
 assert(crossing.parts[0].includes(opposite[3])&&crossing.parts[0].includes(opposite[4]));
});
test('without road reference geometry, no substitution is invented',()=>{
 const line=[p(0,4),p(20,4),p(40,4)];
 assert.deepEqual(Roadside.align({parts:[line],refs:[]}).parts,[line]);
});
test('a short same-road interruption is removed in either direction with a recorded detour veto',()=>{
 const main={kind:'unclassified',name:'Through Road',nodes:[1,2,3],coords:[p(0),p(30),p(65)]};
 const side={kind:'residential',name:'Side Road',nodes:[2,4],coords:[p(30),p(30,25)]};
 const crossing={kind:'footway',footway:'crossing',coords:[p(30,5),p(30,10)]};
 const route=[p(0),p(20),p(30,9),p(40),p(65)];
 for(const source of [route,route.toReversed()]){
  const line=source.slice();
  const joins=Roadside.closeRoadInterruptions(line,[main,side,crossing],[]);
  assert.equal(joins.length,1);
  assert(line.every(point=>point[1]===0),'leave the side road on the map without assigning it to the walk');
  const observed=source.slice();
  assert.equal(Roadside.closeRoadInterruptions(observed,[main,side,crossing],[p(30,9)]).length,0);
  assert.deepEqual(observed,source);
 }
});
test('LAB r486 one-map frontend loads alignment before route-led attribution',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.indexOf('roadside-centreline.js')<page.indexOf('route-led-coverage.js'));
 assert(page.includes('LAB r486')&&page.includes('>PR #486</a>'));
 assert(page.includes("mode=zip?'routeLed':'center'"));
 assert(page.includes('id="zipCompare"'));
});

test('a promoted interior run follows actual road vertices in both directions across split ways',()=>{
 const first={id:1,kind:'residential',name:'Road',nodes:[1,2,3],coords:[p(15),p(31,1),p(50)]};
 const second={id:2,kind:'residential',name:'Road',nodes:[3,4,5],coords:[p(50),p(65,1),p(90)]};
 const foot=[p(15,16),p(15,5),p(30,5),p(48,5),p(64,5),p(90,5),p(90,16)];
 for(const line of [foot,[...foot].reverse()]){
  const {parts,promoted}=Roadside.align({parts:[line],refs:[first,second]});
  assert(promoted.some(run=>run.road==='Road'&&run.metres>65));
  assert.deepEqual(parts[0][0],line[0]);
  assert.deepEqual(parts[0].at(-1),line.at(-1));
  for(const vertex of [p(31,1),p(50),p(65,1)])
   assert(parts[0].some(position=>position[0]===vertex[0]&&position[1]===vertex[1]));
 }
});

test('junction may consolidate a pavement on the same bridge but protects other grades and crossings',()=>{
 const refs=[
  {id:1,kind:'residential',name:'Road',nodes:[1,2],coords:[p(0),p(10)]},
  {id:2,kind:'residential',name:'Road',nodes:[2,3],coords:[p(10),p(55)],bridge:'yes',layer:'1'},
  {id:3,kind:'residential',name:'Road',nodes:[3,4],coords:[p(55),p(75)]},
 ];
 const pavement=[p(17,4),p(25,4),p(35,4),p(45,4),p(55)];
 const part=[p(0),p(10),...pavement,p(65),p(74)];
 const bridge={kind:'footway',coords:pavement,bridge:'yes',layer:'1'};
 const align=foot=>Roadside.align({parts:[part],refs:[...refs,foot]});
 const result=align(bridge);
 assert(result.promoted.some(run=>run.kind==='junction'));
 assert(result.parts[0].every(point=>point[1]===0));
 for(const foot of [{...bridge,layer:'2'},{...bridge,footway:'crossing'}])
  assert.deepEqual(align(foot).parts,[part]);
});

test('dense pavement vertices extend an accepted run to the mapped access transition in either direction',()=>{
 const main={kind:'residential',coords:[p(0),p(100)]};
 const access=[p(66.5,5),p(71,12),p(72,14)];
 const part=[p(0,5),p(20,5),p(40,5),p(65,5),p(65.5,5),p(66,5),...access];
 for(const line of [part,[...part].reverse()]){
  const output=Roadside.align({parts:[line],refs:[main]}).parts[0];
  assert(output.some(point=>Math.abs(point[0]-p(66.5)[0])<1e-10&&point[1]===0));
  for(const point of access)assert(output.some(vertex=>vertex[0]===point[0]&&vertex[1]===point[1]));
 }
});

test('terminal access uses a connected road with saved movement and a corroborating final fix',()=>{
 const road={kind:'residential',name:'Street',coords:[p(0),p(82)]};
 const access={kind:'service',coords:[p(82),p(87,3),p(95,6),p(110,6)]};
 const tail=[p(87,3),p(95,6),p(103,6),p(107,6)];
 const frontage=[p(70,8),p(75,8),p(81,7)];
 const parts=[[p(0,5),p(20,5),p(40,5),p(65,5),p(65.5,5),...frontage,...tail]];
 const route={onFoot:true,parts,refs:[road,access],recorded:[[p(0,5),p(103,-3)]]};
 const result=Roadside.align(route);
 assert(result.promoted.some(run=>run.kind==='terminal-access'));
 assert.deepEqual(result.parts[0].slice(-5),[p(82),...tail]);
 for(const variation of [
  {recorded:[[p(0,5),p(75,8),p(103,-3)]]},
  {recorded:[]},
  {refs:[road,{...access,coords:[p(82,2),...access.coords.slice(1)]}]},
  {corrected:true},
 ]){
  const preserved=Roadside.align({...route,...variation});
  assert(!preserved.promoted.some(run=>run.kind==='terminal-access'));
  assert(preserved.parts[0].some(v=>v===frontage[1]));
 }
});

test('a source already on one carriageway stays there when another carriageway runs in parallel',()=>{
 const own={kind:'primary',name:'Road',nodes:[1,2,3],coords:[p(0),p(30),p(100)]};
 const other={kind:'primary',name:'Road',nodes:[4,5],coords:[p(0,7),p(100,7)]};
 const line=[p(5),p(40),p(70),p(90)];
 const aligned=Roadside.align({parts:[line],refs:[own,other]});
 assert.deepEqual(aligned.parts,[line]);
 assert.deepEqual(aligned.promoted,[]);
});

test('a short divided-road handoff uses the supported through branch to its mapped merge',()=>{
 const road=(nodes,coords)=>({kind:'primary',name:'Road',nodes,coords});
 const approach=road([1,2,3,4,5],[p(0),p(30),p(50),p(65),p(100)]);
 const branch=road([3,6,7,5],[p(50),p(59,12),p(85,12),p(100)]);
 const departure=road([5,8,9],[p(100),p(110),p(125)]);
 const ramp=[p(61,12),p(63,12),p(73,12),p(85,12)];
 const line=[p(0,5),p(12,5),p(27,5),p(60,5),...ramp,p(100),p(110),p(125)];
 const route={parts:[line],onFoot:true,refs:[approach,branch,departure]};
 const normalized=Roadside.align(route);
 assert(normalized.promoted.some(run=>run.kind==='divided-road'));
 assert(normalized.parts[0].every(v=>v[1]===0),'the display follows connected road geometry');
 const recorded=[[p(0,5),p(65,12),p(79,12),p(125)]];
 assert(!Roadside.align({...route,recorded}).promoted.some(run=>run.kind==='divided-road'));
 assert(!Roadside.align({...route,corrected:true}).promoted.some(run=>run.kind==='divided-road'));
 assert(!Roadside.align({...route,refs:[approach,departure]}).promoted.some(run=>run.kind==='divided-road'));
});

test('station transfer uses a connected street approach up to the preserved entrance spur',()=>{
 const refs=[
  {kind:'tertiary',name:'Street',coords:[p(-20),p(0),p(20)],nodes:[1,2,3]},
  {kind:'tertiary',name:'Hill',coords:[p(20),p(36),p(56),p(68)],nodes:[3,4,5,6]},
 ];
 const line=[p(-18),p(0),p(0,12),p(18,12),p(34,12),p(48,10),p(56,10),p(56,4),p(56,1)];
 const route={parts:[line],refs,onFoot:true,railTransitions:[{point:p(56,1)}]};
 const result=Roadside.align(route);
 assert(result.promoted.some(run=>run.kind==='station-approach'));
 assert.deepEqual(result.parts[0].slice(-4),[p(56),p(56,10),p(56,4),p(56,1)]);
 const observed=Roadside.align({...route,recorded:[[p(34,12)]]});
 assert(!observed.promoted.some(run=>run.kind==='station-approach'));
 const withoutMarker=Roadside.align({...route,railTransitions:[]});
 assert(!withoutMarker.promoted.some(run=>run.kind==='station-approach'));
});

test('both directions of a retraced pavement align at one shared road turn',()=>{
 const outward=[p(0,5),p(20,5),p(40,5),p(60,5)];
 const line=outward.concat(outward.slice(0,-1).reverse());
 const route={parts:[line],refs:[road([p(0),p(60)])]};
 const result=Roadside.align(route);
 assert.equal(result.promoted.length,2);
 assert.deepEqual(result.parts[0],[p(0),p(60),p(0)]);
 assert.deepEqual(route.parts,[line]);
});

test('a crossing around a junction corner can count on the through road',()=>{
 const named=(name,coords,nodes,kind='tertiary')=>({name,kind,coords,nodes});
 const refs=[
  named('West Road',[p(-30),p(0)],[1,2]),
  named('East Road',[p(0),p(7),p(30)],[2,3,4]),
  named('Side Road',[p(0),p(0,11),p(0,30)],[2,5,6]),
  named('',[p(-5,11),p(0,11),p(5,11)],[7,5,8],'footway'),
  named('',[p(5,11),p(7,6),p(7)],[8,9,3],'footway')
 ];
 const line=[p(-30),p(0),p(0,11),p(5,11),p(7,6),p(7),p(30)];
 for(const from of [line,line.toReversed()]){
  const route=from.slice();
  assert.equal(Roadside.closeKerbsideLoops(route,refs,[p(2,11)]).length,1);
  assert.deepEqual(route,from.filter(pt=>pt[1]===0));
 }
 const genuineSideRoad=line.slice();
 assert.equal(Roadside.closeKerbsideLoops(genuineSideRoad,refs,[p(0,5),p(2,11)]).length,0);
 assert.deepEqual(genuineSideRoad,line);
 const disconnected=refs.map(ref=>ref.name==='East Road'?{...ref,nodes:[11,3,4]}:ref);
 assert.equal(Roadside.closeKerbsideLoops(line.slice(),disconnected,[p(2,11)]).length,0);
});
