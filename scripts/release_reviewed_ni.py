#!/usr/bin/env python3
"""Publish the exact verified NI version and atomically add its reference records."""
import argparse
from datetime import datetime, timezone
import gzip
import hashlib
import json
import os
from pathlib import Path
import sys

from preview_ni_reference_data import Hosting, SITE, CHANNEL, MANIFEST, sha
from release_reviewed_gb import Fetcher, api_json, check_database
from verify_firebase_reference_data import verify
from verify_gb_preview import verify_batches

PREVIEW = 'https://roadprints-data-822cc--uk-ni-reviewed-20261015-97dbx27n.web.app'
LIVE = 'https://' + SITE + '.web.app'
VERSION = 'sites/' + SITE + '/versions/3d258a98fc0a74c1'
BASE = 'sites/' + SITE + '/versions/7eb7c735fee87d4c'
FILE_MAP = '6c4ac444888ae42b00a4e4a95aab451ae666dfb1fd4be84b4f9e55bc0b3bc474'
BASE_MAP = '2b0d77d7cae1f270e8339cb43e5fa833a217cc4a370ce10590b0de468e9debd8'
CONFIG = '220c245af2bf1331df59ac122c71ee4ebd6cbdb9a842d6c46f7a4eb45fd4d2a7'
REPORT = '8721b48b05b791bdca92b18a65b4da8acb5d72234b078a5e08207a8b6b78c6e0'
METADATA = 'bb2502bbaacb0571dc8d75c098e390b79240e598e50e4f43ba4b69fe2bd826d2'
SOURCE = 'nisra_sdl_2015'


def map_hash(value):
    return sha(json.dumps(value, sort_keys=True).encode())


def load_inputs(root=Path('.github/ni-live')):
    raw = gzip.decompress((root / 'additions-report.json.gz').read_bytes())
    if sha(raw) != REPORT:
        raise ValueError('Reviewed additions report changed')
    report = json.loads(raw)
    if len(report['files']) != 1055 or report['total_bytes'] != 12642492:
        raise ValueError('Incomplete additions report')
    raw = gzip.decompress(Path('.github/ni-preview/ni-metadata.json.gz').read_bytes())
    if sha(raw) != METADATA:
        raise ValueError('Reviewed metadata changed')
    return report, json.loads(raw)['settlements']


def load_public(url, report, metadata, fetch):
    """Download only NI boundaries and manifest, checking every reviewed digest."""
    files = {r['path']: r for r in report['files']}
    def get(path):
        row = files[path]
        status, _, raw = fetch(url + '/' + path, row['bytes'])
        if status != 200 or len(raw) != row['bytes'] or sha(raw) != row['sha256']:
            raise ValueError('Reviewed public input changed')
        return json.loads(raw)
    manifest = get(MANIFEST)
    counts = {}
    for row in manifest['inventories']:
        code, count = row['code'], row['count']
        if code in counts or type(count) is not int or count < 0 or row['status'] != 'available':
            raise ValueError('Invalid combined inventory manifest')
        counts[code] = count
    ni = {r['code'] for r in metadata}
    if len(ni) != 527 or len(counts) != 9072 or {c for c in counts if c.startswith('N')} != ni:
        raise ValueError('Incomplete UK catalogue')
    boundaries = {}
    from concurrent.futures import ThreadPoolExecutor
    def boundary(row):
        code = row['code']
        feature = get('settlement-inventories-v1/' + code + '-boundary.geojson')
        if feature['properties'] != {'code': code, 'name': row['name']}:
            raise ValueError('Boundary identity changed')
        return code, feature['geometry']
    with ThreadPoolExecutor(max_workers=4) as pool:
        boundaries.update(pool.map(boundary, metadata))
    return counts, boundaries


def preserved_state(cursor):
    """Hash complete non-NI shared-reference rows, including IDs and timestamps."""
    queries = {
        'settlements': "SELECT s.* FROM settlements s LEFT JOIN reference_sources r ON r.id=s.source_id WHERE r.source_key IS DISTINCT FROM %s",
        'aliases': "SELECT a.* FROM settlement_aliases a JOIN settlements s ON s.id=a.settlement_id LEFT JOIN reference_sources r ON r.id=s.source_id WHERE r.source_key IS DISTINCT FROM %s",
        'boundaries': "SELECT b.* FROM settlement_boundaries b JOIN settlements s ON s.id=b.settlement_id LEFT JOIN reference_sources r ON r.id=s.source_id WHERE r.source_key IS DISTINCT FROM %s",
        'inventories': "SELECT i.* FROM settlement_inventories i JOIN settlements s ON s.id=i.settlement_id LEFT JOIN reference_sources r ON r.id=s.source_id WHERE r.source_key IS DISTINCT FROM %s",
        'roads': "SELECT i.* FROM settlement_inventory_roads i JOIN settlements s ON s.id=i.settlement_id LEFT JOIN reference_sources r ON r.id=s.source_id WHERE r.source_key IS DISTINCT FROM %s",
        'sources': 'SELECT * FROM reference_sources WHERE source_key <> %s',
        'loads': 'SELECT l.* FROM reference_data_loads l JOIN reference_sources r ON r.id=l.source_id WHERE r.source_key <> %s',
        'road_references': 'SELECT * FROM road_references WHERE %s IS NOT NULL',
    }
    result = {}
    for key, query in queries.items():
        cursor.execute("SELECT count(*),md5(coalesce(string_agg(h,',' ORDER BY h),'')) FROM (SELECT md5(row_to_json(t)::text) h FROM (" + query + ') t) hashes', (SOURCE,))
        result[key] = cursor.fetchone()
    return result


def register(connection, metadata, boundaries, counts, write=False):
    """No GB writes; any conflict or failed postcondition rolls back all NI rows."""
    ni = {r['code']: r for r in metadata}
    gb = {c: n for c, n in counts.items() if c not in ni}
    with connection.cursor() as c:
        c.execute("SET LOCAL lock_timeout='15s'")
        c.execute("SET LOCAL statement_timeout='120s'")
        if write:
            c.execute('LOCK TABLE reference_sources,settlements,settlement_aliases,settlement_boundaries,settlement_inventories,settlement_inventory_roads,reference_data_loads,road_references IN SHARE ROW EXCLUSIVE MODE')
        c.execute('''SELECT s.external_code,i.status,i.road_count FROM settlements s
                     LEFT JOIN settlement_inventories i ON i.settlement_id=s.id
                     WHERE s.is_active AND s.external_code NOT LIKE 'N%' ORDER BY s.external_code''')
        rows = c.fetchall()
        check_database(rows, gb)
        if any(status != 'ready' for _, status, _ in rows):
            raise ValueError('GB reference baseline is not fully ready')
        c.execute('''SELECT s.external_code,r.source_key FROM settlements s
                     LEFT JOIN reference_sources r ON r.id=s.source_id
                     WHERE s.external_code LIKE 'N%' OR r.source_key=%s''', (SOURCE,))
        seen = set()
        for code, source in c.fetchall():
            if code not in ni or source != SOURCE or code in seen:
                raise ValueError('NI reference identity conflict')
            seen.add(code)
        if not write:
            return {'gb_settlements_preserved': len(gb), 'ni_already_present': len(seen)}
        before = preserved_state(c)
        c.execute('''INSERT INTO reference_sources (source_key,display_name,source_version,licence,notes)
                     VALUES (%s,'NISRA Settlement Development Limits 2015','2015','UK Open Government Licence',
                     'NISRA SDL15; reviewed WGS84 boundary SHA-256 2320848e0ccfe9f13e4c0c2b25ae047d5217ceff10d27ee3d74ac07badb344c0. Road counts: OSM/Geofabrik 2026-10-14, ODbL.')
                     ON CONFLICT (source_key) DO NOTHING''', (SOURCE,))
        c.execute('SELECT id,source_version FROM reference_sources WHERE source_key=%s', (SOURCE,))
        source_id, version = c.fetchone()
        if version != '2015':
            raise ValueError('NI source version conflict')
        c.execute('''CREATE TEMP TABLE ni_release (code text PRIMARY KEY,name text,nation text,lon double precision,lat double precision,
                     boundary jsonb,road_count integer) ON COMMIT DROP''')
        from psycopg.types.json import Jsonb
        with c.copy('COPY ni_release FROM STDIN') as copy:
            for code, row in sorted(ni.items()):
                copy.write_row((code,row['name'],'Northern Ireland',row['centre'][1],row['centre'][0],Jsonb(boundaries[code]),counts[code]))
        c.execute('''SELECT count(*) FROM ni_release WHERE NOT ST_IsValid(ST_SetSRID(ST_GeomFromGeoJSON(boundary),4326))
                     OR ST_IsEmpty(ST_GeomFromGeoJSON(boundary)) OR NOT ST_Covers(ST_SetSRID(ST_GeomFromGeoJSON(boundary),4326),ST_SetSRID(ST_MakePoint(lon,lat),4326))''')
        if c.fetchone()[0]:
            raise ValueError('Invalid NI boundary or centre')
        c.execute('''INSERT INTO settlements (source_id,external_code,name,nation,region,county,is_active,centre)
                     SELECT %s,code,name,nation,NULL,NULL,true,ST_SetSRID(ST_MakePoint(lon,lat),4326) FROM ni_release
                     ON CONFLICT (source_id,external_code) DO NOTHING''', (source_id,))
        c.execute('''INSERT INTO settlement_boundaries (settlement_id,source_id,geometry)
                     SELECT s.id,%s,ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(r.boundary),4326))
                     FROM ni_release r JOIN settlements s ON s.external_code=r.code AND s.source_id=%s
                     ON CONFLICT (settlement_id) DO NOTHING''', (source_id,source_id))
        c.execute('''INSERT INTO settlement_aliases (settlement_id,alias)
                     SELECT s.id,r.name FROM ni_release r JOIN settlements s ON s.external_code=r.code AND s.source_id=%s
                     ON CONFLICT (settlement_id,alias) DO NOTHING''', (source_id,))
        c.execute('''INSERT INTO settlement_inventories (settlement_id,status,road_count,source_key,source_version,inventory_version,completed_at)
                     SELECT s.id,'ready',r.road_count,'osm-named-roads','ni-reviewed-2026-10-14','v1',NOW()
                     FROM ni_release r JOIN settlements s ON s.external_code=r.code AND s.source_id=%s
                     ON CONFLICT (settlement_id) DO NOTHING''', (source_id,))
        c.execute('''SELECT count(*) FROM ni_release r JOIN settlements s ON s.external_code=r.code AND s.source_id=%s
                     JOIN settlement_boundaries b ON b.settlement_id=s.id JOIN settlement_inventories i ON i.settlement_id=s.id
                     WHERE s.name=r.name AND s.nation=r.nation AND s.region IS NULL AND s.county IS NULL AND s.is_active
                     AND ST_Equals(s.centre,ST_SetSRID(ST_MakePoint(r.lon,r.lat),4326)) AND b.source_id=%s
                     AND ST_Equals(b.geometry,ST_SetSRID(ST_GeomFromGeoJSON(r.boundary),4326))
                     AND i.status='ready' AND i.road_count=r.road_count AND i.source_key='osm-named-roads'
                     AND i.source_version='ni-reviewed-2026-10-14' AND i.inventory_version='v1'
                     AND EXISTS (SELECT 1 FROM settlement_aliases a WHERE a.settlement_id=s.id AND a.alias=r.name)''', (source_id,source_id))
        if c.fetchone()[0] != len(ni):
            raise ValueError('NI registration differs from reviewed data')
        c.execute('''INSERT INTO reference_data_loads (source_id,dataset_key,checksum,row_count)
                     VALUES (%s,'ni_reviewed_release_v1',%s,%s) ON CONFLICT (source_id,dataset_key) DO NOTHING''', (source_id,REPORT,len(ni)))
        c.execute("SELECT checksum,row_count FROM reference_data_loads WHERE source_id=%s AND dataset_key='ni_reviewed_release_v1'", (source_id,))
        if c.fetchone() != (REPORT,len(ni)) or preserved_state(c) != before:
            raise ValueError('Reference preservation or release provenance failed')
        return {'gb_settlements_preserved': len(gb), 'ni_registered': len(ni), 'ni_road_entries': sum(counts[x] for x in ni),
                'preserved_reference_fingerprints': before}


def run(options):
    import psycopg
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2.service_account import Credentials
    report, metadata = load_inputs()
    options.work.mkdir(parents=True, exist_ok=True)
    credential = json.loads(os.environ['FIREBASE_HOSTING_SERVICE_ACCOUNT_JSON'])
    if credential.get('project_id') != 'roadprints-822cc' or credential.get('type') != 'service_account' or os.environ['ROADPRINTS_DATA_HOSTING_SITE'] != SITE:
        raise ValueError('Unexpected Hosting credentials or site')
    session = AuthorizedSession(Credentials.from_service_account_info(credential,scopes=['https://www.googleapis.com/auth/firebase.hosting']))
    hosting = Hosting(session)
    def check_version():
        channel = hosting.request('GET',f'sites/{SITE}/channels/{CHANNEL}')
        if channel['url'] != PREVIEW or channel['release']['version']['name'] != VERSION:
            raise ValueError('Verified preview version changed')
        version = hosting.request('GET',VERSION)
        if version['status'] != 'FINALIZED' or map_hash(version.get('config',{})) != CONFIG or map_hash(hosting.files(VERSION)) != FILE_MAP:
            raise ValueError('Verified immutable file map or config changed')
    check_version()
    previous = hosting.request('GET',f'sites/{SITE}/channels/live')['release']['version']['name']
    if previous not in (BASE,VERSION):
        raise ValueError('Live changed since NI preview')
    if previous == BASE and map_hash(hosting.files(BASE)) != BASE_MAP:
        raise ValueError('GB baseline changed')
    fetch = Fetcher()
    counts, boundaries = load_public(PREVIEW,report,metadata,fetch)
    with psycopg.connect(os.environ['DATABASE_URL'],connect_timeout=30,options='-c default_transaction_read_only=on') as connection:
        register(connection,metadata,boundaries,counts)
    # Recheck immediately before mutation; promote the exact immutable version.
    if hosting.request('GET',f'sites/{SITE}/channels/live')['release']['version']['name'] != previous:
        raise ValueError('Live changed during preflight')
    state = {'preview_version': VERSION, 'previous_live_version': previous, 'additions_report_sha256': REPORT}
    (options.work/'promotion.json').write_text(json.dumps(state,indent=2)+'\n')
    if previous != VERSION:
        release = api_json(session,'POST',f'sites/{SITE}/releases',params={'versionName':VERSION},json={'message':'Publish verified NI settlement reference additions 2026-10-15'})
        if release['version']['name'] != VERSION:
            raise ValueError('Unexpected live release version')
    print('Verified UK version is live; checking all NI assets.',flush=True)
    result = verify_batches(LIVE,report,verify,fetch,options.work/'live-progress.json')
    old = json.loads(gzip.decompress(Path('.github/gb-live/hosting-stage-report.json.gz').read_bytes()))
    verify(LIVE,{'schema_version':1,'files':[r for r in old['files'] if r['path'].startswith('collections/')]},fetch=fetch)
    if hosting.request('GET',f'sites/{SITE}/channels/live')['release']['version']['name'] != VERSION:
        raise ValueError('Live changed during serving checks')
    with psycopg.connect(os.environ['DATABASE_URL'],connect_timeout=30) as connection:
        registration = register(connection,metadata,boundaries,counts,write=True)
    state.update(status='published_and_registered',settlements=len(counts),**registration,**result,
                 completed_at=datetime.now(timezone.utc).isoformat())
    (options.work/'release-result.json').write_text(json.dumps(state,indent=2)+'\n')
    print(json.dumps(state,indent=2),flush=True)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work',type=Path,default=Path('ni-live-evidence'))
    try:
        run(parser.parse_args())
    except Exception as error:
        print('NI publication stopped: '+type(error).__name__+'. Inspect retained public evidence.',file=sys.stderr)
        sys.exit(1)
