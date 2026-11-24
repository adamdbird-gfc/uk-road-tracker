const assert=require('node:assert/strict');
const {test}=require('node:test');
const RouteLed=require('../route-led-coverage.js');
const p=(x,y=0)=>[x/111320,y/111320];
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

test('ZIP UI keeps canonical and route-led models isolated',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.includes('route-led-coverage.js'));
 assert(page.includes('RouteLedCoverage.accumulate(zipRouteLedLedger,route)'));
 assert(page.includes("mode==='routeLed'?zipRouteLedCache.get(n):zipCoverageCache.get(n)"));
 assert(page.includes("zipRouteLedCache.clear();zipRouteLedLedger.clear()"));
 assert(page.includes('LAB r446')&&page.includes('>PR #446</a>'));
 assert(page.includes('id="zipCompare"')&&page.includes('id="zipModeControls" hidden')&&page.includes("mode=zip?'routeLed':'center'"));
});
