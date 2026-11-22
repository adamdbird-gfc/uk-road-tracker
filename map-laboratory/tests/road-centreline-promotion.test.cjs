// Synthetic footway-to-road equivalence, with no private route geometry.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=html.indexOf('function promoteParallelRoadCentrelines('),end=html.indexOf('function roadEquivalentFootways(',start);
assert.ok(start>0&&end>start,'road-centreline promotion present');
const M=111320,D=Math.PI/180;
const xyz=(a,o)=>[(a[0]-o[0])*M*Math.cos(o[1]*D),(a[1]-o[1])*M];
const proj=(a,b,p)=>{const v=[b[0]-a[0],b[1]-a[1]],d=v[0]*v[0]+v[1]*v[1];if(d<.001)return 1e9;const t=Math.max(0,Math.min(1,((p[0]-a[0])*v[0]+(p[1]-a[1])*v[1])/d));return Math.hypot(p[0]-a[0]-t*v[0],p[1]-a[1]-t*v[1])};
const promote=new Function('xyz','proj',html.slice(start,end)+';return promoteParallelRoadCentrelines')(xyz,proj);
const p=(x,y=0)=>[x/M,y/M],r=[];
for(let i=0;i<8;i++)r.push({p:p(i*8),q:p((i+1)*8),kind:'residential',group:'road',index:i,first:Infinity});
for(let i=0;i<8;i++)r.push({p:p(i*8,4),q:p((i+1)*8,4),kind:'footway',group:'roadside',index:i,first:Infinity});
for(let i=0;i<8;i++)r.push({p:p(i*8,16),q:p((i+1)*8,16),kind:'footway',group:'frontage',index:i,first:Infinity});
r.push({p:p(24,4),q:p(24,16),kind:'footway',group:'entrance',index:0,first:Infinity});
r.push({p:p(32,0),q:p(32,8),kind:'steps',group:'station-steps',index:0,first:Infinity});
const traveled=new Set([8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]);
const result=promote(traveled,r);
assert.equal(result.promoted.size,8,'all supported road sections eligible, not whole road');
assert.equal(result.equivalent.size,8,'only roadside footway goes into centreline');
assert.equal(result.acceptedRuns,1);
assert.equal([...result.equivalent].filter(i=>i>=16).length,0,'separate frontage and perpendicular access preserved');
assert.equal(traveled.size,18,'original evidence not mutated');
const partial=promote(new Set([8,9]),r);
assert.equal(partial.promoted.size,0,'too-short footway evidence cannot promote road');
const distant=promote(new Set([16,17,18,19]),r);
assert.equal(distant.promoted.size,0,'separate frontage remains separate');
assert.equal(promote(new Set([24,25]),r).promoted.size,0,'perpendicular entrance and steps not converted to road');
// A/B testing must recompute *all* first/second attribution, rather than
// visually hiding lines while retaining credit awarded by the ON model.
assert.ok(html.includes("cache.clear();for(const road of roads){road.first=Infinity;road.second=Infinity}"),'A/B toggle resets cumulative credit');
assert.ok(html.includes("typeof promoteRoads"),'promotion can be disabled at matching stage');
assert.ok(html.includes("if(wasIsolated)await jumpTo(target);else await advance(target)"),'A/B toggle restores selected journey in the correct replay scope');
assert.ok(html.includes('id="promotion"'),'owner has visible A/B control');
console.log('PASS: synthetic road/pavement matching and deterministic A/B replay reset');
