const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const begin=html.indexOf('function inspectWayEvidence('),end=html.indexOf('function examineRoad(',begin);
assert.ok(begin>0&&end>begin,'read-only whole-way inspector exists');
const roads=[
{group:'A',index:0,name:'',kind:'footway',first:4},
{group:'A',index:1,name:'',kind:'footway',first:4},
{group:'A',index:2,name:'',kind:'footway',first:4}
];
const cache=new Map([[4,{ids:new Set([0,1,2]),direct:new Set([0,2]),
hits:new Map([[0,[{sourceSegment:10,selectionScore:.5}]],[2,[{sourceSegment:12,selectionScore:.8}]]])}]]);
const inspect=new Function('roads','cache',html.slice(begin,end)+';return inspectWayEvidence')(roads,cache);
const middle=inspect(1,4),start=inspect(0,4);
assert.equal(middle.firstCreditReason,'same-way bridge without direct sample');
assert.equal(start.firstCreditReason,'direct sample');
assert.equal(middle.firstJourneyDirectIntervals,2);
assert.equal(middle.firstJourneyCreditedIntervals,3);
assert.deepEqual(middle.firstJourneyDistinctSourceLegs,[10,12]);
assert.equal(middle.firstJourneySampleHits,2);
assert.equal(middle.firstJourneySampleEvidence.length,2);
assert.deepEqual([...cache.get(4).ids],[0,1,2],'inspection must not mutate coverage');
console.log('PASS: source provenance, bounded way-wide evidence, zero matcher mutation');
