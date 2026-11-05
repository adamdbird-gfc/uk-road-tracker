"""Read only the twelve reviewed cases from the exact revision-4 artifact.

No network, build, source admission, national discovery or entitlement writes.
"""
import argparse
from collections import defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import sqlite3

from scripts.ncn_trial.coverage import merge_intervals

HERE = Path(__file__).resolve().parent
BASE_SHA = 'be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f'
WAYS = ['30268009', '463641622', '4471747', '4471919', '23385711',
        '1289002662', '1050757473', '59306268', '31252441', '44807950',
        '159311690', '77611523']


def export(database):
    database = Path(database)
    if hashlib.sha256(database.read_bytes()).hexdigest() != BASE_SHA:
        raise ValueError('Exact published revision 4 required')
    evidence = json.loads((HERE.parent/'evidence.json').read_text())
    selected = {(c['route'], t['key']) for c in evidence['cases'] for t in c['targets']}
    with sqlite3.connect(database.resolve().as_uri()+'?mode=ro', uri=True) as db:
        if db.execute('PRAGMA quick_check').fetchone() != ('ok',):
            raise ValueError('Database integrity differs')
        paths = {}
        for way in WAYS + ['169004647', '160017281', '674543498']:
            paths[way] = [json.loads(gzip.decompress(row[0])) for row in
                          db.execute('SELECT data FROM paths WHERE way=? ORDER BY route', (way,))]
        targets = []
        for route, key, length, coords, network in db.execute('SELECT * FROM targets ORDER BY route,key'):
            coords = json.loads(coords)
            cumbria = route in ('NCN:6', 'NCN:7') and any(-2.926 < p[0] < -2.921 and 54.642 < p[1] < 54.654 for p in coords)
            if (route, key) in selected or cumbria:
                targets.append(dict(route=route, key=key, length=length, coords=coords, network=json.loads(network)))
        selected = {(t['route'], t['key']) for t in targets}
        coverage = defaultdict(list)
        contributors = defaultdict(set)
        for route, way, raw in db.execute('SELECT route,way,data FROM paths'):
            path = json.loads(gzip.decompress(raw))
            for binding in path['bindings']:
                key = route, binding['target']
                if key in selected:
                    coverage[key].append(sorted([binding['low'], binding['high']]))
                    contributors[key].add(way)
        for target in targets:
            key = target['route'], target['key']
            target['current_route_coverage'] = merge_intervals(coverage[key])
            target['contributing_source_ways'] = sorted(contributors[key])
        screened = []
        for case in evidence['cases']:
            for row in case['candidates']:
                low, high = row['low_m'], row['high_m']
                covered = sum(max(0, min(high,z)-max(low,a)) for a,z in merge_intervals(coverage[case['route'], row['target_key']]))
                screened.append(dict(id=row['id'], route=case['route'], way=case['way'],
                                     target=row['target_key'], interval=[low,high],
                                     admitted=row['candidate_edge_within_admitted_section'],
                                     screened_m=high-low, remaining_route_local_m=max(0,high-low-covered)))
        return dict(reference_revision=4, database_sha256=BASE_SHA,
                    integrity='ok', paths=paths, targets=targets, screened=screened,
                    note='Source bindings are reference assessability, not user completion. Missing path is explicit.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(export(args.database), indent=2, sort_keys=True)+'\n')
