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
test('ZIP UI keeps canonical and route-led models isolated',()=>{
 const fs=require('node:fs'),path=require('node:path');
 const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
 assert(page.includes('route-led-coverage.js'));
 assert(page.includes("if(mode==='routeLed')await ensureRouteLedThrough(n);"));
 assert(page.includes("mode==='routeLed'?zipRouteLedCache.get(n):zipCoverageCache.get(n)"));
 assert(page.includes("zipRouteLedCache.clear();zipRouteLedLedger.clear()"));
 assert(page.includes('LAB r444')&&page.includes('>PR #444</a>'));
});
