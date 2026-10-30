"""Reproduce the reviewed seam candidate from bounded, checksummed Git chunks."""
import argparse
import base64
import gzip
import hashlib
import json
from pathlib import Path
from scripts.ncn_binding_attainability import apply_patch, content_sha

ROOT=Path(__file__).resolve().parents[1]


def read_patch(index_path):
    index_path=Path(index_path);index=json.loads(index_path.read_text());parts=[]
    for row in index['parts']:
        path=(index_path.parent/row['file']).resolve()
        if not path.is_relative_to(index_path.parent.resolve()):raise ValueError('Chunk escapes artifact directory')
        raw=path.read_bytes()
        if hashlib.sha256(raw).hexdigest()!=row['sha256']:raise ValueError('Correction chunk changed')
        parts.append(base64.b64decode(raw,validate=True))
    raw=b''.join(parts)
    if hashlib.sha256(raw).hexdigest()!=index['artifact_sha256']:raise ValueError('Correction artifact changed')
    patch=json.loads(gzip.decompress(raw))
    if patch['implementation_commit']!='c0566c6421a2d2b155f738e0411a526143bc385c':raise ValueError('Wrong implementation')
    return patch


def reproduce(database,output,index_path=ROOT/'.github/ncn-reference/ordinary-seams/index.json'):
    patch=read_patch(index_path)
    candidate=apply_patch(database,patch,output)
    if content_sha(candidate)!=patch['candidate_content_sha256']:raise ValueError('Reference values differ')
    return candidate


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--index',type=Path,default=ROOT/'.github/ncn-reference/ordinary-seams/index.json')
    a=p.parse_args();print(reproduce(a.database,a.output,a.index))
