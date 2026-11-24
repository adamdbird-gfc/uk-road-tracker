const assert = require('node:assert/strict');
const {test} = require('node:test');
const {crc32,deflateRawSync} = require('node:zlib');
const {open} = require('../bulk-diagnostics.js');

function zip(entries, damage) {
  const locals = [], records = [];
  let offset = 0;
  for (const [name,json] of entries) {
    const label = Buffer.from(name), bytes = Buffer.from(JSON.stringify(json));
    const compressed = deflateRawSync(bytes), crc = crc32(bytes);
    const header = Buffer.alloc(30 + label.length);
    header.writeUInt32LE(0x04034b50);header.writeUInt16LE(20,4);
    header.writeUInt16LE(0x0808,6);header.writeUInt16LE(8,8);
    header.writeUInt16LE(label.length,26);label.copy(header,30);
    const descriptor = Buffer.alloc(16);
    descriptor.writeUInt32LE(0x08074b50);descriptor.writeUInt32LE(crc,4);
    descriptor.writeUInt32LE(compressed.length,8);descriptor.writeUInt32LE(bytes.length,12);
    const entry = Buffer.alloc(46 + label.length);
    entry.writeUInt32LE(0x02014b50);entry.writeUInt16LE(20,6);
    entry.writeUInt16LE(0x0808,8);entry.writeUInt16LE(8,10);
    entry.writeUInt32LE(name===damage?0:crc,16);entry.writeUInt32LE(compressed.length,20);
    entry.writeUInt32LE(bytes.length,24);entry.writeUInt16LE(label.length,28);
    entry.writeUInt32LE(offset,42);label.copy(entry,46);
    offset += header.length+compressed.length+descriptor.length;
    locals.push(header,compressed,descriptor);records.push(entry);
  }
  const directory = Buffer.concat(records), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50);end.writeUInt16LE(entries.length,8);end.writeUInt16LE(entries.length,10);
  end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16);
  return new Blob([...locals,directory,end],{type:'application/zip'});
}

function trip(n,date) {
  const journey={journey_id:'j-'+n,started_at:date,ended_at:null,mode:'walking',timezone:null};
  return {journey,stored:[],unavailable:['capture_route_samples']};
}
const manifest = records => ({format:'roadprints_bulk_journey_diagnostics',schema_version:1,
  export_complete:true,journey_count:records.length,journeys:records.map(x=>x.journey)});
const entries = records => [...records.map((record,index)=>['journeys/'+(index+1)+'.json',record]),
  ['manifest.json',manifest(records)]];

test('indexes a full archive, with chronological and lazy ID-checked reads', async () => {
  const records=Array.from({length:1700},(_,n)=>trip(n,new Date(Date.UTC(2026,0,1,0,1700-n)).toISOString()));
  const opened=await open(zip(entries(records)));
  assert.equal(opened.journeys.length,1700);
  assert.equal(opened.journeys[0].journey_id,'j-1699');
  assert.deepEqual(await opened.read(235), records[1699-235]);
  assert.deepEqual(await opened.read(1699), records[0]);
});

test('rejects an incomplete archive, duplicates, corrupted evidence and mismatched IDs', async () => {
  const record=trip(3,'2026-01-02T11:00:00Z');
  const encoded=entries([record]);
  await assert.rejects(open(zip([encoded[0]])),/missing|oversized/i);
  await assert.rejects(open(zip([encoded[0],...encoded])),/duplicate/i);
  const archive=await open(zip(encoded,encoded[0][0]));
  await assert.rejects(archive.read(0),/damaged/i);
  const mismatched=[['journeys/1.json',trip(9,record.journey.started_at)],encoded[1]];
  await assert.rejects((await open(zip(mismatched))).read(0),/does not match/i);
  const complete=zip(encoded);
  await assert.rejects(open(complete.slice(0,complete.size-4)),/incomplete|invalid/i);
});

test('rail stays in the archive and is skipped in replay with original jump ordinals',async()=>{
 const {roadReplay}=require('../bulk-diagnostics.js');
 const read=[];
 const archive={journeys:['walking','train','running','tram','ferry'].map(mode=>({mode})),
  async read(index){read.push(index);return {journey:this.journeys[index]};}};
 const replay=roadReplay(archive);
 assert.equal(replay.archiveCount,5);
 assert.equal(replay.railHidden,2);
 assert.deepEqual(replay.journeys.map(row=>row.archiveOrdinal),[1,3,5]);
 assert.equal((await replay.read(1)).journey.mode,'running');
 assert.deepEqual(read,[2]);
 assert.equal(archive.journeys.length,5);
 assert.throws(()=>replay.read(3),/outside the replay index/);
 assert.equal(roadReplay({journeys:[{mode:'train'}]}).journeys.length,0);
});
