#!/usr/bin/env python3
"""Build a partial map-only beta asset from dated individual venue reviews."""
import argparse
from datetime import date
import hashlib
import json
import math
from pathlib import Path


def build(catalogue, reviews, as_of):
    if catalogue.get('collection_id') != 'wetherspoons' or catalogue.get('scope') != 'UK':
        raise ValueError('Wrong candidate collection/scope')
    if reviews.get('format') != 1 or reviews.get('collection_id') != 'wetherspoons':
        raise ValueError('Wrong review ledger')
    rows = catalogue['venues']
    by_id = {v['id']: v for v in rows}
    if len(by_id) != len(rows):
        raise ValueError('Duplicate candidate identity')
    seen, active, excluded, pending = set(), [], [], []
    for review in reviews['venues']:
        ident = review['id']
        if ident in seen:
            raise ValueError('Duplicate review identity')
        seen.add(ident)
        if review['decision'] not in ('active_map', 'exclude_current'):
            raise ValueError('Unknown review decision')
        if not review.get('evidence_url', '').startswith('https://'):
            raise ValueError('Review requires source link')
        age = (date.fromisoformat(as_of) - date.fromisoformat(review['reviewed_at'])).days
        if age < 0:
            raise ValueError('Future review')
        if review['decision'] == 'exclude_current':
            # Tombstones survive source omission; an OSM refresh cannot reactivate them.
            excluded.append(ident)
            continue
        row = by_id.get(ident)
        if not row or row['status'] != 'candidate' or age > 90:
            pending.append(ident)
            continue
        # Renamed/moved/replaced source objects require a new review.
        if (row['name'] != review['source_name'] or
                not set(review['osm_aliases']).intersection(row['osm_aliases']) or
                abs(row['lat'] - review['lat']) > .0005 or
                abs(row['lon'] - review['lon']) > .0005):
            pending.append(ident)
            continue
        lat, lon = row['lat'], row['lon']
        if (not all(isinstance(n, (int, float)) and not isinstance(n, bool) and math.isfinite(n) for n in (lat, lon))
                or not 49 <= lat <= 61 or not -9 <= lon <= 2 or not row.get('county')):
            raise ValueError('Invalid UK map position/county')
        active.append({k: row[k] for k in ('id', 'name', 'lat', 'lon', 'town', 'county', 'osm_aliases')} |
                      {'status': 'active', 'operating_status': 'reviewed_current',
                       'map_reviewed_at': review['reviewed_at'], 'visit_enabled': False})
    active.sort(key=lambda v: v['id'])
    remaining = len([v for v in rows if v['id'] not in excluded and v['id'] not in {a['id'] for a in active}])
    digest = hashlib.sha256(json.dumps({'source': catalogue['catalogue_version'], 'reviews': reviews,
                                       'as_of': as_of, 'venues': active}, sort_keys=True).encode()).hexdigest()
    return {'schema_version': 1, 'collection_id': 'wetherspoons', 'display_name': 'UK Wetherspoons Collection',
            'scope': 'UK', 'release_status': 'approved', 'approval_scope': 'map_only_partial_beta',
            'ready_for_app': True, 'coverage_complete': False, 'visit_enabled': False,
            'completion_target': None, 'catalogue_version': as_of + '.' + digest[:12],
            'candidate_version': catalogue['catalogue_version'], 'source': catalogue['source'],
            'attribution': '© OpenStreetMap contributors · ODbL 1.0',
            'review_summary': {'map_venues': len(active), 'excluded_current': len(excluded),
                               'unreviewed_candidates': remaining, 'needs_rereview': pending},
            'venues': active}


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--catalogue', type=Path, required=True)
    p.add_argument('--reviews', type=Path, required=True)
    p.add_argument('--as-of', required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    output = build(json.loads(args.catalogue.read_text()), json.loads(args.reviews.read_text()), args.as_of)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n')
    print(output['review_summary'])
