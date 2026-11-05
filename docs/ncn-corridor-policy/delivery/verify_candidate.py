"""Independent every-row preservation and actual canonical-union delta check."""
import argparse
import gzip
from itertools import zip_longest
import json
from pathlib import Path
import sqlite3

from scripts.ncn_chapel_correspondence import load_review
from scripts.ncn_deal_correspondence import measure_delta


def verify(before, after):
    review=load_review()
    with sqlite3.connect(Path(before).resolve().as_uri()+'?mode=ro',uri=True) as a, sqlite3.connect(Path(after).resolve().as_uri()+'?mode=ro',uri=True) as b:
        assert b.execute('PRAGMA quick_check').fetchone()==('ok',)
        counts={}
        for table in ('routes','paths','nodes','network','meta','targets','overview'):
            count=0
            for old,new in zip_longest(a.execute('SELECT * FROM '+table+' ORDER BY 1,2'),b.execute('SELECT * FROM '+table+' ORDER BY 1,2')):
                assert old is not None and new is not None, 'Row added/deleted: '+table
                if old!=new:
                    assert table=='paths' and old[:2]==new[:2]==('NCN:1','4471919')
                    first,last=json.loads(gzip.decompress(old[3])),json.loads(gzip.decompress(new[3]))
                    assert first==review['path']
                    assert last['bindings']==first['bindings']+[review['binding']]
                    assert {k:v for k,v in last.items()if k!='bindings'}=={k:v for k,v in first.items()if k!='bindings'}
                    count+=1
            counts[table]=count
        assert counts==dict(routes=0,paths=1,nodes=0,network=0,meta=0,targets=0,overview=0)
    delta=measure_delta(before,{'targets':{t['key']:t for t in review['targets']},'bindings':[review['binding']]})
    expected=json.loads((Path(__file__).parent/'chapel-impact.json').read_text())
    assert all(delta[k]==expected[k] for k in delta)
    assert counts==expected['all_row_diff_counts']
    print(json.dumps({'verified_changed_rows':counts,'delta':delta},sort_keys=True))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before',required=True,type=Path)
    parser.add_argument('--after',required=True,type=Path)
    args=parser.parse_args();verify(args.before,args.after)
