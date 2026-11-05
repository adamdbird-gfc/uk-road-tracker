"""Independent every-row preservation, per-route union and actual canonical union.

Scans the complete original and candidate, including other routes sharing an
edge. No sum of route-local lengths is reported as unique network recovery.
"""
import argparse
from collections import defaultdict
import gzip
import hashlib
from itertools import zip_longest
import json
from pathlib import Path
import sqlite3
from scripts.ncn_binding_attainability import network_intervals
from scripts.ncn_trial.coverage import merge_intervals
from scripts.ncn_chapel_correspondence import load_review as chapel_review
from scripts.ncn_five_case_correspondence import load_review


def verify(before,after):
    review=load_review();chapel=chapel_review()
    expected={(c['route'],c['way']):(c['path'],c['bindings'])for c in review['cases']}
    expected['NCN:1','4471919']=(chapel['path'],[chapel['binding']])
    additions=defaultdict(set)
    for (route,way),(old,bindings)in expected.items():
        additions[route].update(b['target']for b in bindings)
    def connect(path):return sqlite3.connect(Path(path).resolve().as_uri()+'?mode=ro',uri=True)
    with connect(before)as a,connect(after)as b:
        assert hashlib.sha256(Path(before).read_bytes()).hexdigest()==review['base_sha256']
        assert b.execute('PRAGMA quick_check').fetchone()==('ok',)
        counts={};changed=[]
        for table in ('routes','paths','nodes','network','meta','targets','overview'):
            count=0
            for old,new in zip_longest(a.execute('SELECT * FROM '+table+' ORDER BY 1,2'),b.execute('SELECT * FROM '+table+' ORDER BY 1,2')):
                assert old is not None and new is not None,'Added/deleted row: '+table
                if old!=new:
                    assert table=='paths'and old[:3]==new[:3]and old[:2]in expected
                    first,last=json.loads(gzip.decompress(old[3])),json.loads(gzip.decompress(new[3]))
                    pinned,extra=expected[old[:2]]
                    assert first==pinned
                    assert last==dict(first,bindings=first['bindings']+extra)
                    changed.append(list(old[:2]));count+=1
            counts[table]=count
        assert counts==dict(routes=0,paths=3,nodes=0,network=0,meta=0,targets=0,overview=0)
        targets=[dict(route=r,key=k,length=l,network=json.loads(n))for r,k,l,n in a.execute('SELECT route,key,length,network FROM targets')]
        affected={p[0]for t in targets if t['key']in additions[t['route']]for p in t['network']}
        wanted={(t['route'],t['key'])for t in targets if any(p[0]in affected for p in t['network'])}
        totals=[];local=[];capacity=[]
        for db in (a,b):
            coverage=defaultdict(list)
            for route,raw in db.execute('SELECT route,data FROM paths'):
                for binding in json.loads(gzip.decompress(raw))['bindings']:
                    coverage[route,binding['target']].append(sorted([binding['low'],binding['high']]))
            mapped=defaultdict(list);by_route=defaultdict(float);full=defaultdict(float)
            for t in targets:
                intervals=merge_intervals(coverage.get((t['route'],t['key']),[]))
                if (t['route'],t['key'])in wanted:
                    network_intervals([t],{t['key']:intervals},mapped)
                by_route[t['route']]+=sum(z-a for a,z in intervals)
                full[t['route']]+=t['length']
            totals.append(sum(z-a for edge,spans in mapped.items()if edge in affected for a,z in merge_intervals(spans)))
            local.append(by_route);capacity.append(full)
    routes={r:dict(before_m=local[0][r],after_m=local[1][r],added_m=local[1][r]-local[0][r],target_m=capacity[0][r],remaining_m=capacity[0][r]-local[1][r])for r in ('NCN:1','NCN:66','NCN:92')}
    delta=totals[1]-totals[0]
    seam=json.loads((Path(__file__).resolve().parents[2]/'data-trials/ncn-ordinary-seam-validation-2026-10-30.json').read_text())['after_network']
    deal=json.loads((Path(__file__).resolve().parents[2]/'data-trials/ncn-deal-resolution-20261031.json').read_text())['distance_effect']
    baseline=seam['combined_m']+deal['unique_network_added_m'];denominator=seam['target_m']
    assert all(r['remaining_m']>1 for r in routes.values())
    return dict(changed_rows=changed,all_row_diff_counts=counts,routes=routes,affected_canonical_edges=len(affected),unique_network_before_affected_m=totals[0],unique_network_after_affected_m=totals[1],unique_network_added_m=delta,national_denominator_m=denominator,national_assessable_before_m=baseline,national_assessable_after_m=baseline+delta,national_assessability_before_percent=100*baseline/denominator,national_assessability_after_percent=100*(baseline+delta)/denominator,newly_fully_attainable_routes=0,live_change_m=0,before_sha256=hashlib.sha256(Path(before).read_bytes()).hexdigest(),candidate_sha256=hashlib.sha256(Path(after).read_bytes()).hexdigest())

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    args=p.parse_args();result=verify(args.before,args.after);args.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n');print(json.dumps(result,indent=2))
