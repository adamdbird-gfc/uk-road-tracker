"""Opt-in exact Bradford/Derry candidate plus preserved Chapel Lane; no publication.

No national matching/admission rule changes. The independent review pins every
source row, target, mode and finite correspondence. Input must be revision 4.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3
from scripts.ncn_chapel_correspondence import apply as chapel
from scripts.ncn_binding_attainability import encoded

ROOT=Path(__file__).resolve().parents[1]
REVIEW=ROOT/'docs/ncn-corridor-policy/resolution/review.json'
REVIEW_SHA='e76571514408eaa7b20a7e9cd88acce42cdcd188822cde09948161faee18c9ab'


def load_review():
    raw=REVIEW.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=REVIEW_SHA:
        raise ValueError('Exact independent finite review required')
    return json.loads(raw)


def corrected_path(path,case):
    if path!=case['path']:
        raise ValueError('Source identity, geometry, nodes, route, modes or prior bindings changed')
    updated=dict(path,bindings=path['bindings']+case['bindings'])
    ordered=sorted(updated['bindings'],key=lambda b:b['start'])
    targets={t['key']:t for t in case['targets']}
    for b in case['bindings']:
        if (b['modes']!=path['modes'] or not 0<=b['start']<b['end']<=path['positions'][-1]
                or not 0<=min(b['low'],b['high'])<max(b['low'],b['high'])<=targets[b['target']]['length']):
            raise ValueError('Finite source and target bounds required')
    if any(a['end']>b['start']+1e-7 for a,b in zip(ordered,ordered[1:])):
        raise ValueError('Source ownership overlap')
    return updated


def apply(database,output):
    review=load_review();database=Path(database);output=Path(output)
    if output.exists() or output.resolve()==database.resolve():
        raise ValueError('Fresh separate output required')
    if hashlib.sha256(database.read_bytes()).hexdigest()!=review['base_sha256']:
        raise ValueError('Exact unchanged published revision 4 required')
    updates=[]
    with sqlite3.connect(database.resolve().as_uri()+'?mode=ro',uri=True) as db:
        for case in review['cases']:
            row=db.execute('SELECT data FROM paths WHERE route=? AND way=?',(case['route'],case['way'])).fetchone()
            if row is None:raise ValueError('Pinned source absent')
            updates.append(corrected_path(json.loads(gzip.decompress(row[0])),case))
            for t in case['targets']:
                row=db.execute('SELECT length,coords,network FROM targets WHERE route=? AND key=?',(t['route'],t['key'])).fetchone()
                if row is None or [row[0],json.loads(row[1]),json.loads(row[2])]!=[t['length'],t['coords'],t['network']]:
                    raise ValueError('Pinned target or canonical mapping changed')
    # Existing compiler independently verifies and preserves Chapel Lane.
    chapel(database,output)
    try:
        with sqlite3.connect(output) as db:
            for path in updates:
                db.execute('UPDATE paths SET data=? WHERE route=? AND way=?',(gzip.compress(encoded(path),compresslevel=6,mtime=0),path['route'],path['way']))
            db.commit()
    except Exception:
        output.unlink(missing_ok=True);raise
    return output


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(apply(a.database,a.output))
