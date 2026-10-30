"""Reproduce the reviewed seam candidate from bounded, checksummed Git chunks."""
import argparse
import base64
import gzip
import hashlib
import io
import json
import math
from pathlib import Path
import sqlite3
import struct
from scripts.ncn_binding_attainability import apply_patch, content_sha, digest, encoded

ROOT=Path(__file__).resolve().parents[1]


def read_delta(index_path):
    index_path=Path(index_path);index=json.loads(index_path.read_text());parts=[]
    for row in index['parts']:
        path=(index_path.parent/row['file']).resolve()
        if not path.is_relative_to(index_path.parent.resolve()):raise ValueError('Chunk escapes artifact directory')
        raw=path.read_bytes()
        if hashlib.sha256(raw).hexdigest()!=row['sha256']:raise ValueError('Correction chunk changed')
        parts.append(base64.b64decode(raw,validate=True))
    raw=b''.join(parts)
    if hashlib.sha256(raw).hexdigest()!=index['artifact_sha256']:raise ValueError('Correction artifact changed')
    if index['implementation_commit']!='c0566c6421a2d2b155f738e0411a526143bc385c':raise ValueError('Wrong implementation')
    return raw,index


def reproduce(database,output,index_path=ROOT/'.github/ncn-reference/ordinary-seams/index.json'):
    raw,index=read_delta(index_path)
    prior_raw=(ROOT/'.github/ncn-reference/stage1-binding-correction.json.gz').read_bytes()
    if hashlib.sha256(prior_raw).hexdigest()!=index['source_offset_artifact_sha256']:raise ValueError('Prior correction changed')
    candidate=apply_patch(database,json.loads(gzip.decompress(prior_raw)),output)
    # Preserve every original binding, inserting only the audited seam additions.
    stream=io.BytesIO(gzip.decompress(raw))
    if stream.read(9)!=b'NCNSEAM1\n':raise ValueError('Unsupported delta format')
    count=0;additions=0;seen=set();targets={}
    with sqlite3.connect(candidate) as db:
        while length:=stream.read(4):
            if len(length)!=4:raise ValueError('Truncated delta header')
            route,way,expected,n=json.loads(stream.read(struct.unpack('<I',length)[0]))
            if (route,way) in seen:raise ValueError('Duplicate source row')
            seen.add((route,way))
            saved=db.execute('SELECT data FROM paths WHERE route=? AND way=?',(route,way)).fetchone()
            if not saved:raise ValueError('Unknown source identity')
            path=json.loads(gzip.decompress(saved[0]));old=path['bindings']
            if digest(old)!=expected:raise ValueError('Integrated source bindings changed')
            if route not in targets:
                targets[route]=list(db.execute('SELECT key,length FROM targets WHERE route=? ORDER BY key',(route,)))
            inserts={}
            for _ in range(n):
                record=stream.read(41)
                if len(record)!=41:raise ValueError('Truncated delta binding')
                position,target,a,z,low,high,mask=struct.unpack('<IIddddB',record)
                if position in inserts or position>=len(old)+n:raise ValueError('Invalid insertion position')
                modes={1:['on_foot'],2:['cycling'],3:['on_foot','cycling'],7:['cycling','on_foot']}.get(mask)
                if not modes or not set(modes)<=set(path['modes']):raise ValueError('Unsupported mode')
                if target>=len(targets[route]):raise ValueError('Unknown target')
                key,total=targets[route][target]
                if not all(math.isfinite(v) for v in (a,z,low,high)) or not 0<=a<z<=path['positions'][-1]+1e-7 or not 0<=min(low,high)<max(low,high)<=total+1e-6:
                    raise ValueError('Nonfinite or out-of-bounds insertion')
                inserts[position]=dict(start=a,end=z,target=key,low=low,high=high,modes=modes)
            iterator=iter(old);bindings=[inserts[i] if i in inserts else next(iterator) for i in range(len(old)+n)]
            if next(iterator,None) is not None:raise ValueError('Existing binding lost')
            path['bindings']=bindings
            db.execute('UPDATE paths SET data=? WHERE route=? AND way=?',(gzip.compress(encoded(path),compresslevel=6,mtime=0),route,way))
            count+=1;additions+=n
        db.commit();db.execute('VACUUM')
    if count!=index['changed_source_rows'] or additions!=index['added_bindings']:raise ValueError('Delta count mismatch')
    if content_sha(candidate)!=index['candidate_content_sha256']:raise ValueError('Candidate reference values differ')
    print('Reproduced all candidate reference values; SQLite byte layout may differ from the original one-pass writer.')
    return candidate


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--index',type=Path,default=ROOT/'.github/ncn-reference/ordinary-seams/index.json')
    a=p.parse_args();print(reproduce(a.database,a.output,a.index))
