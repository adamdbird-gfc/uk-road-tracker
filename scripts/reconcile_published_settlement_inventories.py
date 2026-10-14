#!/usr/bin/env python3
"""Repair queue status from validated, already published inventories; no OSM calls."""
import json
import os
import re
from pathlib import Path


def published_inventories(root):
    root = Path(root)
    manifest = json.loads((root / 'settlement-inventory-manifest-v1.json').read_text())
    result = []
    for entry in manifest['inventories']:
        if entry.get('status') != 'available':
            continue
        code = entry['code']
        if not re.fullmatch(r'[EWK][0-9]{8}', code):
            raise ValueError('Unsupported inventory code: ' + code)
        data = json.loads((root / 'settlement-inventories-v1' / (code + '.json')).read_text())
        if (data['code'] != code or type(data['count']) is not int
                or data['count'] != entry['count'] or data['count'] != len(data['roads'])):
            raise ValueError('Published inventory/manifest mismatch: ' + code)
        boundary = json.loads((root / 'settlement-inventories-v1' / (code + '-boundary.geojson')).read_text())
        if boundary['properties']['code'] != code or not boundary.get('geometry'):
            raise ValueError('Published boundary mismatch: ' + code)
        result.append(data)
    return result


def reconcile(root='.'):
    import psycopg
    inventories = published_inventories(root)  # Validate every file before any DB write.
    with psycopg.connect(os.environ['DATABASE_URL']) as conn:
        with conn.cursor() as cur:
            for data in inventories:
                cur.execute(
                    """UPDATE settlement_inventories i
                    SET status = 'ready', road_count = %s,
                        source_key = 'osm-named-roads', source_version = %s,
                        inventory_version = 'v1',
                        completed_at = COALESCE(i.completed_at, NOW()),
                        build_started_at = NULL, failure_code = NULL, updated_at = NOW()
                    WHERE i.settlement_id = (SELECT id FROM settlements WHERE external_code = %s)
                      AND i.status IN ('pending', 'failed', 'building')
                    RETURNING status, road_count""",
                    (data['count'], data.get('source', 'OSM'), data['code']),
                )
                for row in cur.fetchall():
                    print('Reconciled', data['code'], data['name'], row)
            cur.execute("""SELECT i.status, i.road_count FROM settlement_inventories i
                JOIN settlements s ON s.id = i.settlement_id
                WHERE s.external_code = 'K08000001'""")
            rows = cur.fetchall()
            expected = next(data['count'] for data in inventories if data['code'] == 'K08000001')
            if rows != [('ready', expected)]:
                raise ValueError('Chester queue verification failed: ' + repr(rows))
            print('Verified Chester K08000001: ready,', expected, 'roads; no rebuild')
    print('Validated', len(inventories), 'published inventories')


if __name__ == '__main__':
    reconcile()
