// Direct navigation and source-near uncredited candidate inspection are local.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const page=fs.readFileSync(path.join(__dirname,'..','index.html'),'utf8');
const start=page.indexOf('function resetAttribution()'),end=page.indexOf('async function advance(n)',start);
assert(start>0&&end>start,'direct journey engine exists');
const view=page.slice(start,end);
const data={journeys:Array.from({length:1500},(_,i)=>({id:i}))};
let busy=false,step=0,isolated=false,selectedRoad=-1,selectedReport=null;
const roads=Array.from({length:10},()=>({first:Infinity,second:Infinity}));
const cache=new Map(),calls=[],notice=[],elements=new Map();
const $=id=>{if(!elements.has(id))elements.set(id,{style:{}});return elements.get(id)};
const nav=new Function('data','roads','cache','$','ensure','match','stop','clearError','controls','tell','update','focus','problem','state',
'let busy=false,step=0,isolated=false,selectedRoad=-1,selectedReport=null;'+view+';return {jumpTo,resetAttribution,get:()=>({busy,step,isolated})};')(
data,roads,cache,$,async j=>calls.push(j.id),j=>({ids:new Set([2,4,6]),candidateRefs:new Set([1,2,3,4,6])}),
()=>{},()=>{},()=>{},text=>notice.push(text),()=>{},()=>{},text=>notice.push(text),{});
 // State mutations are local to the extracted functions, not persisted into
 // the source file. More important: direct jump has exactly one ensure/match.
(async()=>{
 await nav.jumpTo(1200);
 assert.deepEqual(calls,[1199],'no prior journeys fetched or matched');
 assert.deepEqual([...cache.keys()],[1199],'only requested match cached');
 assert.equal(roads[2].first,1199);assert.deepEqual(nav.get(),{busy:false,step:1200,isolated:true});
 assert.equal(roads[3].first,Infinity,'unselected reference remains uncredited');
 await nav.jumpTo(6);
 assert.deepEqual(calls,[1199,5],'changing target loads only the new journey');
 assert.deepEqual([...cache.keys()],[5],'old isolated match cleared');
 assert.equal(roads[2].first,5,'isolated attribution reset');assert.equal(nav.get().step,6);
 await nav.jumpTo(1501);
 assert.equal(calls.length,2,'invalid journey never triggers matching');
 assert(page.includes('id="jumpForm"'),'number entry exposed');
 assert(page.includes('id="replayAll"'),'explicit cumulative replay option');
 assert(page.includes("red is matched geometry")||page.includes('red is matched geometry'),'isolated noncumulative explanation present');
 assert(page.includes("Matched (isolated; not cumulative new)"),'red legend never claims cumulative new');
 assert(page.includes("candidateRefs.add(id)"),'inspector candidates collected from source-near references');
 assert(page.includes("stroke(segments,'#efb84c',3)"),'uncredited candidates visually highlighted');
 assert(page.includes("if(gapMode){for(const id of candidateMatch?.candidateRefs"),'tap targets match highlighted candidates');
 assert(page.includes("m.ids.has(id)"),'credited road intervals excluded from amber candidates');
 console.log('PASS: direct journey 1200 without sequential replay, isolated reset, invalid bounds and visible source-near gap candidates');
})().catch(e=>{console.error(e);process.exitCode=1});
