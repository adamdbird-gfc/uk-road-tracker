"""Capture resumable, pinned OSM node evidence for explicit reconciliation queues.

Selecting an offset/extent review queue never approves that queue's geometry.
The downstream source-span checks and published-scope holds remain unchanged.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import gzip
import hashlib
import json
from pathlib import Path
from urllib.request import Request, urlopen

STATES = ('close_bidirectional_correspondence_candidate',
          'offset_sensitive_correspondence_review', 'partial_or_different_extent_review')
MAX_RESPONSE = 20_000_000


def select_sources(report, profiles, states):
    if not states or set(states) - set(STATES):
        raise ValueError('Explicit supported review states required')
    rows = [r for r in report['routes'] if r['state'] in states]
    if not rows or len({r['official_route_key'] for r in rows}) != len(rows):
        raise ValueError('Distinct route review rows required')
    if any(not r['source_geometry_complete'] or not r['uk_metadata_anchor']
           or r.get('comparison_error') for r in rows):
        raise ValueError('Complete, UK-anchored source candidates required')
    seeds = {rid for r in rows for rid in r['candidate_relation_ids']}
    by_id = {p['relation_id']: p for p in profiles}
    if not seeds or not seeds <= by_id.keys():
        raise ValueError('Candidate membership profiles missing')
    if any(not by_id[rid]['member_lists_complete'] for rid in seeds):
        raise ValueError('Complete member lists required')
    ids = sorted({v['id'] for rid in seeds for v in by_id[rid]['source_relations']})
    return rows, sorted(seeds), ids


def verify_snapshot(raw, identity, expected=None):
    if len(raw) > MAX_RESPONSE:
        raise ValueError('Response exceeds source capture limit')
    sha = hashlib.sha256(raw).hexdigest()
    if expected and sha != expected:
        raise ValueError('Pinned source snapshot changed')
    data = json.loads(raw)
    if data.get('remark') or data.get('error') or not any(
            e['type'] == 'relation' and e['id'] == identity for e in data['elements']):
        raise ValueError('Incomplete or wrong source relation')
    return sha


def verified_sources(directory, manifest):
    """Read exactly the requested, complete set; reject truncated manifests."""
    requested = manifest.get('requested_relation_ids', [])
    records = manifest.get('sources', [])
    ids = [r['relation_id'] for r in records]
    if (not manifest.get('complete') or manifest.get('errors') or not requested
            or len(set(requested)) != len(requested) or len(set(ids)) != len(ids)
            or set(ids) != set(requested)):
        raise ValueError('Complete distinct pinned source responses required')
    raws = []
    for record in records:
        path = (directory / record['file']).resolve()
        if not path.is_relative_to(directory.resolve()):
            raise ValueError('Source file escapes capture directory')
        raw = gzip.decompress(path.read_bytes())
        verify_snapshot(raw, record['relation_id'], record['sha256'])
        raws.append(raw)
    return raws


def capture(root, output, states, reuse=None):
    report_raw = (root / 'physical-national/summary.json').read_bytes()
    profile_raw = gzip.decompress((root / 'numeric-route-membership-profiles.json.gz').read_bytes())
    rows, seeds, ids = select_sources(json.loads(report_raw), json.loads(profile_raw)['profiles'], states)
    output.mkdir(parents=True, exist_ok=True)
    (output / 'relations').mkdir(exist_ok=True)
    manifest = output / 'fetch-manifest.json'
    prior = json.loads(manifest.read_bytes()) if manifest.exists() else {}
    reuse_manifest = json.loads((reuse / 'fetch-manifest.json').read_bytes()) if reuse else {}
    old = {r['relation_id']: r for r in prior.get('sources', [])}
    reusable = {r['relation_id']: r for r in reuse_manifest.get('sources', [])}

    def fetch(identity):
        url = f'https://api.openstreetmap.org/api/0.6/relation/{identity}/full.json'
        path = output / 'relations' / f'{identity}.json.gz'
        try:
            previous = old.get(identity)
            if path.exists():
                if previous is None:
                    raise ValueError('Unpinned cached source; remove or explicitly reconcile')
                raw = gzip.decompress(path.read_bytes())
            elif identity in reusable:
                previous = reusable[identity]
                raw = gzip.decompress((reuse / previous['file']).read_bytes())
            else:
                with urlopen(Request(url, headers={'User-Agent': 'Roadprints read-only NCN reconciliation'}), timeout=45) as response:
                    raw = response.read(MAX_RESPONSE + 1)
            sha = verify_snapshot(raw, identity, previous['sha256'] if previous else None)
            if not path.exists():
                temporary = path.with_suffix('.tmp')
                temporary.write_bytes(gzip.compress(raw, mtime=0))
                temporary.replace(path)
            return {'relation_id': identity, 'url': url, 'file': 'relations/' + path.name,
                    'sha256': sha, 'bytes': len(raw),
                    'observed_at_utc': previous['observed_at_utc'] if previous else datetime.now(timezone.utc).isoformat()}
        except Exception as error:
            return {'relation_id': identity, 'url': url, 'error': str(error)}

    # Retain all pinned cache rows at every checkpoint, including ones not yet
    # visited on a resumed run. Only the selected IDs enter this manifest.
    records = {i: r for i, r in old.items() if i in ids}
    errors = {}
    with ThreadPoolExecutor(max_workers=2) as pool:
        for count, record in enumerate(pool.map(fetch, ids), 1):
            identity = record['relation_id']
            if 'error' in record:
                errors[identity] = record
                records.pop(identity, None)
            else:
                records[identity] = record
            value = {'schema_version': 1, 'scope': 'Pinned source evidence, not route or branch approval',
                     'selected_states': sorted(states), 'routes': sorted(r['official_route_key'] for r in rows),
                     'seed_relation_ids': seeds, 'requested_relation_ids': ids,
                     'inputs': {'physical_report_sha256': hashlib.sha256(report_raw).hexdigest(),
                                'membership_profiles_sha256': hashlib.sha256(profile_raw).hexdigest()},
                     'sources': [records[i] for i in sorted(records)],
                     'errors': [errors[i] for i in sorted(errors)],
                     'complete': count == len(ids) and not errors and len(records) == len(ids)}
            temporary = manifest.with_suffix('.tmp')
            temporary.write_text(json.dumps(value, indent=2) + '\n')
            temporary.replace(manifest)
            if count % 10 == 0 or count == len(ids):
                print(f'Source snapshots checked: {count}/{len(ids)}; errors: {len(errors)}', flush=True)
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--research-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--reuse', type=Path)
    parser.add_argument('--states', nargs='+', choices=STATES, required=True)
    args = parser.parse_args()
    result = capture(args.research_root, args.output, args.states, args.reuse)
    if not result['complete']:
        raise SystemExit('Source capture incomplete; inspect manifest and resume')


if __name__ == '__main__':
    main()
