const assert=require('node:assert/strict');
const {test}=require('node:test');
const PairedWalk=require('../paired-walk.js');

const point=(x,y)=>[x/111320,y/111320];
const path=[point(0,0),point(60,0),point(120,0),point(120,10),point(180,10),point(240,10)];
const refs=[
 {id:1,kind:'footway',nodes:[1,2,3],coords:path.slice(0,3)},
 {id:2,kind:'footway',footway:'crossing',nodes:[3,4],coords:path.slice(2,4)},
 {id:3,kind:'footway',nodes:[4,5,6],coords:path.slice(3)}
];
const route=line=>({onFoot:true,source:'saved match',sourceType:'timeline_import',
 recorded:[[line[0],line.at(-1)]],parts:[line],independentRoadReferences:refs});
const outward=route(path);
const home=route([path[5],point(220,22),point(140,22),path[3],path[2],point(100,-12),point(20,-12),path[0]]);

test('opposite sparse matches use the same connected pedestrian route and mapped crossing',()=>{
 const result=PairedWalk.match(outward,home);
 assert.deepEqual(result.first,path);
 assert.deepEqual(result.second,path.toReversed());
 assert.equal(PairedWalk.match(outward,{...home,corrected:true}),null);
 assert.equal(PairedWalk.match(outward,route(path.toReversed())),null,'agreement needs no correction');
 assert.equal(PairedWalk.match(outward,route(home.parts[0].filter(p=>p!==path[3]))),null,'both walks need crossing evidence');
});

test('replay only considers an unambiguous adjacent walking pair',async()=>{
 const rows=[
  {mode:'walking',archiveOrdinal:10,started_at:'2026-01-10T19:06:00Z',ended_at:'2026-01-10T19:15:00Z'},
  {mode:'walking',archiveOrdinal:11,started_at:'2026-01-10T23:25:00Z',ended_at:'2026-01-10T23:34:00Z'}
 ];
 const load=async i=>[outward,home][i];
 assert.deepEqual((await PairedWalk.forReplay(1,home,rows,load)).pairedWalk.line,path.toReversed());
 assert.equal(await PairedWalk.forReplay(0,outward,[rows[0],{...rows[1],archiveOrdinal:12}],load),outward);
 assert.equal((await PairedWalk.forReplay(0,outward,rows,async()=>({networkUnavailable:true}))).networkUnavailable,true);
});
