#!/usr/bin/env python3
"""Join public ONS settlement polygons to the exact HCT County Collector source.

Usage: PYTHONPATH=... python scripts/build_settlement_historic_counties.py
    COUNTY_ZIP SETTLEMENT_CATALOGUE PAGE_CACHE OUTPUT_JSON
Requires pyshp 3.1.6 and shapely 2.2.0. No personal journey data is used.
Downloaded pages are resumable; the compact output is committed, not rebuilt
over the network by Android CI. Settlement IDs and names are never changed.
"""
import concurrent.futures
import gzip
import hashlib
import io
import json
import pathlib
import sys
import urllib.parse
import urllib.request
import zipfile

import shapefile
from shapely import make_valid
from shapely.geometry import shape
from shapely.strtree import STRtree

SOURCE = ('https://services1.arcgis.com/ESMARspQHYMw9BZ9/arcgis/rest/services/'
          'BUA_2022_GB/FeatureServer/0/query')
PAGE_SIZE = 250


def assign(boundary, county_shapes, codes, tree):
    """Positive polygon area only: a shared edge/point is not membership.

    Primary is largest overlapping footprint, with code as deterministic tie
    breaker. All positive overlaps are retained, including very small ones.
    This is reference membership, never evidence of travel or an award.
    """
    overlaps = []
    for index in tree.query(boundary, predicate='intersects'):
        area = boundary.intersection(county_shapes[index]).area
        if area > 0:
            overlaps.append((area, codes[index]))
    overlaps.sort(key=lambda item: (-item[0], item[1]))
    return [code for _, code in overlaps]


def main():
    county_zip, catalogue_file, cache_dir, output_file = map(pathlib.Path, sys.argv[1:])
    catalogue = json.loads(catalogue_file.read_text())['settlements']
    expected = {row['code']: row['name'] for row in catalogue}
    if len(expected) != len(catalogue):
        raise ValueError('Duplicate settlement IDs')
    county_manifest = json.loads((output_file.parent / 'catalogue.json').read_text())
    digest = hashlib.sha256(county_zip.read_bytes()).hexdigest()
    if digest != county_manifest['source_sha256']:
        raise ValueError('County source differs from County Collector')
    archive = zipfile.ZipFile(county_zip)
    def part(extension):
        return io.BytesIO(archive.read(next(name for name in archive.namelist()
                                          if name.lower().endswith(extension))))
    reader = shapefile.Reader(shp=part('.shp'), shx=part('.shx'), dbf=part('.dbf'))
    records = list(reader.iterShapeRecords())
    codes = [row.record.as_dict()['HCS_CODE'] for row in records]
    counties = [shape(row.shape.__geo_interface__) for row in records]
    if set(codes) != {row['code'] for row in county_manifest['counties']}:
        raise ValueError('County IDs differ from County Collector')
    tree = STRtree(counties)
    cache_dir.mkdir(parents=True, exist_ok=True)
    def page(offset):
        target = cache_dir / f'{offset:05d}.json'
        if not target.exists():
            params = dict(where='1=1', outFields='BUA22CD,BUA22NM',
                          returnGeometry='true', outSR=4326, resultOffset=offset,
                          resultRecordCount=PAGE_SIZE, orderByFields='OBJECTID', f='geojson')
            data = None
            for attempt in range(3):
                try:
                    with urllib.request.urlopen(SOURCE + '?' + urllib.parse.urlencode(params),
                                                timeout=120) as response:
                        data = response.read()
                    parsed = json.loads(data)
                    if 'error' in parsed or not parsed.get('features'):
                        raise ValueError(f'Invalid ONS page {offset}')
                    break
                except Exception:
                    if attempt == 2:
                        raise
            target.write_bytes(data)
        data = target.read_bytes()
        print(f'Boundary page {offset}: {len(data)} bytes', flush=True)
        return offset, data
    # The catalogue and boundary service must agree exactly. Extra/missing IDs
    # fail publication rather than silently relying on centre-point estimates.
    offsets = range(0, len(catalogue), PAGE_SIZE)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        pages = sorted(pool.map(page, offsets))
    memberships = {}
    repaired = []
    source_digest = hashlib.sha256()
    for offset, data in pages:
        source_digest.update(data)
        for feature in json.loads(data)['features']:
            code = feature['properties']['BUA22CD']
            if code in memberships or code not in expected:
                raise ValueError(f'Unexpected/duplicate settlement {code}')
            if feature['properties']['BUA22NM'] != expected[code]:
                raise ValueError(f'Settlement name changed: {code}')
            boundary = shape(feature['geometry'])
            if not boundary.is_valid:
                boundary = make_valid(boundary)
                repaired.append(code)
            memberships[code] = assign(boundary, counties, codes, tree)
    if set(memberships) != set(expected):
        raise ValueError(f'Missing {len(set(expected) - set(memberships))} settlement boundaries')
    missing = [code for code, values in memberships.items() if not values]
    if missing:
        raise ValueError(f'Settlements outside the historic reference: {missing}')
    output = dict(format=1, county_version=county_manifest['version'],
                  county_source_sha256=digest, settlement_source=SOURCE,
                  settlement_source_sha256=source_digest.hexdigest(),
                  settlement_catalogue_sha256=hashlib.sha256(catalogue_file.read_bytes()).hexdigest(),
                  scope='Great Britain; Northern Ireland settlements not yet in source catalogue',
                  primary_rule='Largest positive polygon overlap, then county code',
                  repaired_settlement_boundaries=repaired,
                  settlements={code: memberships[code] for code in sorted(memberships)})
    encoded = (json.dumps(output, separators=(',', ':')) + '\n').encode()
    output_file.write_bytes(gzip.compress(encoded, compresslevel=9, mtime=0)
                            if output_file.suffix in ('.gz', '.bin') else encoded)
    print(json.dumps(dict(settlements=len(memberships),
                          spanning_counties=sum(len(v) > 1 for v in memberships.values()),
                          repaired_boundaries=len(repaired), bytes=output_file.stat().st_size)), flush=True)


if __name__ == '__main__':
    main()
