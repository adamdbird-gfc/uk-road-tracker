"""Opt-in revision-4 Chapel Lane candidate; no download, publication or awards.

This reviewed endpoint maps one last source edge to the remaining target span.
It does not change the ordinary compiler's direction or proximity policy.
"""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3

from scripts.ncn_binding_attainability import encoded

ROOT = Path(__file__).resolve().parents[1]
REVIEW = ROOT / 'docs/ncn-corridor-policy/delivery/chapel-review.json'
BASE_SHA = 'be0c803d508ff548db8458a57a2fba18b4f75249f2f9b37401be3526b3cddb2f'
REVIEW_SHA = 'f356c3d4b25410d3e913e07446362e380f76bcd7f5f8205d39f3f652b34c7359'


def load_review():
    raw = REVIEW.read_bytes()
    if hashlib.sha256(raw).hexdigest() != REVIEW_SHA:
        raise ValueError('Pinned independent review changed')
    review = json.loads(raw)
    path = review['path']
    target = review['target']
    neighbour = review['neighbour']
    following = review['following_target']
    # Source and publisher junctions are different representations of the same
    # verified degree-two road junction. Never project beyond either finite edge.
    if (path['way'] != '4471919' or path['route'] != 'NCN:1'
            or path['source_geometry_sha256'] != '2e3d8e9bf009474905358fa47348ead5414890513a0ace39781ad0f508663f26'
            or path['nodes'][-2:] != [11371661294, 27412821]
            or neighbour['way'] != '169004647' or neighbour['nodes'][0] != path['nodes'][-1]
            or neighbour['coordinates'][0] != path['coordinates'][-1]
            or target['coords'][-1] != following['coords'][0]
            or review['target_junction_degree'] != 2
            or path['modes'] != ['on_foot', 'cycling']):
        raise ValueError('Exact reviewed finite junction required')
    last = path['bindings'][-1]
    binding = dict(start=path['positions'][-2], end=path['positions'][-1],
                   target=target['key'], low=last['high'], high=target['length'],
                   modes=path['modes'])
    if (last['target'] != target['key'] or last['end'] != binding['start']
            or binding != review['binding']
            or any(b['end'] > binding['start'] for b in path['bindings'])
            or not 0 < binding['low'] < binding['high']):
        raise ValueError('Non-overlapping exact last-edge ownership required')
    return review


def corrected_path(path, review):
    if path != review['path']:
        raise ValueError('Exact revision-4 source, modes, geometry and bindings required')
    return dict(path, bindings=path['bindings'] + [review['binding']])


def apply(database, output):
    database, output = Path(database), Path(output)
    if output.exists() or output.resolve() == database.resolve():
        raise ValueError('Fresh separate candidate output required')
    if hashlib.sha256(database.read_bytes()).hexdigest() != BASE_SHA:
        raise ValueError('Exact published revision-4 database required')
    review = load_review()
    with sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True) as db:
        for expected in (review['path'], review['neighbour']):
            row = db.execute('SELECT data FROM paths WHERE route=? AND way=?',
                             (expected['route'], expected['way'])).fetchone()
            if row is None or json.loads(gzip.decompress(row[0])) != expected:
                raise ValueError('Reviewed adjacent source changed')
        for t in review['targets']:
            row = db.execute('SELECT length,coords,network FROM targets WHERE route=? AND key=?',
                             ('NCN:1', t['key'])).fetchone()
            if row is None or [row[0], json.loads(row[1]), json.loads(row[2])] != [t['length'], t['coords'], t['network']]:
                raise ValueError('Reviewed target or canonical mapping changed')
    updated = corrected_path(review['path'], review)
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(database, output)
    try:
        with sqlite3.connect(output) as db:
            db.execute('UPDATE paths SET data=? WHERE route=? AND way=?',
                       (gzip.compress(encoded(updated), compresslevel=6, mtime=0), 'NCN:1', '4471919'))
            db.commit()
    except Exception:
        output.unlink(missing_ok=True)
        raise
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(apply(args.database, args.output))
