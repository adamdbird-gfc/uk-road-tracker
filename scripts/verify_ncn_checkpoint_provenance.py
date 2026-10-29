"""Read-only link between the historical checkpoint and Android's pinned catalogue.

Run as python -m scripts.verify_ncn_checkpoint_provenance CHECKPOINT_ROOT DATABASE.
No network, writes to the catalogue, source admissions or publishing operations.
"""
import argparse
import gzip
import hashlib
import json
import sqlite3
from pathlib import Path

from scripts.ncn_reference import verify_database
from scripts.ncn_trial.completion_checkpoint import pinned, check_registry, read_checkpoint_route

INPUT_PINS = 'a706b3ce8c0307c5410954045e68d938ae7f2616dc8c6e32c29b48ca5b8d0641'


def verify(root, database, manifest_path):
    root, database = Path(root), Path(database)
    manifest = json.loads(Path(manifest_path).read_text())
    verify_database(database, manifest)
    sha = lambda raw: hashlib.sha256(raw).hexdigest()
    pins_raw = (root / 'inputs/input-pins.json').read_bytes()
    if sha(pins_raw) != INPUT_PINS:
        raise ValueError('Historical checkpoint input pins differ')
    pins = json.loads(pins_raw)
    files = {r['file']: pinned(root / 'inputs', r) for r in pins['files']}
    if len(files) != len(pins['files']):
        raise ValueError('Duplicate checkpoint input')
    check_registry(root / 'inputs', pins, files)
    summary_raw = (root / 'plans/summary.json').read_bytes()
    if sha(summary_raw) != manifest['checkpoint_sha256']:
        raise ValueError('Catalogue uses a different completion checkpoint')
    summary = json.loads(summary_raw)
    rows = 0
    with sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True) as db:
        for record in summary['routes']:
            folder = root / 'plans' / record['route_key'].replace(':', '-')
            _, paths, _, saved_manifest = read_checkpoint_route(
                folder, sha((folder / 'manifest.json').read_bytes()))
            if saved_manifest != record:
                raise ValueError('Route receipt differs from pinned summary')
            for path in paths:
                saved = db.execute('SELECT data FROM paths WHERE route=? AND way=?',
                                   (record['route_key'], str(path.way_id))).fetchone()
                if not saved:
                    raise ValueError('Admitted source way missing from catalogue')
                data = json.loads(gzip.decompress(saved[0]))
                if (data['coordinates'] != [list(c) for c in path.coordinates]
                        or data['nodes'] != list(path.osm_node_ids)
                        or set(data['modes']) != set(path.modes)):
                    raise ValueError('Source geometry, node identity or eligible modes changed')
                rows += 1
        keys = {r['route_key'] for r in summary['routes']}
        if (keys != {r[0] for r in db.execute('SELECT key FROM routes')}
                or rows != db.execute('SELECT COUNT(*) FROM paths').fetchone()[0]
                or manifest['held_route_keys'] != summary['held_route_keys']):
            raise ValueError('Catalogue/checkpoint identity partition differs')
    return {'input_files_verified': len(files), 'route_manifests_read_back': len(keys),
            'exact_route_way_rows_compared': rows,
            'identical_fields': ['coordinates', 'osm_nodes', 'modes'],
            'database_sha256': manifest['sha256'], 'checkpoint_sha256': sha(summary_raw),
            'held_identities': len(summary['held_route_keys']), 'result': 'pass'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('checkpoint_root', type=Path)
    parser.add_argument('database', type=Path)
    parser.add_argument('--manifest', type=Path, default=Path(__file__).resolve().parents[1] /
                        'android-prototype/app/src/main/assets/ncn-national/manifest.json')
    args = parser.parse_args()
    print(json.dumps(verify(args.checkpoint_root, args.database, args.manifest), indent=2))
