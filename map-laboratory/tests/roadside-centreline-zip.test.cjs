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
test('LAB r449 one-map frontend loads alignment before route-led attribution',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.indexOf('roadside-centreline.js')<page.indexOf('route-led-coverage.js'));
 assert(page.includes('LAB r449')&&page.includes('>PR #449</a>'));
 assert(page.includes("mode=zip?'routeLed':'center'"));
 assert(page.includes('id="zipCompare"'));
});
