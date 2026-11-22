// Synthetic network geometry only; no owner locations or source archive.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=html.indexOf('function unsupportedFootwayEndcaps('),end=html.indexOf('function match(j){',start);
assert.ok(start>0&&end>start,'footway endcap classifier present');
const M=111320,D=Math.PI/180;
const metres=(a,b)=>Math.hypot((a[0]-b[0])*M*Math.cos((a[1]+b[1])*.5*D),(a[1]-b[1])*M);
const classify=new Function('metres',html.slice(start,end)+';return unsupportedFootwayEndcaps')(metres);
const p=(x,y=0)=>[x/M,y/M],roads=[],byIndex=new Map();
for(let n=65;n<=82;n++){const id=roads.length;byIndex.set(n,id);roads.push({p:p((n-65)*6),q:p((n-64)*6),index:n,kind:'footway',group:'sample-way'});}
const groups=new Map([['sample-way',[...roads.keys()]]]);
const mark=[70,72,73,74,75,76],evidence=new Set(mark.map(n=>byIndex.get(n))),hits=new Map();
for(const n of mark){const id=byIndex.get(n),r=roads[id];hits.set(id,[{sourceSegment:31,sourcePoint:p((n-64)*6-3)}]);}
// An isolated interval (70) has two nominal matches but a single source
// position near its inboard endpoint, backed by a strong separate run.
const endcapId=byIndex.get(70),point=roads[endcapId].q;
hits.set(endcapId,[{sourceSegment:31,sourcePoint:point},{sourceSegment:46,sourcePoint:point}]);
let held=classify(roads,groups,evidence,hits);
assert.deepEqual([...held],[endcapId],'endpoint-only outlier held');
assert.ok(!held.has(byIndex.get(72)),'supported corridor retained');
assert.ok(!held.has(byIndex.get(73)),'rest of frontage retained');
// Two independent geographical points on the candidate *prove* that
// some distance was actually travelled on the first interval.
hits.set(endcapId,[{sourceSegment:31,sourcePoint:roads[endcapId].p},{sourceSegment:46,sourcePoint:point}]);
assert.ok(!classify(roads,groups,evidence,hits).has(endcapId),'traversed interval is kept');
// Genuine one-piece connecting footpath is never removed simply for length.
hits.set(endcapId,[{sourceSegment:31,sourcePoint:point}]);
assert.ok(!classify(roads,groups,new Set([endcapId]),hits).has(endcapId),'single connecting way kept');
// If the map has direct evidence right next to this interval it is not
// an isolated endcap; don't break adjacent physically walked paths.
const connected=new Set([...evidence,byIndex.get(71)]);
assert.ok(!classify(roads,groups,connected,hits).has(endcapId),'directly adjacent path retained');
assert.equal(evidence.size,6,'input evidence unchanged');
assert.ok(html.includes('!unsupportedFootEnds.has(id)'),'held endcaps excluded before gap bridging');
console.log('PASS: end-touch-only footway nib held, genuine footways and frontage preserved');
