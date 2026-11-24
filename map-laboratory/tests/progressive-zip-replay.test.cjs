const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('async function showZipJourney(n) {');
const end=page.indexOf('\nfunction updateZip(){',start);
assert(start>=0&&end>start,'ZIP progressive navigation must exist');
assert(page.includes('drawParts(0,Math.max(0,step-1),i=>zipRouteCache.get(i)'),
 'previously played ZIP routes must be rendered cumulatively');
assert(page.includes('if(diagnosticsZip){')&&page.includes("if(mode==='canonical'||mode==='routeLed')")&&page.includes('Route-first displays raw matched history'),
 'ZIP source replay stays distinct from provisional canonical coverage');
assert(page.includes("zipRouteCache.clear();zipCoverageCache.clear();zipCoverageLedger.clear();zipRouteLedCache.clear();zipRouteLedLedger.clear();zipIndependentByJourney.clear();zipNetworkStatusByJourney.clear();currentCoverageAudit=null;$('coverageAuditSummary').textContent='';$('downloadCoverageAudit').disabled=true;mode=zip?'routeLed':'center'"),
 'changing input resets previously replayed route history');
assert(page.includes('LAB r453')&&page.includes('>PR #453</a>'));
const seen=[];
const zip={read:async index=>{seen.push(index);return {journey:{index}};}};
const reconstruct=journey=>({
 parts:[[[journey.index,0],[journey.index+0.5,0]]],
 recorded:[[[journey.index,0],[journey.index+0.5,0]]],
 refs:[],source:'saved match',projected:0,joins:0
});
const harness=new Function('zip','reconstruct',`
 let diagnosticsZip=zip,diagnosticRoute=null,zipRouteCache=new Map(),busy=false,step=0,isolated=false,mode='routeLed';
 let data={journeys:Array.from({length:4},(_,i)=>['date'+i,'walk','walking',[],null])};
 let roads=[],roadIds=new Map(),cache=new Map(),messages=[];
 const RouteFirst={reconstruct};
 const CanonicalCoverage={accumulate:(set)=>({novel:[],repeat:[],held:[],next:new Set(set)})};
 let zipCoverageCache=new Map(),zipCoverageLedger=new Set(),zipRouteLedCache=new Map(),zipRouteLedLedger=new Set(),zipIndependentByJourney=new Map(),zipNetworkStatusByJourney=new Map(),currentCoverageAudit=null;
 const RouteLedCoverage={accumulate:set=>({novel:[],repeat:[],held:[],next:new Set(set)})};
 const IndependentRoadNetwork={needsNetwork:()=>false,lookup:async()=>{throw Error('public network lookup not needed for fully referenced sample')}};
 const controlsById={};const $=id=>(controlsById[id]??={textContent:'',disabled:false});
 const tell=s=>messages.push(s),clearError=()=>{},controls=()=>{},focus=()=>{},update=()=>{},stop=()=>{};
 const problem=s=>{throw Error(s)},addWays=()=>{},match=()=>{throw Error('unexpected rematch')};
 ${page.slice(start,end)}
 return {showZipJourney,state:()=>({step,cache:[...zipRouteCache.keys()],parts:zipRouteCache.get(0),route:diagnosticRoute,rawRows:data.journeys.map(j=>j[3].length),messages})};
`)(zip,reconstruct);
(async()=>{
 await harness.showZipJourney(1);
 assert.deepEqual(seen,[0],'first Next reads one journey');
 await harness.showZipJourney(3);
 assert.deepEqual([...new Set(seen)].sort(),[0,1,2],'jump processes every intervening journey without cross-journey reference scan');
 assert.deepEqual(harness.state().cache,[0,1,2],'earlier route geometry retained');
 await harness.showZipJourney(2);
 assert.equal(harness.state().step,2,'Back changes the displayed cumulative state');
 assert(harness.state().messages.at(-1).includes('Cumulative ZIP replay · 2 / 4 journeys'), 'status must use committed selected journey, not old processing target');
 assert.equal(harness.state().cache.length,3,'Back does not discard later cached geometry');
 assert.equal(harness.state().cache.length,3,'previous journey reference check must not discard already played history');
 assert.equal(harness.state().rawRows.filter(Boolean).length,1,'raw recorded lines retained only for selected journey');
 await harness.showZipJourney(4);
 assert.deepEqual([...new Set(seen)].sort(),[0,1,2,3],'later jump retains chronological coverage and adds the new selected journey');
 assert(harness.state().messages.at(-1).includes('Cumulative ZIP replay · 4 / 4 journeys'), 'later jump status stays in sync with selected journey');
 console.log('PASS: progressive ZIP Next/Back/direct jump and chronological geometry accumulation');
})().catch(error=>{console.error(error);process.exitCode=1});
