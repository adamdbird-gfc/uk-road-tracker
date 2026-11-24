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
test('without road reference geometry, no substitution is invented',()=>{
 const line=[p(0,4),p(20,4),p(40,4)];
 assert.deepEqual(Roadside.align({parts:[line],refs:[]}).parts,[line]);
});
test('LAB r456 one-map frontend loads alignment before route-led attribution',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.indexOf('roadside-centreline.js')<page.indexOf('route-led-coverage.js'));
 assert(page.includes('LAB r456')&&page.includes('>PR #456</a>'));
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
