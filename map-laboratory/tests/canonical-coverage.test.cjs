const assert=require('node:assert/strict');
const {test}=require('node:test');
const Coverage=require('../canonical-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
const ref=(kind,coords)=>({kind,coords});
const route=(parts,refs)=>({parts,refs});
test('roadside pedestrian travel maps to a road centreline once, not a parallel footway',()=>{
 const refs=[ref('residential',[p(0),p(40)]),ref('footway',[p(0,4),p(40,4)])];
 const c=Coverage.accumulate(new Set(),route([[p(0,3),p(20,3),p(40,3)]],refs));
 assert(c.novel.length>0,'physical road receives provisional intervals');
 assert(c.novel.every(x=>x.type==='road'),'parallel roadside footway suppressed');
 assert(c.held.some(x=>x.reason==='parallel-roadside-path'),'redundancy made inspectable');
 assert(c.novel.every(x=>Math.abs(x.a[1])<1e-10),'road credited at centreline');
});
test('nearby unused side road is not credited as a spur',()=>{
 const refs=[ref('residential',[p(0),p(40)]),ref('residential',[p(20),p(20,25)])];
 const c=Coverage.accumulate(new Set(),route([[p(0),p(20),p(40)]],refs));
 assert(c.novel.length>0);
 assert(c.novel.every(x=>Math.abs(x.a[0]-p(20)[0])>1e-10||Math.abs(x.a[1])<1e-10),
 'a perpendicular nearby branch earns no coverage without traversal');
});
test('independent footpath and two road approaches are retained as distinct network geometries',()=>{
 const refs=[
  ref('residential',[p(0),p(32)]),
  ref('footway',[p(32),p(32,24)]),
  ref('residential',[p(32,24),p(64,24)])
 ];
 const c=Coverage.accumulate(new Set(),route([[p(0),p(32),p(32,24),p(64,24)]],refs));
 assert(c.novel.some(x=>x.type==='path'),'genuine independent crossing retained');
 assert(c.novel.some(x=>x.type==='road'),'physical roads retained');
});
test('repeated journey uses stable geometry identities, not additional credits',()=>{
 const refs=[ref('residential',[p(0),p(40)])];
 const first=Coverage.accumulate(new Set(),route([[p(0),p(40)]],refs));
 const second=Coverage.accumulate(first.next,route([[p(40),p(0)]],refs));
 assert(first.novel.length>0);assert.equal(second.novel.length,0);
 assert.equal(second.repeat.length,first.novel.length);
});
test('a genuine source-matched out and back on a separate path is not blindly deleted',()=>{
 const refs=[ref('residential',[p(0),p(40)]),ref('footway',[p(20),p(20,22)])];
 const c=Coverage.accumulate(new Set(),route([[p(0),p(20),p(20,22),p(20),p(40)]],refs));
 assert(c.novel.some(x=>x.type==='path'),'legitimate path traversal preserved');
});
test('two nearly overlapping equally plausible roads remain uncredited',()=>{
 const refs=[ref('residential',[p(0),p(40)]),ref('residential',[p(0,2),p(40,2)])];
 const c=Coverage.accumulate(new Set(),route([[p(0,1),p(40,1)]],refs));
 assert.equal(c.novel.length,0,'uncertain parallel attribution is held');
 assert(c.held.length>0);
});
test('recorded match and source references are never mutated',()=>{
 const input=route([[p(0),p(32)]],[ref('residential',[p(0),p(32)])]);
 const before=JSON.stringify(input);
 Coverage.accumulate(new Set(),input);
 assert.equal(JSON.stringify(input),before);
});
