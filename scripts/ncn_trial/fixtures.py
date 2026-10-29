"""Load the actual public Route 1 subset and make labelled synthetic fixes."""
import hashlib
import json
import math
from pathlib import Path

from shapely.geometry import LineString

from .coverage import Fix, Section, TrialReference

ROOT = Path(__file__).resolve().parents[2]
FIXTURE = ROOT / 'tests/fixtures/ncn-route1'


def load_reference(name='ncn-route1'):
    if name not in {'ncn-route1', 'ncn-ni-route9', 'ncn-wales-route8', 'ncn-scotland-route7'}:
        raise ValueError('Unknown bounded trial fixture')
    fixture = ROOT / 'tests/fixtures' / name
    metadata = json.loads((fixture / 'source.json').read_text())
    raw = (fixture / 'reference.geojson').read_bytes()
    if hashlib.sha256(raw).hexdigest() != metadata['fixture_sha256']:
        raise ValueError('Trial fixture checksum changed')
    origin_lng, origin_lat = metadata['projection_origin']
    def project(coordinate):
        lng, lat = coordinate
        return (6371008.8 * math.radians(lng - origin_lng) * math.cos(math.radians(origin_lat)),
                6371008.8 * math.radians(lat - origin_lat))
    sections = []
    source = json.loads(raw)['features']
    for feature in source:
        p, g = feature['properties'], feature['geometry']
        parts = [g['coordinates']] if g['type'] == 'LineString' else g['coordinates']
        for index, line in enumerate(parts):
            sections.append(Section(f"FID:{p['FID']}:{index}", LineString([project(c) for c in line]),
                                    (f"NCN:{p['RouteNo']}",), p['OpenStatus'] == 'Open' and p['Desc_'] != 'Ferry'))
    version = metadata['source_snapshot_sha256'] + ':' + metadata['fixture_sha256']
    return TrialReference(version, sections), metadata


def fixes_along(line, start=0.1, end=0.8, accuracy=3.0, period=2.0):
    first, last = start * line.length, end * line.length
    count = max(3, math.ceil(abs(last - first) / 8.0) + 1)
    return [Fix(*line.interpolate(first + (last - first) * i / (count - 1)).coords[0],
                i * period, accuracy) for i in range(count)]
